package com.nematjon.edd_client_season_two;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import static com.nematjon.edd_client_season_two.services.MainService.EMA_RESPONSE_EXPIRE_TIME;

public class FirebaseNotifications extends FirebaseMessagingService {

    private static final String TAG = FirebaseNotifications.class.getSimpleName();

    SharedPreferences loginPrefs;

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        //handle when receive notification via data event
        loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);

       // Log.e(TAG, "onMessageReceived: HERE EMA");
        if(remoteMessage.getData().size() > 0){
            SharedPreferences.Editor editor = loginPrefs.edit();
            editor.putBoolean("ema_btn_make_visible", true);
            editor.apply();

            Intent notificationIntent = new Intent(this, EMAActivity.class);
            //notificationIntent.putExtra("ema_order", ema_order);
            showNotification(remoteMessage.getData().get("title"), remoteMessage.getData().get("message"));
        }

        //handle when receive notification
        if(remoteMessage.getNotification() !=null){
            SharedPreferences.Editor editor = loginPrefs.edit();
            editor.putBoolean("ema_btn_make_visible", true);
            editor.apply();
            showNotification(remoteMessage.getNotification().getTitle(), remoteMessage.getNotification().getBody());
        }


    }

    public void showNotification (String title, String message){
        Intent intent = new Intent(this, MainActivity.class);
        String channel_id = getString(R.string.default_notification_channel_id);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channel_id)
                .setSound(uri)
                .setTimeoutAfter(1000 * EMA_RESPONSE_EXPIRE_TIME)
                .setAutoCancel(true)
                .setSmallIcon(R.mipmap.ic_launcher_no_bg)
                .setVibrate(new long[] {1000, 1000, 1000, 1000, 1000})
                .setOnlyAlertOnce(true)
                .setContentTitle(title)
                .setSubText(message)
                .setDefaults(Notification.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel notificationChannel = new NotificationChannel(channel_id, "Notification", NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setSound(uri, null);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        notificationManager.notify(0, builder.build());
    }
}
