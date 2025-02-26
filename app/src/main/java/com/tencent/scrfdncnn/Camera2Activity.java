package com.tencent.scrfdncnn;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.widget.FrameLayout;

import com.android.xz.camera.Camera2Manager;
import com.android.xz.camera.ICameraManager;
import com.android.xz.camera.YUVFormat;
import com.android.xz.camera.callback.CameraCallback;
import com.android.xz.camera.callback.PreviewBufferCallback;
import com.android.xz.util.YUVUtils;
import com.tencent.scrfdncnn.model.Face;
import com.tencent.scrfdncnn.view.DisplayYUVGLSurfaceView;
import com.tencent.scrfdncnn.view.FrameFaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Camera2Activity extends AppCompatActivity implements CameraCallback {

    private static final String TAG = Camera2Activity.class.getSimpleName();

    private FrameLayout mContentLayout;
    private DisplayYUVGLSurfaceView mDisplayYUVGLSurfaceView;
    private FrameFaceView mFrameFaceView;

    private ICameraManager mCameraManager;
    private int mCameraId = 1;

    private FaceDetectorThread mFaceDetectorThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        mContentLayout = findViewById(R.id.contentLayout);
        mDisplayYUVGLSurfaceView = findViewById(R.id.cameraView);
        mFrameFaceView = findViewById(R.id.frameView);

        mCameraManager = new Camera2Manager(this);
        mCameraManager.setCameraId(mCameraId);
        mCameraManager.setCameraCallback(this);
        mCameraManager.setPreviewSize(new Size(640, 480));
        mCameraManager.addPreviewBufferCallback(mPreviewBufferCallback);

        if (mCameraId == 1) {
            mFrameFaceView.setMirror(true);
        } else {
            mFrameFaceView.setMirror(false);
        }

        mDisplayYUVGLSurfaceView.setCameraId(mCameraId);

        mContentLayout.post(() -> {
            int contentWidth = mContentLayout.getMeasuredWidth();
            Log.i(TAG, "contentLayout: " + mContentLayout.getMeasuredWidth() + "x" + mContentLayout.getMeasuredHeight());

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mDisplayYUVGLSurfaceView.getLayoutParams();
            lp.width = contentWidth;
            lp.height = contentWidth * 4 / 3;
            mDisplayYUVGLSurfaceView.setLayoutParams(lp);

            lp = (FrameLayout.LayoutParams) mFrameFaceView.getLayoutParams();
            lp.width = contentWidth;
            lp.height = contentWidth * 4 / 3;
            mFrameFaceView.setLayoutParams(lp);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraManager.openCamera();
        startDetector();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraManager.releaseCamera();
        stopDetector();
    }

    private void startDetector() {
        if (mFaceDetectorThread == null) {
            mFaceDetectorThread = new FaceDetectorThread("FaceDetector");
            mFaceDetectorThread.start();
        }
    }

    private void stopDetector() {
        if (mFaceDetectorThread != null) {
            mFaceDetectorThread.quitSafely();
            mFaceDetectorThread = null;
        }
    }

    @Override
    public void onOpen() {
        mCameraManager.startPreview((SurfaceTexture) null);
    }

    @Override
    public void onOpenError(int error, String msg) {

    }

    @Override
    public void onPreview(int previewWidth, int previewHeight) {

    }

    @Override
    public void onPreviewError(int error, String msg) {

    }

    @Override
    public void onClose() {

    }

    private class FaceDetectorThread extends HandlerThread implements Handler.Callback {

        public static final int DETECT_YUV_DATA = 101;

        private Handler mHandler;
        private SCRFDNcnn mSCRFDNcnn = new SCRFDNcnn();

        private AtomicBoolean isProcessing = new AtomicBoolean(false);

        public FaceDetectorThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            Log.i(TAG, "start FaceDetectorThread.");
            mSCRFDNcnn.create();

            boolean ret_init = mSCRFDNcnn.loadModel(getAssets(), 0, 0);
            if (!ret_init) {
                Log.e("MainActivity", "scrfdncnn loadModel failed");
            }

            super.run();

            mSCRFDNcnn.destroy();
            Log.v(TAG, "exit FaceDetectorThread.");
        }

        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            mHandler = new Handler(Looper.myLooper(), this);
        }

        @Override
        public boolean handleMessage(@NonNull Message msg) {
            isProcessing.set(true);
            // 处理消息
            if (msg.what == DETECT_YUV_DATA) {
                byte[] yuv = (byte[]) msg.obj;
                int width = msg.arg1;
                int height = msg.arg2;

                long start = System.currentTimeMillis();
                mDisplayYUVGLSurfaceView.feedYUVData(yuv, width, height, YUVFormat.NV21, mCameraManager.getOrientation());
                Face[] faces = mSCRFDNcnn.detectNV21(yuv, width, height, mCameraManager.getOrientation());
                mDisplayYUVGLSurfaceView.requestRender();
                Log.i(TAG, "detect:" + (System.currentTimeMillis() - start) + "ms");

                if (faces != null) {

                    List<float[]> faceRectList = new ArrayList<>();
                    for (Face face : faces) {
                        float[] rect = face.getRect();
                        faceRectList.add(rect);
                    }

                    mFrameFaceView.setLocFaces(faceRectList);
                } else {
                    mFrameFaceView.setLocFaces(null);
                }
            }
            isProcessing.set(false);
            return true;
        }

        public Handler getHandler() {
            return mHandler;
        }

        public boolean isBusy() {
            if (mHandler == null) {
                return true;
            }
            if (!isAlive()) {
                return true;
            }
            if (mHandler.hasMessages(DETECT_YUV_DATA)) {
                return true;
            }
            return isProcessing.get();
        }
    }

    private void send(byte[] data, int width, int height) {
        if (mFaceDetectorThread != null) {
            Handler handler = mFaceDetectorThread.getHandler();
            if (!mFaceDetectorThread.isBusy() && handler != null) {
                Message message = handler.obtainMessage();
                message.what = FaceDetectorThread.DETECT_YUV_DATA;
                message.obj = data;
                message.arg1 = width;
                message.arg2 = height;
                message.sendToTarget();
            }
        }
    }

    byte[] mNV21Data;

    private PreviewBufferCallback mPreviewBufferCallback = new PreviewBufferCallback() {
        @Override
        public void onPreviewBufferFrame(byte[] data, int width, int height, YUVFormat format) {
            if (format == YUVFormat.I420) {
                if (mNV21Data == null || mNV21Data.length != data.length) {
                    mNV21Data = new byte[data.length];
                }
                YUVUtils.yuv420pToNV21(data, width, height, mNV21Data);
                send(data, width, height);
            } else {
                send(data, width, height);
            }
        }
    };
}