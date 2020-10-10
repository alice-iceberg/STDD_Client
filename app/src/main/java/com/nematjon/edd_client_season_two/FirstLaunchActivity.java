package com.nematjon.edd_client_season_two;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;

public class FirstLaunchActivity extends AppCompatActivity {

    LottieAnimationView lottieAnimationLocation;
    LottieAnimationView lottieAnimationMicrophone;
    LottieAnimationView lottieAnimationCamera;
    LottieAnimationView lottieAnimationStorage;
    Button continueBtn;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_launch);

        lottieAnimationLocation = findViewById(R.id.lottie_location);
        lottieAnimationMicrophone = findViewById(R.id.lottie_microphone);
        lottieAnimationCamera = findViewById(R.id.lottie_camera);
        lottieAnimationStorage = findViewById(R.id.lottie_storage);
        continueBtn = findViewById(R.id.continueBtn);

        lottieAnimationLocation.animate().setDuration(5000).setStartDelay(4000);
        lottieAnimationMicrophone.animate().setDuration(4000).setStartDelay(4000);
        lottieAnimationCamera.animate().setDuration(4500).setStartDelay(4000);
        lottieAnimationStorage.animate().setDuration(5000).setStartDelay(4000);

        continueBtn.animate().setDuration(5000).setStartDelay(4000);

    }

    public void continueBtnOnClick(View view) {
        finish();
    }
}
