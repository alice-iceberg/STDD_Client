package com.nematjon.edd_client_season_two.receivers;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.HandlerThread;
import android.util.Log;

import com.nematjon.edd_client_season_two.Camera2Capture;
import com.nematjon.edd_client_season_two.DbMgr;

import java.util.Objects;

import static com.nematjon.edd_client_season_two.services.MainService.Y_GRAVITY_MIN;
import static com.nematjon.edd_client_season_two.services.MainService.y_value_gravity;

public class ScreenAndUnlockRcvr extends BroadcastReceiver {
    public static final String TAG = "ScreenAndUnlockReceiver";
    public static boolean phoneUnlocked = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences confPrefs = context.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        SharedPreferences phoneUsageVariablesPrefs = context.getSharedPreferences("PhoneUsageVariablesPrefs", Context.MODE_PRIVATE);
        //init DbMgr if it's null
        if (DbMgr.getDB() == null)
            DbMgr.init(context);

        PendingResult pendingResult = goAsync();
        Task task = new Task(pendingResult, intent, confPrefs, phoneUsageVariablesPrefs, context);
        task.execute();
    }

    private static class Task extends AsyncTask<String, Integer, String> {

        private final PendingResult pendingResult;
        private final Intent intent;
        SharedPreferences confPrefs;
        SharedPreferences phoneUsageVariablesPrefs;
        @SuppressLint("StaticFieldLeak")
        Context context;


        private Task(PendingResult pendingResult, Intent intent, SharedPreferences confPrefs, SharedPreferences phoneUsageVariablesPrefs, Context context) {
            this.pendingResult = pendingResult;
            this.intent = intent;
            this.confPrefs = confPrefs;
            this.phoneUsageVariablesPrefs = phoneUsageVariablesPrefs;
            this.context = context;
        }

        @Override
        protected String doInBackground(String... strings) {
            long nowTime = System.currentTimeMillis();
            int dataSourceLockUnlock = confPrefs.getInt("UNLOCK_STATE", -1);
            int dataSourceScreenOnOff = confPrefs.getInt("SCREEN_STATE", -1);

            if (Objects.equals(intent.getAction(), Intent.ACTION_USER_PRESENT)) {
                Log.e(TAG, "Phone unlocked");
                phoneUnlocked = true;
                if (dataSourceLockUnlock != -1) {
                    DbMgr.saveMixedData(dataSourceLockUnlock, nowTime, 1.0f, nowTime, "UNLOCK");
                    SharedPreferences.Editor editor = phoneUsageVariablesPrefs.edit();
                    editor.putBoolean("unlocked", true);
                    editor.apply();

                }


            } else if (Objects.equals(intent.getAction(), Intent.ACTION_SCREEN_OFF)) {
                Log.e(TAG, "Phone locked / Screen OFF");
                phoneUnlocked = false;
                //region Handling phone locked state
                if (phoneUsageVariablesPrefs.getBoolean("unlocked", false)) {
                    SharedPreferences.Editor editor = phoneUsageVariablesPrefs.edit();
                    editor.putBoolean("unlocked", false);
                    editor.apply();
                    nowTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(dataSourceLockUnlock, nowTime, 1.0f, nowTime, "LOCK");
                }
                //endregion

                //region Handling screen OFF state
                if (dataSourceScreenOnOff != -1) {
                    nowTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(dataSourceScreenOnOff, nowTime, 1.0f, nowTime, "OFF");
                }
                //endregion

            } else if (Objects.equals(intent.getAction(), Intent.ACTION_SCREEN_ON)) {
                Log.e(TAG, "Screen ON");
                if (dataSourceScreenOnOff != -1) {
                    nowTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(dataSourceScreenOnOff, nowTime, 1.0f, nowTime, "ON");
                }
            }
            return "Success";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            if (phoneUnlocked) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // check position of the phone
                if (y_value_gravity > Y_GRAVITY_MIN || y_value_gravity == 0f) { // 0 when the device does not have a gravity sensor
                    //check whether camera is in use
                    boolean cameraAvailable = phoneUsageVariablesPrefs.getBoolean("isCameraAvailable", false);

                    if (cameraAvailable) {
                        //take a photo
                        Camera2Capture camera2Capture = new Camera2Capture(context);
                        camera2Capture.setupCamera2();
                    }
                }

            }
            pendingResult.finish();
        }
    }
}
