package com.nematjon.edd_client_season_two.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.nematjon.edd_client_season_two.DbMgr;
import com.nematjon.edd_client_season_two.Tools;

import java.util.Calendar;

import static android.content.Context.MODE_PRIVATE;

public class EMAAlarmRcvr extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences loginPrefs = context.getSharedPreferences("UserLogin", MODE_PRIVATE);
        SharedPreferences rewardPrefs = context.getSharedPreferences("Rewards", MODE_PRIVATE);

        //init DbMgr if it's null
        if (DbMgr.getDB() == null)
            DbMgr.init(context);

        int ema_order = Tools.getEMAOrderFromRangeAfterEMA(Calendar.getInstance());
        if (ema_order != 0 && ema_order != -1) {
            SharedPreferences.Editor editor = loginPrefs.edit();
            editor.putBoolean("ema_btn_make_visible", true);
            editor.apply();
        }
        //if it is 23:59 pm
        if (intent.getBooleanExtra("ema_reset", false)) {
            SharedPreferences.Editor ema_editor = rewardPrefs.edit();
            ema_editor.putBoolean("ema1_answered", false);
            ema_editor.putBoolean("ema2_answered", false);
            ema_editor.putBoolean("ema3_answered", false);
            ema_editor.putBoolean("ema4_answered", false);
            ema_editor.putInt("ema_answered_count", 0);
            ema_editor.apply();

            SharedPreferences.Editor loginEditor = loginPrefs.edit();
            loginEditor.putBoolean("ema_btn_make_visible", false);
            loginEditor.apply();
        }
    }
}



