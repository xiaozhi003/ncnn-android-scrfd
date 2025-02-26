package com.android.xz.camera.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;

import com.android.xz.camera.CameraManager;
import com.android.xz.camera.ICameraManager;
import com.android.xz.camera.YUVFormat;
import com.android.xz.camera.callback.PreviewBufferCallback;
import com.android.xz.camera.view.base.BaseSurfaceView;
import com.android.xz.util.Logs;

/**
 * 适用Camera的SurfaceView预览
 *
 * @author xiaozhi
 * @since 2024/8/22
 */
public class CameraSurfaceView extends BaseSurfaceView {

    public CameraSurfaceView(Context context) {
        super(context);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void init(Context context) {
        super.init(context);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Logs.i(TAG, "surfaceChanged [" + width + ", " + height + "]");
        mSurfaceWidth = width;
        mSurfaceHeight = height;
    }

    @Override
    public void onOpen() {
        mPreviewSize = getCameraManager().getPreviewSize();
        getCameraManager().startPreview(getSurfaceHolder());
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public ICameraManager createCameraManager(Context context) {
        return new CameraManager(context);
    }
}
