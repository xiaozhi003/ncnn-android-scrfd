package com.tencent.scrfdncnn.view;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.List;

/**
 * Created by wangzhi on 2016/5/23.
 */
public class FrameFaceView extends View {

    private static final String TAG = FrameFaceView.class.getSimpleName();

    private int previewWidth = 480;
    private int previewHeight = 640;

    private Paint mPaint;

    private List<float[]> mLocFaces;

    private int[] mPoints;

    private int mWidth = 0;

    private int mHeight = 0;

    private float mDx;

    private float mDy;

    private int mBitmapWidth;

    private int mBitmapHeight;

    private int mCameraId;

    private boolean isMirror = false;

    private Context mContext;

    public FrameFaceView(Context context) {
        super(context);
        init(context);
    }

    public FrameFaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FrameFaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public static boolean isScreenOriatationPortrait(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    public void setCameraId(int cameraId) {
        mCameraId = cameraId;
    }

    public void setpreviewSize(int previewWidth, int previewHeight) {
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
    }

    public int getPreviewWidth() {
        return previewWidth;
    }

    public int getPreviewHeight() {
        return previewHeight;
    }

    public void setMirror(boolean mirror) {
        isMirror = mirror;
    }

    private void init(Context context) {
        mContext = context;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.GREEN);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(4);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        Log.i(TAG, "获取显示区域参数");
        int desiredWidth = previewWidth;
        int desiredHeight = previewHeight;

        float radio = (float) desiredWidth / (float) desiredHeight;

        Log.i(TAG, "获取显示区域参数 radio:" + radio);

        /**
         * 每个MeasureSpec均包含两种数据，尺寸和设定类型，需要通过 MeasureSpec.getMode和getSize进行提取
         */
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // 参考值竖屏 800 1214
        // 参考值横屏 1280 734
        int layout_width = 0;
        int layout_height = 0;

        if (widthMode == MeasureSpec.EXACTLY) {
            layout_width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            layout_width = Math.min(desiredWidth, widthSize);
        } else {
            layout_width = desiredWidth;
        }
        if (heightMode == MeasureSpec.EXACTLY) {
            layout_height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            layout_height = Math.min(desiredHeight, heightSize);
        } else {
            layout_height = desiredHeight;
        }

        Log.i(TAG, "layout_height:" + layout_height);

        float layout_radio = (float) layout_width / (float) layout_height;

        if (layout_radio > radio) {
            layout_height = (int) (layout_width / radio);
        } else {
            layout_width = (int) (layout_height * radio);
        }

        mWidth = layout_width;
        mHeight = layout_height;
        setMeasuredDimension(layout_width, layout_height);
        Log.i(TAG, "CSV设定宽度:" + widthSize + "  设定高度:" + heightSize);// 让我们来输出他们
        Log.i(TAG, "CSV实际宽度:" + layout_width + "  实际高度:" + layout_height);// 让我们来输出他们
        Log.i(TAG, "显示比例：" + ((float) layout_width / (float) layout_height));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        List<float[]> locFaces = mLocFaces;

        if (locFaces == null)
            return;
        for (float[] locFace : locFaces) {
            float right;
            float left;

            if (!isMirror) {
                left = getX(locFace[0]);
                right = left + (float) locFace[2] * mWidth / previewWidth;
            } else {
                right = getX(locFace[0]);
                left = right - (float) locFace[2] * mWidth / previewWidth;
            }

            float top = getY(locFace[1]);
            float bottom = top + (float) locFace[3] * mHeight / previewHeight;

            canvas.drawRect(new Rect((int) left, (int) top, (int) right, (int) bottom), mPaint);
            Log.i(TAG, "draw... left:" + left + " right:" + right + " top:" + top + "bottom:" + bottom);
        }

        int[] points = mPoints;
        if (points != null) {
            for (int i = 0; i < points.length / 2; i++) {
                float x, y;
                x = getX(points[i * 2]);
                y = getY(points[i * 2 + 1]);

                canvas.drawPoint(x, y, mPaint);
            }
        }
    }

    private float getY(float y) {
        return y * mHeight / previewHeight;
    }

    private float getX(float x) {
        if (!isMirror) {
            return x * mWidth / previewWidth;
        } else {
            return mWidth - x * mWidth / previewWidth;
        }
    }

    public void setPoints(int[] points) {
        mPoints = points;
    }

    public void setLocFaces(List<float[]> locFaces) {
        mLocFaces = locFaces;
        postInvalidate();
    }
}
