package com.tencent.scrfdncnn.model;

public class Face {

    private float[] rect = new float[4];
    private float[] landmark = new float[10];

    public float[] getRect() {
        return rect;
    }

    public float[] getLandmark() {
        return landmark;
    }
}
