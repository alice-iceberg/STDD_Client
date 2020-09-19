package com.nematjon.edd_client_season_two.receivers;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.TrafficStats;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.CalendarContract;
import android.util.Log;


import com.nematjon.edd_client_season_two.AppUseDb;
import com.nematjon.edd_client_season_two.DbMgr;
import com.nematjon.edd_client_season_two.StoredMedia;
import com.nematjon.edd_client_season_two.Tools;

import java.util.Calendar;

import static android.content.Context.MODE_PRIVATE;
import static com.nematjon.edd_client_season_two.services.MainService.SERVICE_START_X_MIN_BEFORE_EMA;

public class EMAAlarmRcvr extends BroadcastReceiver {
    private static final String TAG = EMAAlarmRcvr.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences networkPrefs = context.getSharedPreferences("NetworkVariables", MODE_PRIVATE);
        SharedPreferences configPrefs = context.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        SharedPreferences loginPrefs = context.getSharedPreferences("UserLogin", MODE_PRIVATE);
        SharedPreferences rewardPrefs = context.getSharedPreferences("Rewards", MODE_PRIVATE);
        ContentResolver CR = context.getContentResolver();

        //init DbMgr if it's null
        if (DbMgr.getDB() == null)
            DbMgr.init(context);

        //if EMA notification comes
        if (intent.getBooleanExtra("ema_notif", false)) {
            int ema_order = Tools.getEMAOrderFromRangeAfterEMA(Calendar.getInstance());
            if (ema_order != 0) {
                SharedPreferences.Editor editor = loginPrefs.edit();
                editor.putBoolean("ema_btn_make_visible", true);
                editor.apply();


                PendingResult pendingResult = goAsync();
                Task task = new Task(pendingResult, configPrefs, networkPrefs, loginPrefs, CR);
                task.execute();
            }

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

    private static class Task extends AsyncTask<String, Integer, String> {
        private final PendingResult pendingResult;
        SharedPreferences confPrefs;
        SharedPreferences networkPrefs;
        SharedPreferences loginPrefs;
        ContentResolver cr;

        private Task(PendingResult pendingResult, SharedPreferences confPrefs, SharedPreferences networkPrefs, SharedPreferences loginPrefs, ContentResolver cr) {
            this.pendingResult = pendingResult;
            this.confPrefs = confPrefs;
            this.networkPrefs = networkPrefs;
            this.loginPrefs = loginPrefs;
            this.cr = cr;
        }


        @SuppressLint("MissingPermission")
        @Override
        protected String doInBackground(String... strings) {
            // saving app usage data
            final long app_usage_time_end = System.currentTimeMillis();
            final long app_usage_time_start = (app_usage_time_end - SERVICE_START_X_MIN_BEFORE_EMA * 60 * 1000) + 1000; // add one second to start time
            int appUseDataSourceId = confPrefs.getInt("APPLICATION_USAGE", -1);
            assert appUseDataSourceId != -1;
            Cursor cursor = AppUseDb.getAppUsage();
            if (cursor.moveToFirst()) {
                do {
                    String package_name = cursor.getString(1);
                    long start_time = cursor.getLong(2);
                    long end_time = cursor.getLong(3);
                    if (Tools.inRange(start_time, app_usage_time_start, app_usage_time_end) && Tools.inRange(end_time, app_usage_time_start, app_usage_time_end))
                        if (start_time < end_time) {
                            DbMgr.saveMixedData(appUseDataSourceId, start_time, 1.0f, start_time, end_time, package_name);
                        }
                }
                while (cursor.moveToNext());
            }
            cursor.close();


            //region saving transmitted & received network data
            long nowTime = System.currentTimeMillis();
            String usage_tx_type = "TX";
            String usage_rx_type = "RX";

            long prevRx = networkPrefs.getLong("prev_rx_network_data", 0);
            long prevTx = networkPrefs.getLong("prev_tx_network_data", 0);

            long rxBytes = TrafficStats.getTotalRxBytes() - prevRx;
            long txBytes = TrafficStats.getTotalTxBytes() - prevTx;

            final long time_start = (nowTime - SERVICE_START_X_MIN_BEFORE_EMA * 60 * 1000) + 1000; // add one second to start time
            int networkDataSourceId = confPrefs.getInt("NETWORK_USAGE", -1);
            assert networkDataSourceId != -1;
            DbMgr.saveMixedData(networkDataSourceId, nowTime, 1.0f, time_start, nowTime, rxBytes, usage_tx_type);
            nowTime = System.currentTimeMillis();
            DbMgr.saveMixedData(networkDataSourceId, nowTime, 1.0f, time_start, nowTime, txBytes, usage_rx_type);

            SharedPreferences.Editor editor = networkPrefs.edit();
            editor.putLong("prev_rx_network_data", rxBytes);
            editor.putLong("prev_tx_network_data", txBytes);
            editor.apply();
            //endregion


            //region saving the total number of Images, Music and Video Files stored

            StoredMedia storedMedia = new StoredMedia();
            String image_media_type = "IMAGE";
            String video_media_type = "VIDEO";
            String music_media_type = "MUSIC";
            int totalNumOfImages = 0;
            int totalNumOfVideoFiles = 0;
            int totalNumOfMusic = 0;
            totalNumOfImages = storedMedia.totalNumberOfImages(cr);
            totalNumOfVideoFiles = storedMedia.totalNumberOfVideoFiles(cr);
            totalNumOfMusic = storedMedia.totalNumOfMusic(cr);
            int storedMediaSourceId = confPrefs.getInt("STORED_MEDIA", -1);
            assert storedMediaSourceId != -1;
            nowTime = System.currentTimeMillis();
            DbMgr.saveMixedData(storedMediaSourceId, nowTime, 1.0f, nowTime, totalNumOfImages, image_media_type);
            nowTime = System.currentTimeMillis();
            DbMgr.saveMixedData(storedMediaSourceId, nowTime, 1.0f, nowTime, totalNumOfVideoFiles, video_media_type);
            nowTime = System.currentTimeMillis();
            DbMgr.saveMixedData(storedMediaSourceId, nowTime, 1.0f, nowTime, totalNumOfMusic, music_media_type);

            //endregion

            //region saving the total number of Calendar Events
            Cursor calendarCursor;
            String calendar_type = "EVENT";
            calendarCursor = cr.query(CalendarContract.Events.CONTENT_URI, null, null, null, null);
            assert calendarCursor != null;
            int total_number_of_events = calendarCursor.getCount();
            int calendarSourceId = confPrefs.getInt("CALENDAR", -1);
            assert calendarSourceId != -1;
            nowTime = System.currentTimeMillis();
            DbMgr.saveMixedData(calendarSourceId, nowTime, 1.0f, nowTime, total_number_of_events, calendar_type);
            calendarCursor.close();
            //endregion

            // region checking and updating device information
            String current_device_model = Build.MODEL;
            int current_api_level = Build.VERSION.SDK_INT;
            boolean deviceInfoChanged = false;

            int deviceInfoSourceId = confPrefs.getInt("ANDROID_DEVICE_INFO", -1);
            String deviceModelType = "DEVICE MODEL";
            String apiLevelType = "API";

            String stored_device_model = loginPrefs.getString("deviceModel", null);
            int stored_api_level = loginPrefs.getInt("apiLevel", 0);

            if (!current_device_model.equals(stored_device_model) || (current_api_level != stored_api_level)) {
                editor = loginPrefs.edit();
                editor.putString("deviceModel", current_device_model);
                editor.putInt("apiLevel", current_api_level);
                editor.apply();
            }

            String updated_device_model = loginPrefs.getString("deviceModel", null);
            int updated_api_level = loginPrefs.getInt("apiLevel", 0);

            nowTime = System.currentTimeMillis();
            DbMgr.saveMixedData(deviceInfoSourceId, nowTime, 1.0f, nowTime, updated_device_model, deviceModelType);
            nowTime = System.currentTimeMillis();
            DbMgr.saveMixedData(deviceInfoSourceId, nowTime, 1.0f, nowTime, updated_api_level, apiLevelType);
            // endregion
            return "Success";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.e(TAG, "Ema task is completed: " + s);
            pendingResult.finish();
        }
    }

}

