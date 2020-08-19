package com.nematjon.edd_client_season_two;

import android.os.Bundle;
import android.widget.GridView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class CapturedPhotosActivity extends AppCompatActivity {

    GridView gridView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_images);

        gridView = (GridView) findViewById(R.id.saved_images_gridview);
        gridView.setAdapter(new ImageAdapter(CapturedPhotosActivity.this));


    }
}
