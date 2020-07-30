package com.nematjon.edd_client_season_two.receivers;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

import androidx.core.app.NotificationCompat;

import com.nematjon.edd_client_season_two.AppUseDb;
import com.nematjon.edd_client_season_two.DbMgr;
import com.nematjon.edd_client_season_two.EMAActivity;
import com.nematjon.edd_client_season_two.R;
import com.nematjon.edd_client_season_two.StoredMedia;
import com.nematjon.edd_client_season_two.Tools;

import java.util.Calendar;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static com.nematjon.edd_client_season_two.EMAActivity.EMA_NOTIF_HOURS;
import static com.nematjon.edd_client_season_two.services.MainService.EMA_NOTIFICATION_ID;
import static com.nematjon.edd_client_season_two.services.MainService.EMA_RESPONSE_EXPIRE_TIME;
import static com.nematjon.edd_client_season_two.services.MainService.SERVICE_START_X_MIN_BEFORE_EMA;

public class EMAAlarmRcvr extends BroadcastReceiver {
    private static final String TAG = EMAAlarmRcvr.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences networkPrefs = context.getSharedPreferences("NetworkVariables", MODE_PRIVATE);
        SharedPreferences configPrefs = context.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        SharedPreferences loginPrefs = context.getSharedPreferences("UserLogin", MODE_PRIVATE);
        ContentResolver CR = context.getContentResolver();
        //init DbMgr if it's null
        if (DbMgr.getDB() == null)
            DbMgr.init(context);

        SharedPreferences.Editor editor = loginPrefs.edit();
        editor.putBoolean("ema_btn_make_visible", true);
        editor.apply();

        PendingResult pendingResult = goAsync();
        Task task = new Task(pendingResult, configPrefs, networkPrefs, loginPrefs, CR);
        task.execute();
        sendNotification(context, intent.getIntExtra("ema_order", -1));
        setAlarams(context, intent.getIntExtra("ema_order", -1));
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
            DbMgr.saveMixedData(storedMediaSourceId, nowTime, 1.0f, nowTime, totalNumOfImages, image_media_type);
            DbMgr.saveMixedData(storedMediaSourceId, nowTime, 1.0f, nowTime, totalNumOfVideoFiles, video_media_type);
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
            DbMgr.saveMixedData(deviceInfoSourceId, nowTime, 1.0f, nowTime, updated_api_level, apiLevelType);
            // endregion
            return "Success";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.e(TAG, "Task is completed: " + s);
            pendingResult.finish();
        }
    }

    private void sendNotification(Context context, int ema_order) {
        final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(context, EMAActivity.class);
        notificationIntent.putExtra("ema_order", ema_order);
        //PendingIntent pendingIntent = PendingIntent.getActivities(CustomSensorsService.this, 0, new Intent[]{notificationIntent}, 0);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String channelId = context.getString(R.string.notif_channel_id);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext(), channelId);
        builder.setContentTitle(context.getString(R.string.app_name))
                .setTimeoutAfter(1000 * EMA_RESPONSE_EXPIRE_TIME)
                .setContentText(context.getString(R.string.daily_notif_text))
                .setTicker("New Message Alert!")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher_no_bg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        final Notification notification = builder.build();
        if (notificationManager != null) {
            notificationManager.notify(EMA_NOTIFICATION_ID, notification);
        }
    }

    public void setAlarams(Context context, int ema_order) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);

        Intent intent1 = new Intent(context, EMAAlarmRcvr.class);
        intent1.putExtra("ema_order", 1);
        Intent intent2 = new Intent(context, EMAAlarmRcvr.class);
        intent2.putExtra("ema_order", 2);
        Intent intent3 = new Intent(context, EMAAlarmRcvr.class);
        intent3.putExtra("ema_order", 3);
        Intent intent4 = new Intent(context, EMAAlarmRcvr.class);
        intent4.putExtra("ema_order", 4);

        PendingIntent pendingIntent1 = PendingIntent.getBroadcast(context, 1, intent1, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntent2 = PendingIntent.getBroadcast(context, 2, intent2, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntent3 = PendingIntent.getBroadcast(context, 3, intent3, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntent4 = PendingIntent.getBroadcast(context, 4, intent4, PendingIntent.FLAG_UPDATE_CURRENT);
        if (alarmManager == null)
            return;

        Calendar firingCal1 = Calendar.getInstance();
        firingCal1.set(Calendar.HOUR_OF_DAY, EMA_NOTIF_HOURS[0]); // at 10am
        firingCal1.set(Calendar.MINUTE, 0); // Particular minute
        firingCal1.set(Calendar.SECOND, 0); // particular second
        firingCal1.set(Calendar.MILLISECOND, 0); // particular second

        Calendar firingCal2 = Calendar.getInstance();
        firingCal2.set(Calendar.HOUR_OF_DAY, EMA_NOTIF_HOURS[1]); // at 2pm
        firingCal2.set(Calendar.MINUTE, 0); // Particular minute
        firingCal2.set(Calendar.SECOND, 0); // particular second
        firingCal2.set(Calendar.MILLISECOND, 0); // particular second

        Calendar firingCal3 = Calendar.getInstance();
        firingCal3.set(Calendar.HOUR_OF_DAY, EMA_NOTIF_HOURS[2]); // at 6pm
        firingCal3.set(Calendar.MINUTE, 0); // Particular minute
        firingCal3.set(Calendar.SECOND, 0); // particular second
        firingCal3.set(Calendar.MILLISECOND, 0); // particular second

        Calendar firingCal4 = Calendar.getInstance();
        firingCal4.set(Calendar.HOUR_OF_DAY, EMA_NOTIF_HOURS[3]); // at 10pm
        firingCal4.set(Calendar.MINUTE, 0); // Particular minute
        firingCal4.set(Calendar.SECOND, 0); // particular second
        firingCal4.set(Calendar.MILLISECOND, 0); // particular second

        if (ema_order == 1)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, firingCal2.getTimeInMillis(), 30000, pendingIntent2); //set from today
        else if (ema_order == 2)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, firingCal3.getTimeInMillis(), 30000, pendingIntent3); //set from today
        else if (ema_order == 3)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, firingCal4.getTimeInMillis(), 30000, pendingIntent4); //set from today
        else if (ema_order == 4) {
            firingCal1.add(Calendar.DAY_OF_MONTH, 1);
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, firingCal1.getTimeInMillis(), 30000, pendingIntent1); //set from today
        }
    }
}

