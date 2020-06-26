package com.nematjon.edd_client_season_two;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.koushikdutta.ion.Ion;


import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

import static com.nematjon.edd_client_season_two.services.MainService.EMA_NOTIFICATION_ID;


public class EMAActivity extends AppCompatActivity {

    //region Constants
    private static final String TAG = EMAActivity.class.getSimpleName();
    public static final Short[] EMA_NOTIF_HOURS = {10, 14, 18, 22};  //in hours of day
    //endregion

    //region UI  variables
    TextView question1;
    TextView question2;
    TextView question3;
    TextView question4;
    TextView question5;
    TextView question6;
    TextView question7;
    TextView question8;
    TextView question9;

    SeekBar seekBar1;
    SeekBar seekBar2;
    SeekBar seekBar3;
    SeekBar seekBar4;
    SeekBar seekBar5;
    SeekBar seekBar6;
    SeekBar seekBar7;
    SeekBar seekBar8;
    SeekBar seekBar9;

    Button btnSubmit;
    //endregion
    private int emaOrder;

    private SharedPreferences loginPrefs;
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
            dialog = Tools.requestPermissions(EMAActivity.this);
        }
        loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
        if (!loginPrefs.getBoolean("logged_in", false)) {
            finish();
        }
        setContentView(R.layout.activity_ema);
        init();
    }

    public void init() {
        question1 = findViewById(R.id.question1);
        question2 = findViewById(R.id.question2);
        question3 = findViewById(R.id.question3);
        question4 = findViewById(R.id.question4);
        question5 = findViewById(R.id.question5);
        question6 = findViewById(R.id.question6);
        question7 = findViewById(R.id.question7);
        question8 = findViewById(R.id.question8);
        question9 = findViewById(R.id.question9);

        seekBar1 = findViewById(R.id.scale_q1);
        seekBar2 = findViewById(R.id.scale_q2);
        seekBar3 = findViewById(R.id.scale_q3);
        seekBar4 = findViewById(R.id.scale_q4);
        seekBar5 = findViewById(R.id.scale_q5);
        seekBar6 = findViewById(R.id.scale_q6);
        seekBar7 = findViewById(R.id.scale_q7);
        seekBar8 = findViewById(R.id.scale_q8);
        seekBar9 = findViewById(R.id.scale_q9);

        btnSubmit = findViewById(R.id.btn_submit);

        emaOrder = getIntent().getIntExtra("ema_order", -1);
    }

    public void clickSubmit(View view) {

        long timestamp = System.currentTimeMillis();

        String answers = String.format(Locale.US, "%d %d %d %d %d %d %d %d %d",
                seekBar1.getProgress() + 1,
                seekBar2.getProgress() + 1,
                seekBar3.getProgress() + 1,
                seekBar4.getProgress() + 1,
                seekBar5.getProgress() + 1,
                seekBar6.getProgress() + 1,
                seekBar7.getProgress() + 1,
                seekBar8.getProgress() + 1,
                seekBar9.getProgress() + 1);

        SharedPreferences prefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        int dataSourceId = prefs.getInt("SURVEY_EMA", -1);
        assert dataSourceId != -1;
        DbMgr.saveMixedData(dataSourceId, timestamp, 1.0f, timestamp, emaOrder, answers);

        SharedPreferences.Editor editor = loginPrefs.edit();
        editor.putBoolean("ema_btn_make_visible", false);
        editor.apply();

        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(EMA_NOTIFICATION_ID);
        }

        Toast.makeText(this, "Response saved", Toast.LENGTH_SHORT).show();
        rewardDialog = new Dialog(this);
        sendRewardsData(250);
        showRewardPopup(250);
    }

    private void sendRewardsData(int points) {
        long nowTime = System.currentTimeMillis();
        SharedPreferences prefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        int dataSourceId = prefs.getInt("REWARD_POINTS", -1);
        assert dataSourceId != -1;
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HH:mm:ss", Locale.KOREA).format(Calendar.getInstance().getTime());
        DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, timeStamp, points);
    }

    Dialog rewardDialog;
    private void showRewardPopup(int points) {
        rewardDialog.setContentView(R.layout.reward_pop_up);
        Button yesButton = rewardDialog.findViewById(R.id.button_accept);
        ImageView closePopUp = rewardDialog.findViewById(R.id.close);
        ImageView imageView = rewardDialog.findViewById(R.id.image);
        TextView earnedPoints = rewardDialog.findViewById(R.id.earned_points);
        earnedPoints.setText(getString(R.string.plus_earned_points, points));
        Ion.with(imageView).load("android.resource://com.nematjon.edd_client_season_two/" + R.drawable.reward);


        closePopUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rewardDialog.dismiss();
            }
        });

        yesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //go to main activity
                Intent intent = new Intent(EMAActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                rewardDialog.dismiss();
            }
        });

        Objects.requireNonNull(rewardDialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        rewardDialog.setCanceledOnTouchOutside(false);
        rewardDialog.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        rewardDialog.dismiss();
    }
}
