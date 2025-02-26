package com.android.xz.util;

public class YUVUtils {

    /**
     * 将YUV420Planner（I420）转换为NV21, YYYYYYYY UU VV --> YYYYYYYY VUVU
     *
     * @param src
     * @param width
     * @param height
     * @return
     */
    public static void yuv420pToNV21(byte[] src, int width, int height, byte[] nv21) {
        int yLength = width * height;
        int uLength = yLength / 4;

        System.arraycopy(src, 0, nv21, 0, yLength); // Y分量
        for (int i = 0; i < yLength / 4; i++) {
            // U分量
            nv21[yLength + 2 * i + 1] = src[yLength + i];
            // V分量
            nv21[yLength + 2 * i] = src[yLength + uLength + i];
        }
    }
}
