package com.tencent.scrfdncnn.view;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import com.android.xz.camera.YUVFormat;
import com.android.xz.gles.YUVFilter;
import com.android.xz.util.MatrixUtils;

import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class DisplayYUVGLSurfaceView extends GLSurfaceView {

    private static final String TAG = DisplayYUVGLSurfaceView.class.getSimpleName();

    private Context mContext;
    private MyRenderer mMyRenderer;

    public DisplayYUVGLSurfaceView(Context context) {
        super(context);
        init(context);
    }

    public DisplayYUVGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mMyRenderer = new MyRenderer();
        setEGLContextClientVersion(2);
        setRenderer(mMyRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void feedYUVData(byte[] yuvData, int width, int height, YUVFormat yuvFormat, int rotate) {
        if (yuvData == null) {
            return;
        }
        mMyRenderer.feedData(yuvData, width, height, yuvFormat, rotate);
    }

    public void setCameraId(int id) {
        mMyRenderer.setCameraId(id);
    }

    static class MyRenderer implements Renderer {

        private YUVFilter mYUVFilter;

        private YUVFormat mYUVFormat;

        private int mWidth;
        private int mHeight;

        // vPMatrix is an abbreviation for "Model View Projection Matrix"
        private float[] mMVPMatrix = new float[16];

        // y分量数据
        private ByteBuffer y = ByteBuffer.allocate(0);
        // u分量数据
        private ByteBuffer u = ByteBuffer.allocate(0);
        // v分量数据
        private ByteBuffer v = ByteBuffer.allocate(0);
        // uv分量数据
        private ByteBuffer uv = ByteBuffer.allocate(0);

        // 标识GLSurfaceView是否准备好
        private boolean hasVisibility = false;
        private boolean isMirror = false;
        private int mRotate;
        private int mCameraId;

        public MyRenderer() {
            mYUVFilter = new YUVFilter();
        }

        public void setCameraId(int cameraId) {
            mCameraId = cameraId;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            mYUVFilter.surfaceCreated();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            mYUVFilter.surfaceChanged(width, height);

            hasVisibility = true;
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            synchronized (this) {
                if (y.capacity() > 0) {
                    y.position(0);
                    if (mYUVFormat == YUVFormat.I420) {
                        u.position(0);
                        v.position(0);
                        mYUVFilter.feedTextureWithImageData(y, u, v, mWidth, mHeight);
                    } else {
                        uv.position(0);
                        mYUVFilter.feedTextureWithImageData(y, uv, mWidth, mHeight);
                    }

                    MatrixUtils.getMatrix(mMVPMatrix, MatrixUtils.TYPE_FITXY, mWidth, mHeight, mWidth, mHeight);
                    MatrixUtils.flip(mMVPMatrix, false, true);
                    if (mCameraId == 1) {
                        MatrixUtils.flip(mMVPMatrix, true, false);
                    }
                    MatrixUtils.rotate(mMVPMatrix, mRotate);

                    try {
                        long start = System.currentTimeMillis();
                        mYUVFilter.onDraw(mMVPMatrix, mYUVFormat);
                        Log.i(TAG, "drawTexture " + mWidth + "x" + mHeight + " 耗时：" + (System.currentTimeMillis() - start) + "ms");
                    } catch (Exception e) {
                        Log.w(TAG, e.getMessage());
                    }
                }
            }
        }

        /**
         * 设置渲染的YUV数据的宽高
         *
         * @param width  宽度
         * @param height 高度
         */
        public void setYuvDataSize(int width, int height) {
            if (width > 0 && height > 0) {
                // 初始化容器
                if (width != mWidth || height != mHeight) {
                    this.mWidth = width;
                    this.mHeight = height;
                    int yarraySize = width * height;
                    int uvarraySize = yarraySize / 4;
                    synchronized (this) {
                        y = ByteBuffer.allocate(yarraySize);
                        u = ByteBuffer.allocate(uvarraySize);
                        v = ByteBuffer.allocate(uvarraySize);
                        uv = ByteBuffer.allocate(uvarraySize * 2);
                    }
                }
            }
        }

        public void feedData(byte[] yuvData, int width, int height, YUVFormat yuvFormat, int rotate) {
            setYuvDataSize(width, height);

            synchronized (this) {
                mWidth = width;
                mHeight = height;
                mYUVFormat = yuvFormat;
                mRotate = rotate;
                if (hasVisibility) {
                    if (yuvFormat == YUVFormat.I420) {
                        y.clear();
                        u.clear();
                        v.clear();
                        y.put(yuvData, 0, width * height);
                        u.put(yuvData, width * height, width * height / 4);
                        v.put(yuvData, width * height * 5 / 4, width * height / 4);
                    } else {
                        y.clear();
                        uv.clear();
                        y.put(yuvData, 0, width * height);
                        uv.put(yuvData, width * height, width * height / 2);
                    }
                }
            }
        }
    }
}
