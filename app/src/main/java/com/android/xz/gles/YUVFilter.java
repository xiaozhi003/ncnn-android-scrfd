package com.android.xz.gles;

import android.opengl.GLES20;

import com.android.xz.camera.YUVFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class YUVFilter {

    /**
     * 绘制的流程
     * 1.顶点着色程序 - 用于渲染形状的顶点的 OpenGL ES 图形代码
     * 2.片段着色器 - 用于渲染具有特定颜色或形状的形状的 OpenGL ES 代码纹理。
     * 3.程序 - 包含您想要用于绘制的着色器的 OpenGL ES 对象 一个或多个形状
     * <p>
     * 您至少需要一个顶点着色器来绘制形状，以及一个 fragment 着色器来为该形状着色。
     * 这些着色器必须经过编译，然后添加到 OpenGL ES 程序中，该程序随后用于绘制形状。
     */

    // 顶点着色器代码
    private final String vertexShaderCode =
            "uniform mat4 uMVPMatrix;\n" +
                    // 顶点坐标
                    "attribute vec4 vPosition;\n" +
                    // 纹理坐标
                    "attribute vec2 vTexCoordinate;\n" +
                    "varying vec2 aTexCoordinate;\n" +
                    "void main() {\n" +
                    "  gl_Position = uMVPMatrix * vPosition;\n" +
                    "  aTexCoordinate = vTexCoordinate;\n" +
                    "}\n";

    // 片段着色器代码
    private final String fragmentShaderCode =
            "precision mediump float;\n" +
                    "uniform sampler2D samplerY;\n" +
                    "uniform sampler2D samplerU;\n" +
                    "uniform sampler2D samplerV;\n" +
                    "uniform sampler2D samplerUV;\n" +
                    "uniform int yuvType;\n" +
                    "varying vec2 aTexCoordinate;\n" +
                    
                    "void main() {\n" +
                    "  vec3 yuv;\n" +
                    "  if (yuvType == 0) {" +
                    "    yuv.x = texture2D(samplerY, aTexCoordinate).r;\n" +
                    "    yuv.y = texture2D(samplerU, aTexCoordinate).r - 0.5;\n" +
                    "    yuv.z = texture2D(samplerV, aTexCoordinate).r - 0.5;\n" +
                    "  } else if (yuvType == 1) {" +
                    "    yuv.x = texture2D(samplerY, aTexCoordinate).r;\n" +
                    "    yuv.y = texture2D(samplerUV, aTexCoordinate).r - 0.5;\n" +
                    "    yuv.z = texture2D(samplerUV, aTexCoordinate).a - 0.5;\n" +
                    "  } else {" +
                    "    yuv.x = texture2D(samplerY, aTexCoordinate).r;\n" +
                    "    yuv.y = texture2D(samplerUV, aTexCoordinate).a - 0.5;\n" +
                    "    yuv.z = texture2D(samplerUV, aTexCoordinate).r - 0.5;\n" +
                    "  }" +
                    "  vec3 rgb = mat3(1.0, 1.0, 1.0,\n" +
                    "  0.0, -0.344, 1.772,\n" +
                    "  1.402, -0.714, 0.0) * yuv;\n" +
                    "  gl_FragColor = vec4(rgb, 1);\n" +
                    "}\n";

    private int mProgram;

    // 顶点坐标缓冲区
    private FloatBuffer vertexBuffer;

    // 纹理坐标缓冲区
    private FloatBuffer textureBuffer;

    // 此数组中每个顶点的坐标数
    static final int COORDS_PER_VERTEX = 2;

    /**
     * 顶点坐标数组
     * 顶点坐标系中原点(0,0)在画布中心
     * 向左为x轴正方向
     * 向上为y轴正方向
     * 画布四个角坐标如下：
     * (-1, 1),(1, 1)
     * (-1,-1),(1,-1)
     */
    private float vertexCoords[] = {
            -1.0f, 1.0f,   // 左上
            -1.0f, -1.0f,  // 左下
            1.0f, 1.0f,    // 右上
            1.0f, -1.0f,   // 右下
    };

    /**
     * 纹理坐标数组
     * 这里我们需要注意纹理坐标系，原点(0,0s)在画布左下角
     * 向左为x轴正方向
     * 向上为y轴正方向
     * 画布四个角坐标如下：
     * (0,1),(1,1)
     * (0,0),(1,0)
     */
    private float textureCoords[] = {
            0.0f, 1.0f, // 左上
            0.0f, 0.0f, // 左下
            1.0f, 1.0f, // 右上
            1.0f, 0.0f, // 右下
    };

    private int positionHandle;
    // 纹理坐标句柄
    private int texCoordinateHandle;

    // Use to access and set the view transformation
    private int vPMatrixHandle;

    private IntBuffer mPlanarTextureHandles = IntBuffer.wrap(new int[3]);
    private int[] mSampleHandle = new int[3];
    private int mYUVTypeHandle;

    private final int vertexCount = vertexCoords.length / COORDS_PER_VERTEX;
    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private int mTextureWidth;
    private int mTextureHeight;

    public YUVFilter() {
        // 初始化形状坐标的顶点字节缓冲区
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexCoords);
        vertexBuffer.position(0);

        // 初始化纹理坐标顶点字节缓冲区
        textureBuffer = ByteBuffer.allocateDirect(textureCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureCoords);
        textureBuffer.position(0);
    }

    public void setTextureSize(int width, int height) {
        mTextureWidth = width;
        mTextureHeight = height;
    }

    public void surfaceCreated() {
        // 加载顶点着色器程序
        int vertexShader = GLESUtils.loadShader(GLES20.GL_VERTEX_SHADER,
                vertexShaderCode);
        // 加载片段着色器程序
        int fragmentShader = GLESUtils.loadShader(GLES20.GL_FRAGMENT_SHADER,
                fragmentShaderCode);

        // 创建空的OpenGL ES程序
        mProgram = GLES20.glCreateProgram();
        // 将顶点着色器添加到程序中
        GLES20.glAttachShader(mProgram, vertexShader);
        // 将片段着色器添加到程序中
        GLES20.glAttachShader(mProgram, fragmentShader);
        // 创建OpenGL ES程序可执行文件
        GLES20.glLinkProgram(mProgram);

        // 获取顶点着色器vPosition成员的句柄
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        // 获取顶点着色器中纹理坐标的句柄
        texCoordinateHandle = GLES20.glGetAttribLocation(mProgram, "vTexCoordinate");
        // 获取绘制矩阵句柄
        vPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        // 获取yuvType句柄
        mYUVTypeHandle = GLES20.glGetUniformLocation(mProgram, "yuvType");

        // 生成YUV纹理句柄
        GLES20.glGenTextures(3, mPlanarTextureHandles);
    }

    public void surfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    public void onDraw(float[] matrix, YUVFormat yuvFormat) {
        // 将程序添加到OpenGL ES环境
        GLES20.glUseProgram(mProgram);

        // 重新绘制背景色为黑色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // 为正方形顶点启用控制句柄
        GLES20.glEnableVertexAttribArray(positionHandle);
        // 写入坐标数据
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);

        // 启用纹理坐标控制句柄
        GLES20.glEnableVertexAttribArray(texCoordinateHandle);
        // 写入坐标数据
        GLES20.glVertexAttribPointer(texCoordinateHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, textureBuffer);

        // 将投影和视图变换传递给着色器
        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, matrix, 0);

        int yuvType = 0;
        // 设置yuvType
        if (yuvFormat == YUVFormat.I420) {
            yuvType = 0;
        } else if (yuvFormat == YUVFormat.NV12) {
            yuvType = 1;
        } else if (yuvFormat == YUVFormat.NV21) {
            yuvType = 2;
        }
        GLES20.glUniform1i(mYUVTypeHandle, yuvType);

        // yuvType: 0是I420，1是NV12
        int planarCount = 0;
        if (yuvFormat == YUVFormat.I420) {
            planarCount = 3;
            mSampleHandle[0] = GLES20.glGetUniformLocation(mProgram, "samplerY");
            mSampleHandle[1] = GLES20.glGetUniformLocation(mProgram, "samplerU");
            mSampleHandle[2] = GLES20.glGetUniformLocation(mProgram, "samplerV");
        } else {
            //NV12、NV21有两个平面
            planarCount = 2;
            mSampleHandle[0] = GLES20.glGetUniformLocation(mProgram, "samplerY");
            mSampleHandle[1] = GLES20.glGetUniformLocation(mProgram, "samplerUV");
        }
        for (int i = 0; i < planarCount; i++) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mPlanarTextureHandles.get(i));
            GLES20.glUniform1i(mSampleHandle[i], i);
        }

        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // 禁用顶点阵列
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordinateHandle);
    }

    public void release() {
        GLES20.glDeleteProgram(mProgram);
        mProgram = -1;
    }

    /**
     * 将图片数据绑定到纹理目标，适用于UV分量分开存储的（I420）
     *
     * @param yPlane YUV数据的Y分量
     * @param uPlane YUV数据的U分量
     * @param vPlane YUV数据的V分量
     * @param width  YUV图片宽度
     * @param height YUV图片高度
     */
    public void feedTextureWithImageData(ByteBuffer yPlane, ByteBuffer uPlane, ByteBuffer vPlane, int width, int height) {
        //根据YUV编码的特点，获得不同平面的基址
        textureYUV(yPlane, width, height, 0);
        textureYUV(uPlane, width / 2, height / 2, 1);
        textureYUV(vPlane, width / 2, height / 2, 2);
    }

    /**
     * 将图片数据绑定到纹理目标，适用于UV分量交叉存储的（NV12、NV21）
     *
     * @param yPlane  YUV数据的Y分量
     * @param uvPlane YUV数据的UV分量
     * @param width   YUV图片宽度
     * @param height  YUV图片高度
     */
    public void feedTextureWithImageData(ByteBuffer yPlane, ByteBuffer uvPlane, int width, int height) {
        //根据YUV编码的特点，获得不同平面的基址
        textureYUV(yPlane, width, height, 0);
        textureNV12(uvPlane, width / 2, height / 2, 1);
    }

    /**
     * 将图片数据绑定到纹理目标，适用于UV分量分开存储的（I420）
     *
     * @param imageData YUV数据的Y/U/V分量
     * @param width     YUV图片宽度
     * @param height    YUV图片高度
     */
    private void textureYUV(ByteBuffer imageData, int width, int height, int index) {
        // 将纹理对象绑定到纹理目标
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mPlanarTextureHandles.get(index));
        // 设置放大和缩小时，纹理的过滤选项为：线性过滤
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        // 设置纹理X,Y轴的纹理环绕选项为：边缘像素延伸
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        // 加载图像数据到纹理，GL_LUMINANCE指明了图像数据的像素格式为只有亮度，虽然第三个和第七个参数都使用了GL_LUMINANCE，
        // 但意义是不一样的，前者指明了纹理对象的颜色分量成分，后者指明了图像数据的像素格式
        // 获得纹理对象后，其每个像素的r,g,b,a值都为相同，为加载图像的像素亮度，在这里就是YUV某一平面的分量值
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0,
                GLES20.GL_LUMINANCE, width, height, 0,
                GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE, imageData
        );
    }

    /**
     * 将图片数据绑定到纹理目标，适用于UV分量交叉存储的（NV12、NV21）
     *
     * @param imageData YUV数据的UV分量
     * @param width     YUV图片宽度
     * @param height    YUV图片高度
     */
    private void textureNV12(ByteBuffer imageData, int width, int height, int index) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mPlanarTextureHandles.get(index));
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0,
                GLES20.GL_LUMINANCE_ALPHA, width, height, 0,
                GLES20.GL_LUMINANCE_ALPHA,
                GLES20.GL_UNSIGNED_BYTE, imageData
        );
    }
}
