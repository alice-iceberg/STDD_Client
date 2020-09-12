package com.nematjon.edd_client_season_two;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import inha.nsl.easytrack.ETServiceGrpc;
import inha.nsl.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import static com.nematjon.edd_client_season_two.SmartwatchActivity.TAG;
import static com.nematjon.edd_client_season_two.SAPAndroidAgent.runThreads;
import static com.nematjon.edd_client_season_two.SAPAndroidAgent.mProviderServiceSocket;

public class DataCollectorService extends Service {

    private IBinder mBinder = new LocalBinder();
    static boolean uploadingSuccessfully = true;
    SharedPreferences smartwatchPrefs;

    private class LocalBinder extends Binder {
        DataCollectorService getService() {
            return DataCollectorService.this;
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        smartwatchPrefs = getApplicationContext().getSharedPreferences("SmartwatchPrefs", MODE_PRIVATE);

        // region setup foreground service (notification)
        int notificationId = 98766;
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String notificationChannelId = "com.example.stresssensingmiddleman";
            String notificationChannelName = "Stress sensing service";
            NotificationChannel notificationChannel = new NotificationChannel(notificationChannelId, notificationChannelName, NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setLightColor(Color.BLUE);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
                Notification notification = new Notification.Builder(this, notificationChannelId)
                        .setContentTitle("Stress sensing")
                        .setContentText("Stress sensing data collection is running now...")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(pendingIntent)
                        .build();
                startForeground(notificationId, notification);
            }
        } else {
            Notification notification = new Notification.Builder(this)
                    .setContentTitle("EasyTrack")
                    .setContentText("Data Collection service is running now...")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .build();
            startForeground(notificationId, notification);
        }
        // endregion
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // heartbeat submission thread
        new Thread(() -> {
            SharedPreferences prefs = getApplicationContext().getApplicationContext().getSharedPreferences(getApplicationContext().getPackageName(), Context.MODE_PRIVATE);
            String grpcHost = getApplicationContext().getString(R.string.grpc_host);
            int grpcPort = Integer.parseInt(getApplicationContext().getString(R.string.grpc_port));
            while (runThreads) {
                try {
                    String email = smartwatchPrefs.getString("email", null);
                    assert email != null;
                    int userId = smartwatchPrefs.getInt("userId", -1);
                    ManagedChannel channel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort).usePlaintext().build();
                    ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                    EtService.SubmitHeartbeat.Request submitHeartbeatRequest = EtService.SubmitHeartbeat.Request.newBuilder()
                            .setUserId(userId)
                            .setEmail(email)
                            .build();
                    try {
                        EtService.SubmitHeartbeat.Response responseMessage = stub.submitHeartbeat(submitHeartbeatRequest);
                        Log.e(TAG, "Heartbeat submission result : doneSuccessfully=" + responseMessage.getSuccess());
                    } catch (StatusRuntimeException e) {
                        e.printStackTrace();
                    } finally {
                        channel.shutdown();
                    }
                    Thread.sleep(20000);
                } catch (NullPointerException | InterruptedException e) {
                    Log.e(TAG, "SAPAndroidAgent: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();

        // data submission thread
        new Thread(() -> {
            SharedPreferences prefs = getApplicationContext().getApplicationContext().getSharedPreferences(getApplicationContext().getPackageName(), Context.MODE_PRIVATE);
            String grpcHost = getApplicationContext().getString(R.string.grpc_host);
            int grpcPort = Integer.parseInt(getApplicationContext().getString(R.string.grpc_port));
            while (runThreads) {
                try {
                    if (isConnectedToWifi()) {
                        Cursor cursor = DbMgr.getSensorData();
                        if(cursor.moveToFirst()){
                        uploadingSuccessfully = true;
                        ManagedChannel channel = ManagedChannelBuilder.forAddress(grpcHost, grpcPort).usePlaintext().build();
                        ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);

                        int userId = smartwatchPrefs.getInt("userId", -1);
                        String email = smartwatchPrefs.getString("email", null);
                        try {
                            do {
                                EtService.SubmitDataRecord.Request submitDataRecordRequest = EtService.SubmitDataRecord.Request.newBuilder()
                                        .setUserId(userId)
                                        .setEmail(email)
                                        .setCampaignId(Integer.parseInt(getString(R.string.campaign_id)))
                                        .setDataSource(cursor.getInt(cursor.getColumnIndex("dataSourceId")))
                                        .setTimestamp(cursor.getLong(cursor.getColumnIndex("timestamp")))
                                        .setValue(ByteString.copyFrom(cursor.getString(cursor.getColumnIndex("data")), StandardCharsets.UTF_8))
                                        .build();

                                EtService.SubmitDataRecord.Response responseMessage = stub.submitDataRecord(submitDataRecordRequest);
                                if (responseMessage.getSuccess()) {
                                    DbMgr.deleteRecord(cursor.getInt(cursor.getColumnIndex("id")));
                                }

                            } while (cursor.moveToNext());
                        } catch (StatusRuntimeException e) {
                            Log.e(TAG, "DataCollectorService.setUpDataSubmissionThread() exception: " + e.getMessage());
                            e.printStackTrace();
                        } finally {
                            channel.shutdown();
                        }
                    }
                        Log.e(TAG, "Data transferred to EasyTrack server!");
                    } else {
                        Log.e(TAG, "Couldn't try to submit data because device isn't connected to a WiFi network!");
                        uploadingSuccessfully = false;
                    }
                    Thread.sleep(60000);
                } catch (NullPointerException | InterruptedException e) {
                    uploadingSuccessfully = false;
                    e.printStackTrace();
                }
            }
        }).start();

        // requesting data
        new Thread(() -> {
            while (runThreads) {
                try {
                    if (mProviderServiceSocket != null) {
                        boolean sent = SAPAndroidAgent.sendMessage(new byte[]{1});
                        Log.e(TAG, "Request data from SmartWatch : " + sent);
                    }
                    Thread.sleep(90000);
                } catch (InterruptedException | NullPointerException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        return super.onStartCommand(intent, flags, startId);
    }

    private boolean isConnectedToWifi() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.getTypeName() != null && activeNetwork.getTypeName().toLowerCase().equals("wifi"))
                return activeNetwork.isConnected();
        }
        return false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
