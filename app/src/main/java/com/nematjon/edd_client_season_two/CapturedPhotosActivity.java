package com.nematjon.edd_client_season_two;

import android.os.Bundle;
import android.widget.GridView;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import me.itangqi.waveloadingview.WaveLoadingView;

public class CapturedPhotosActivity extends AppCompatActivity {

    GridView gridView;

    WaveLoadingView waveLoadingView;
    SeekBar seekBar;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_images);

        seekBar = findViewById(R.id.waveSeekbar);
        waveLoadingView = findViewById(R.id.waveLoadingView);

        waveLoadingView.setProgressValue(0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                waveLoadingView.setProgressValue(i);

                if (i < 50) {
                    waveLoadingView.setBottomTitle(String.format("%d%%", i));
                    waveLoadingView.setCenterTitle("");
                    waveLoadingView.setTopTitle("");
                } else if (i < 80){
                    waveLoadingView.setBottomTitle("");
                    waveLoadingView.setCenterTitle(String.format("%d%%", i));
                    waveLoadingView.setTopTitle("");
                } else{
                    waveLoadingView.setBottomTitle("");
                    waveLoadingView.setCenterTitle("");
                    waveLoadingView.setTopTitle(String.format("%d%%", i));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

//        gridView = (GridView) findViewById(R.id.saved_images_gridview);
//        gridView.setAdapter(new ImageAdapter(CapturedPhotosActivity.this));


    }
}
