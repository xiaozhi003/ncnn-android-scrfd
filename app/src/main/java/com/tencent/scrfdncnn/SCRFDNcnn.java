// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.tencent.scrfdncnn;

import android.content.res.AssetManager;
import android.view.Surface;

import com.tencent.scrfdncnn.model.Face;

public class SCRFDNcnn {


    public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);

    public native boolean openCamera(int facing);

    public native boolean closeCamera();

    public native boolean setOutputWindow(Surface surface);

    /**
     * 创建
     *
     * @return
     */
    public native boolean create();

    /**
     * 销毁
     *
     * @return
     */
    public native boolean destroy();

    /**
     * NV21转RGB数据
     *
     * @param yuv    NV21数据区
     * @param rgb    RGB数据区
     * @param hw     hw[0]:宽，hw[1]:高
     * @param rotate 旋转角度：0，90，180，270
     */
    public native boolean NV21RotateToRGB(byte[] yuv, byte[] rgb, int[] hw, int rotate);

    /**
     * 检测人脸
     *
     * @param rgb    图像RGB数据
     * @param width  图像宽
     * @param height 图像高
     * @return 人脸数据
     */
    public native Face[] detectRGB(byte[] rgb, int width, int height);

    /**
     * 检测NV21数据
     *
     * @param nv21        NV21数据
     * @param width       图像宽
     * @param height      图像高
     * @param orientation 图像旋转方向
     * @return
     */
    public native Face[] detectNV21(byte[] nv21, int width, int height, int orientation);

    static {
        System.loadLibrary("scrfdncnn");
    }
}
