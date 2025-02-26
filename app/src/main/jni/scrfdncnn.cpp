// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>

#include "scrfd.h"

#include "ndkcamera.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static int NV21RotateToRGB(const unsigned char *_yuv, int width, int height, unsigned char *_rgb,
                           int dstw, int dsth, int type) {
    unsigned char *dst_yuv = (unsigned char *) malloc(width * height * 3 / 2);
    ncnn::kanna_rotate_yuv420sp(_yuv, width, height, dst_yuv, dstw, dsth, type);
    ncnn::yuv420sp2rgb(dst_yuv, dstw, dsth, _rgb);
    free(dst_yuv);
    return 0;
}

static int draw_unsupported(cv::Mat &rgb) {
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y),
                                cv::Size(label_size.width, label_size.height + baseLine)),
                  cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static int draw_fps(cv::Mat &rgb) {
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f) {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--) {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f) {
            return 0;
        }

        for (int i = 0; i < 10; i++) {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    char text[32];
    sprintf(text, "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y),
                                cv::Size(label_size.width, label_size.height + baseLine)),
                  cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));

    return 0;
}

static SCRFD *g_scrfd = 0;
static ncnn::Mutex lock;

class MyNdkCamera : public NdkCameraWindow {
public:
    virtual void on_image_render(cv::Mat &rgb) const;
};

void MyNdkCamera::on_image_render(cv::Mat &rgb) const {
    // scrfd
    {
        ncnn::MutexLockGuard g(lock);

        if (g_scrfd) {
            std::vector<FaceObject> faceobjects;
            g_scrfd->detect(rgb, faceobjects);

            g_scrfd->draw(rgb, faceobjects);
        } else {
            draw_unsupported(rgb);
        }
    }

    draw_fps(rgb);
}

static MyNdkCamera *g_camera = 0;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_tencent_scrfdncnn_SCRFDNcnn_create(JNIEnv *env, jobject thiz) {
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "create");

    g_camera = new MyNdkCamera;

    return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL
Java_com_tencent_scrfdncnn_SCRFDNcnn_destroy(JNIEnv *env, jobject thiz) {
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "destroy");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_scrfd;
        g_scrfd = 0;
    }

    delete g_camera;
    g_camera = 0;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_tencent_scrfdncnn_SCRFDNcnn_NV21RotateToRGB(JNIEnv *env, jobject thiz, jbyteArray yuv,
                                                     jbyteArray rgb, jintArray hw, jint rotate) {
    jbyte *_yuv = env->GetByteArrayElements(yuv, 0);
    jbyte *_rgb = env->GetByteArrayElements(rgb, 0);
    jint *_hw = env->GetIntArrayElements(hw, 0);
    int width = _hw[0];
    int height = _hw[1];
    int type = 0;


    /**
     * type类型说明
     * 1: 原图
     * 2: 左右镜像
     * 3: 旋转180
     * 4: 上下镜像
     * 5: 旋转90，并镜像
     * 6: 旋转90
     * 7: 旋转270，并镜像
     * 8: 旋转270
     */
    if (rotate == 0) {
        type = 1;
    } else if (rotate == 90) {
        type = 6;
        _hw[0] = height;
        _hw[1] = width;
    } else if (rotate == 180) {
        type = 3;
    } else if (rotate == 270) {
        type = 8;
        _hw[0] = height;
        _hw[1] = width;
    }

    NV21RotateToRGB((unsigned char *) _yuv, width, height, (unsigned char *) _rgb,
                    _hw[0], _hw[1], type);

    env->ReleaseIntArrayElements(hw, _hw, 0);
    env->ReleaseByteArrayElements(yuv, _yuv, 0);
    env->ReleaseByteArrayElements(rgb, _rgb, 0);

    return JNI_TRUE;
}

// public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL
Java_com_tencent_scrfdncnn_SCRFDNcnn_loadModel(JNIEnv *env, jobject thiz, jobject assetManager,
                                               jint modelid, jint cpugpu) {
    if (modelid < 0 || modelid > 7 || cpugpu < 0 || cpugpu > 1) {
        return JNI_FALSE;
    }

    AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const char *modeltypes[] =
            {
                    "500m",
                    "500m_kps",
                    "1g",
                    "2.5g",
                    "2.5g_kps",
                    "10g",
                    "10g_kps",
                    "34g"
            };

    const char *modeltype = modeltypes[(int) modelid];
    bool use_gpu = (int) cpugpu == 1;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        if (use_gpu && ncnn::get_gpu_count() == 0) {
            // no gpu
            delete g_scrfd;
            g_scrfd = 0;
        } else {
            if (!g_scrfd)
                g_scrfd = new SCRFD;
            g_scrfd->load(mgr, modeltype, use_gpu);
        }
    }

    return JNI_TRUE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_tencent_scrfdncnn_SCRFDNcnn_detectRGB(JNIEnv *env, jobject thiz, jbyteArray rgb, jint width,
                                               jint height) {
    jbyte *_rgb = env->GetByteArrayElements(rgb, 0);

    cv::Mat img_rgb(height, width, CV_8UC3, (unsigned char *) _rgb);

    std::vector<FaceObject> faceobjects;
    g_scrfd->detect(img_rgb, faceobjects);

    jobjectArray faceArray = NULL;
    if (faceobjects.size() > 0) {
        jclass faceClass = env->FindClass("com/tencent/scrfdncnn/model/Face");
        faceArray = env->NewObjectArray(faceobjects.size(), faceClass, NULL);
        for (size_t i = 0; i < faceobjects.size(); i++) {
            const FaceObject &obj = faceobjects[i];

            jmethodID constructor = env->GetMethodID(faceClass, "<init>", "()V");
            jobject faceObj = env->NewObject(faceClass, constructor);

            jfieldID rectField = env->GetFieldID(faceClass, "rect", "[F");
            jfloatArray rectArray = static_cast<jfloatArray>(env->GetObjectField(faceObj,
                                                                                 rectField));
            jfloat *_rect = env->GetFloatArrayElements(rectArray, 0);
            _rect[0] = obj.rect.x;
            _rect[1] = obj.rect.y;
            _rect[2] = obj.rect.width;
            _rect[3] = obj.rect.height;

            env->SetObjectArrayElement(faceArray, i, faceObj);

            // 释放局部变量
            env->ReleaseFloatArrayElements(rectArray, _rect, 0);
            env->DeleteLocalRef(rectArray);
            env->DeleteLocalRef(faceObj);
        }
        env->DeleteLocalRef(faceClass);
    }

    env->ReleaseByteArrayElements(rgb, _rgb, 0);

    return faceArray;
}

JNIEXPORT jobjectArray JNICALL
Java_com_tencent_scrfdncnn_SCRFDNcnn_detectNV21(JNIEnv *env, jobject thiz, jbyteArray nv21,
                                                jint nv21_width, jint nv21_height,
                                                jint camera_orientation) {
    jbyte *_nv21 = env->GetByteArrayElements(nv21, 0);

    // rotate nv21
    int w = 0;
    int h = 0;
    int rotate_type = 0;
    {
        if (camera_orientation == 0) {
            w = nv21_width;
            h = nv21_height;
            rotate_type = 1;
        }
        if (camera_orientation == 90) {
            w = nv21_height;
            h = nv21_width;
            rotate_type = 6;
        }
        if (camera_orientation == 180) {
            w = nv21_width;
            h = nv21_height;
            rotate_type = 3;
        }
        if (camera_orientation == 270) {
            w = nv21_height;
            h = nv21_width;
            rotate_type = 8;
        }
    }

    cv::Mat nv21_rotated(h + h / 2, w, CV_8UC1);
    ncnn::kanna_rotate_yuv420sp(reinterpret_cast<const unsigned char *>(_nv21), nv21_width,
                                nv21_height, nv21_rotated.data, w, h, rotate_type);

    // nv21_rotated to rgb
    cv::Mat rgb(h, w, CV_8UC3);
    ncnn::yuv420sp2rgb(nv21_rotated.data, w, h, rgb.data);

    std::vector<FaceObject> faceobjects;
    g_scrfd->detect(rgb, faceobjects);

    jobjectArray faceArray = NULL;
    if (faceobjects.size() > 0) {
        jclass faceClass = env->FindClass("com/tencent/scrfdncnn/model/Face");
        faceArray = env->NewObjectArray(faceobjects.size(), faceClass, NULL);
        for (size_t i = 0; i < faceobjects.size(); i++) {
            const FaceObject &obj = faceobjects[i];

            jmethodID constructor = env->GetMethodID(faceClass, "<init>", "()V");
            jobject faceObj = env->NewObject(faceClass, constructor);

            jfieldID rectField = env->GetFieldID(faceClass, "rect", "[F");
            jfloatArray rectArray = static_cast<jfloatArray>(env->GetObjectField(faceObj,
                                                                                 rectField));
            jfloat *_rect = env->GetFloatArrayElements(rectArray, 0);
            _rect[0] = obj.rect.x;
            _rect[1] = obj.rect.y;
            _rect[2] = obj.rect.width;
            _rect[3] = obj.rect.height;

            env->SetObjectArrayElement(faceArray, i, faceObj);

            // 释放局部变量
            env->ReleaseFloatArrayElements(rectArray, _rect, 0);
            env->DeleteLocalRef(rectArray);
            env->DeleteLocalRef(faceObj);
        }
        env->DeleteLocalRef(faceClass);
    }

    env->ReleaseByteArrayElements(nv21, _nv21, 0);

    return faceArray;
}

// public native boolean openCamera(int facing);
JNIEXPORT jboolean JNICALL
Java_com_tencent_scrfdncnn_SCRFDNcnn_openCamera(JNIEnv *env, jobject thiz, jint facing) {
    if (facing < 0 || facing > 1)
        return JNI_FALSE;

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "openCamera %d", facing);

    g_camera->open((int) facing);

    return JNI_TRUE;
}

// public native boolean closeCamera();
JNIEXPORT jboolean JNICALL
Java_com_tencent_scrfdncnn_SCRFDNcnn_closeCamera(JNIEnv *env, jobject thiz) {
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "closeCamera");

    g_camera->close();

    return JNI_TRUE;
}

// public native boolean setOutputWindow(Surface surface);
JNIEXPORT jboolean JNICALL
Java_com_tencent_scrfdncnn_SCRFDNcnn_setOutputWindow(JNIEnv *env, jobject thiz, jobject surface) {
    ANativeWindow *win = ANativeWindow_fromSurface(env, surface);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setOutputWindow %p", win);

    g_camera->set_window(win);

    return JNI_TRUE;
}

}
