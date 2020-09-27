package com.nematjon.edd_client_season_two.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.nematjon.edd_client_season_two.EMAActivity;
import com.nematjon.edd_client_season_two.R;
import com.nematjon.edd_client_season_two.Tools;

import java.util.Calendar;

public class EMAOverlayShowingService extends Service {

    private WindowManager windowManager;
    private LinearLayout emaAlertDialogView;
    private Button buttonAnswer;
    private Button buttonCancel;
    private int ema_order;


    @Override
    public void onCreate() {

        if (!Settings.canDrawOverlays(getApplicationContext())) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    1000,
                    500,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.CENTER;
            emaAlertDialogView = (LinearLayout) inflater.inflate(R.layout.ema_alert_dialog, null);
            buttonAnswer = (Button) emaAlertDialogView.findViewById(R.id.emaAlertButtonAnswer);
            buttonCancel = (Button) emaAlertDialogView.findViewById(R.id.emaAlertButtonCancel);

            buttonAnswer.setOnClickListener(view -> {
                ema_order = Tools.getEMAOrderFromRangeAfterEMA(Calendar.getInstance());
                if (ema_order != 0) {
                    windowManager.removeView(emaAlertDialogView);
                    Intent intent = new Intent(getApplicationContext(), EMAActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    stopService(new Intent(getApplicationContext(), EMAOverlayShowingService.class));
                } else {
                    windowManager.removeView(emaAlertDialogView);
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.time_is_up), Toast.LENGTH_LONG).show();
                    stopService(new Intent(getApplicationContext(), EMAOverlayShowingService.class));
                }
            });

            buttonCancel.setOnClickListener(view -> {
                windowManager.removeView(emaAlertDialogView);
                stopService(new Intent(getApplicationContext(), EMAOverlayShowingService.class));
            });

            windowManager = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
            windowManager.addView(emaAlertDialogView, params);

        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


}
