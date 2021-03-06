package com.nematjon.edd_client_season_two.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.nematjon.edd_client_season_two.EMAActivity;
import com.nematjon.edd_client_season_two.R;
import com.nematjon.edd_client_season_two.Tools;

import java.util.Calendar;

public class EMAOverlayShowingService extends Service {

    BroadcastReceiver mReceiver;
    public WindowManager windowManager;
    public RelativeLayout emaAlertDialogView;
    public Button buttonAnswer;
    public Button buttonCancel;
    public int ema_order;


    @Override
    public void onCreate() {

        if (!Settings.canDrawOverlays(getApplicationContext())) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.CENTER;
            emaAlertDialogView = (RelativeLayout) inflater.inflate(R.layout.ema_alert_dialog, null);
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

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("EMA_POP_UP_REMOVE");
        mReceiver = new EmaDismissReceiver();
        registerReceiver(mReceiver, intentFilter);

    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class EmaDismissReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra("ema_popup_remove", false)) {
                if (windowManager != null) {
                    windowManager.removeView(emaAlertDialogView);
                    stopService(new Intent(getApplicationContext(), EMAOverlayShowingService.class));
                }
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
}
