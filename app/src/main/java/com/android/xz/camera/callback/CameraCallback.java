package com.android.xz.camera.callback;

/**
 * 摄像头打开预览等回调
 *
 * @author xiaozhi
 * @since 2024/8/15
 */
public interface CameraCallback {

    void onOpen();

    void onOpenError(int error, String msg);

    void onPreview(int previewWidth, int previewHeight);

    void onPreviewError(int error, String msg);

    void onClose();
}
