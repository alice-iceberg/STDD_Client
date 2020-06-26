package com.nematjon.edd_client_season_two.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.nematjon.edd_client_season_two.DbMgr;

import java.util.Objects;

public class ScreenAndUnlockRcvr extends BroadcastReceiver {
    public static final String TAG = "ScreenAndUnlockReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences confPrefs = context.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        SharedPreferences screenPrefs = context.getSharedPreferences("SecreenVariables", Context.MODE_PRIVATE);
        PendingResult pendingResult = goAsync();
        Task task = new Task(pendingResult, intent, confPrefs, screenPrefs);
        task.execute();
    }

    private static class Task extends AsyncTask<String, Integer, String> {

        private final PendingResult pendingResult;
        private final Intent intent;
        SharedPreferences confPrefs;
        SharedPreferences screenPrefs;

        private Task(PendingResult pendingResult, Intent intent, SharedPreferences confPrefs, SharedPreferences screenPrefs) {
            this.pendingResult = pendingResult;
            this.intent = intent;
            this.confPrefs = confPrefs;
            this.screenPrefs = screenPrefs;
        }

        @Override
        protected String doInBackground(String... strings) {
            long nowTime = System.currentTimeMillis();
            int dataSourceLockUnlock = confPrefs.getInt("UNLOCK_STATE", -1);
            int dataSourceScreenOnOff = confPrefs.getInt("SCREEN_STATE", -1);
            assert dataSourceLockUnlock != -1;
            assert dataSourceScreenOnOff != -1;

            if (Objects.equals(intent.getAction(), Intent.ACTION_USER_PRESENT)) {
                Log.e(TAG, "Phone unlocked");
                DbMgr.saveMixedData(dataSourceLockUnlock, nowTime, 1.0f, nowTime, "UNLOCK");
                SharedPreferences.Editor editor = screenPrefs.edit();
                editor.putBoolean("unlocked", true);
                editor.apply();
            } else if (Objects.equals(intent.getAction(), Intent.ACTION_SCREEN_OFF)) {
                Log.e(TAG, "Phone locked / Screen OFF");
                //region Handling phone locked state
                if (screenPrefs.getBoolean("unlocked", false)) {
                    SharedPreferences.Editor editor = screenPrefs.edit();
                    editor.putBoolean("unlocked", false);
                    editor.apply();
                    DbMgr.saveMixedData(dataSourceLockUnlock, nowTime, 1.0f, nowTime, "LOCK");
                }
                //endregion

                //region Handling screen OFF state
                DbMgr.saveMixedData(dataSourceScreenOnOff, nowTime, 1.0f, nowTime, "OFF");
                //endregion

            } else if (Objects.equals(intent.getAction(), Intent.ACTION_SCREEN_ON)) {
                Log.e(TAG, "Screen ON");
                DbMgr.saveMixedData(dataSourceScreenOnOff, nowTime, 1.0f, nowTime, "ON");
            }
            return "Success";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.e(TAG, "Task is completed: " + s);
            pendingResult.finish();
        }
    }
}
