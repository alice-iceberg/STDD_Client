package com.nematjon.edd_client_season_two;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class FullScreenImageActivity extends AppCompatActivity {

    ImageView imageView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        imageView = findViewById(R.id.fullscreen_imageView);

        Intent i = getIntent();
        int position = i.getIntExtra("id", -1);
        ImageAdapter imageAdapter = new ImageAdapter(this);

        Glide.with(this).load(imageAdapter.imagesPaths.get(position))
                .into(imageView);

    }
}
