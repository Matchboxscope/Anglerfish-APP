package com.example.espressoscope;

import android.os.Bundle;
import android.os.Environment;
import android.widget.GridView;

import com.example.espressoscope.ImageAdapter;

import java.io.File;
import java.util.ArrayList;

import androidx.appcompat.app.AppCompatActivity;

public class GalleryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        GridView gridView = findViewById(R.id.gridView);
        File imageFolder = new File(getFilesDir().getAbsolutePath());
        ArrayList<String> images = new ArrayList<>();

        if (imageFolder.exists()) {
            File[] files = imageFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getAbsolutePath().endsWith(".jpg")) {
                        images.add(file.getAbsolutePath());
                    }
                }
            }
        }

        ImageAdapter imageAdapter = new ImageAdapter(this, images);
        gridView.setAdapter(imageAdapter);
    }
}
