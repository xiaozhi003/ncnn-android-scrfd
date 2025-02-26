package com.tencent.scrfdncnn;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.ndkCameraBtn).setOnClickListener(v -> startActivity(new Intent(this, NDKCameraActivity.class)));
        findViewById(R.id.imageDetectBtn).setOnClickListener(v -> startActivity(new Intent(this, ImageActivity.class)));
        findViewById(R.id.faceCameraBtn).setOnClickListener(v -> startActivity(new Intent(this, CameraActivity.class)));
        findViewById(R.id.faceCamera2Btn).setOnClickListener(v -> startActivity(new Intent(this, Camera2Activity.class)));
    }
}