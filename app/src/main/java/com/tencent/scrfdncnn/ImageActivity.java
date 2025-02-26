package com.tencent.scrfdncnn;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.tencent.scrfdncnn.model.Face;

import java.nio.ByteBuffer;

public class ImageActivity extends AppCompatActivity {

    private static final String TAG = ImageActivity.class.getSimpleName();
    private SCRFDNcnn scrfdncnn = new SCRFDNcnn();
    private ProgressDialog mProgressDialog;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        mImageView = findViewById(R.id.faceIv);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMessage("检测中...");

        detect();
    }

    private void detect() {
        mProgressDialog.show();
        new Thread(() -> {
            scrfdncnn.create();
            boolean ret_init = scrfdncnn.loadModel(getAssets(), 0, 0);
            if (!ret_init) {
                Log.e(TAG, "scrfdncnn loadModel failed");
            } else {
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.beauty);
                Log.i(TAG, "bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                byte[] rgb = bitmap2RGB(bitmap);
                long start = System.currentTimeMillis();
                Face[] faces = scrfdncnn.detectRGB(rgb, bitmap.getWidth(), bitmap.getHeight());
                float[] rect = faces[0].getRect();
                Log.i(TAG, "faces.rect [" + rect[0] + ", " + rect[1] + ", " + rect[2] + ", " + rect[3] + "]"
                        + ", " + (System.currentTimeMillis() - start) + "ms");
                runOnUiThread(() -> mImageView.setImageBitmap(drawFaceRect(bitmap, rect)));
            }

            runOnUiThread(() -> mProgressDialog.dismiss());
            scrfdncnn.destroy();
        }).start();
    }

    /**
     * Draw face rectangle
     *
     * @param bitmap
     * @param faceRect
     */
    private Bitmap drawFaceRect(Bitmap bitmap, float[] faceRect) {
        Bitmap tempBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(tempBitmap);

        // 画出人脸位置(Mark face position)
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawRect(faceRect[0], faceRect[1], faceRect[0] + faceRect[2], faceRect[1] + faceRect[3], paint);
        return tempBitmap;
    }

    public static byte[] bitmap2RGB(Bitmap bitmap) {
        int bytes = bitmap.getByteCount();  //返回可用于储存此位图像素的最小字节数

        ByteBuffer buffer = ByteBuffer.allocate(bytes); //  使用allocate()静态方法创建字节缓冲区
        bitmap.copyPixelsToBuffer(buffer); // 将位图的像素复制到指定的缓冲区

        byte[] rgba = buffer.array();
        byte[] pixels = new byte[(rgba.length / 4) * 3];

        int count = rgba.length / 4;

        //Bitmap像素点的色彩通道排列顺序是RGBA
        for (int i = 0; i < count; i++) {

            pixels[i * 3] = rgba[i * 4];        //R
            pixels[i * 3 + 1] = rgba[i * 4 + 1];    //G
            pixels[i * 3 + 2] = rgba[i * 4 + 2];       //B

        }

        return pixels;
    }
}