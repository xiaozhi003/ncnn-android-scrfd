package com.tencent.scrfdncnn;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.xz.camera.callback.PreviewBufferCallback;
import com.android.xz.camera.view.CameraSurfaceView;
import com.tencent.scrfdncnn.model.Face;
import com.tencent.scrfdncnn.view.FrameFaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = CameraActivity.class.getSimpleName();

    public static final int REQUEST_CAMERA = 100;

    private FrameLayout mContentLayout;
    private CameraSurfaceView mCameraSurfaceView;
    private FrameFaceView mFrameFaceView;
    private FaceDetectorThread mFaceDetectorThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mContentLayout = findViewById(R.id.contentLayout);
        mCameraSurfaceView = findViewById(R.id.cameraView);
        mFrameFaceView = findViewById(R.id.frameView);

        mCameraSurfaceView.getCameraManager().setCameraId(1);
        mCameraSurfaceView.getCameraManager().setPreviewSize(new Size(640, 480));
        if (mCameraSurfaceView.getCameraManager().getCameraId() == 1) {
            mFrameFaceView.setMirror(true);
        } else {
            mFrameFaceView.setMirror(false);
        }

        mContentLayout.post(() -> {
            int contentWidth = mContentLayout.getMeasuredWidth();
            Log.i(TAG, "contentLayout: " + mContentLayout.getMeasuredWidth() + "x" + mContentLayout.getMeasuredHeight());

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mCameraSurfaceView.getLayoutParams();
            lp.width = contentWidth;
            lp.height = contentWidth * 4 / 3;
            mCameraSurfaceView.setLayoutParams(lp);

            lp = (FrameLayout.LayoutParams) mFrameFaceView.getLayoutParams();
            lp.width = contentWidth;
            lp.height = contentWidth * 4 / 3;
            mFrameFaceView.setLayoutParams(lp);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
        mCameraSurfaceView.getCameraManager().addPreviewBufferCallback(mPreviewBufferCallback);
        mCameraSurfaceView.onResume();
        startDetector();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraSurfaceView.onPause();
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
                Face[] faces = mSCRFDNcnn.detectNV21(yuv, width, height, mCameraSurfaceView.getCameraManager().getOrientation());
                Log.i(TAG, "detect:" + (System.currentTimeMillis() - start) + "ms");

                if (faces != null) {

                    List<float[]> faceRectList = new ArrayList<>();
                    for (Face face: faces) {
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

    private PreviewBufferCallback mPreviewBufferCallback = (data, width, height, format) -> send(data, width, height);
}