package com.nematjon.edd_client_season_two.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.protobuf.ByteString;
import com.nematjon.edd_client_season_two.AuthActivity;
import com.nematjon.edd_client_season_two.DbMgr;
import com.nematjon.edd_client_season_two.R;
import com.nematjon.edd_client_season_two.Tools;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import inha.nsl.easytrack.ETServiceGrpc;
import inha.nsl.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

public class DataSubmissionService extends Service {

    public static final int DATA_SUBMISSION_ID_SERVICE = 101;
    static NotificationManager mNotificationManager;
    public int bulkSize = 200;
    public int dataSourceIdCheck = -1;
    public static boolean uploadingSuccessfully = true;
    ArrayList<Integer> ids = new ArrayList<>();
    ArrayList<Long> timestampsList = new ArrayList<>();
    ArrayList<Integer> dataSourceIdList = new ArrayList<>();
    ArrayList<ByteString> valueList = new ArrayList<>();
    ArrayList<Integer> photosIds = new ArrayList<>();
    ArrayList<Long> photosTimestampsList = new ArrayList<>();
    ArrayList<Integer> photosDataSourceIdList = new ArrayList<>();
    ArrayList<ByteString> photosValueList = new ArrayList<>();

    private Handler dataSubmissionHandler = new Handler();
    private Runnable dataSubmissionRunnable = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                SharedPreferences loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
                SharedPreferences confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);

                boolean lteEnabled = loginPrefs.getBoolean("lte_on", false);
                int capturedPhotosDataSourceId = confPrefs.getInt("CAPTURED_PHOTOS", -1);

                //init DbMgr if it's null
                if (DbMgr.getDB() == null)
                    DbMgr.init(getApplicationContext());
                DbMgr.cleanupUselessData();

                if (Tools.isConnectedToWifi(getApplicationContext()) || (Tools.isNetworkAvailable() && lteEnabled)) {
                    Log.e("DataSubmissionService", "Data submission service running... Network available");
                    uploadingSuccessfully = true;

                    ids.clear();
                    dataSourceIdList.clear();
                    timestampsList.clear();
                    valueList.clear();

                    photosIds.clear();
                    photosDataSourceIdList.clear();
                    photosTimestampsList.clear();
                    photosValueList.clear();

                    Cursor cursor = DbMgr.getSensorData();

                    if (cursor != null && cursor.moveToFirst()) {
                        boolean stop = false;
                        do {
                            dataSourceIdCheck = cursor.getInt(cursor.getColumnIndex("dataSourceId"));

                            if (dataSourceIdCheck != capturedPhotosDataSourceId) {
                                ids.add(cursor.getInt(cursor.getColumnIndex("id")));
                                timestampsList.add(cursor.getLong(cursor.getColumnIndex("timestamp")));
                                dataSourceIdList.add(cursor.getInt(cursor.getColumnIndex("dataSourceId")));
                                valueList.add(ByteString.copyFrom(cursor.getString(cursor.getColumnIndex("data")), StandardCharsets.UTF_8));

                            } else {
                                photosIds.add(cursor.getInt(cursor.getColumnIndex("id")));
                                photosTimestampsList.add(cursor.getLong(cursor.getColumnIndex("timestamp")));
                                photosDataSourceIdList.add(cursor.getInt(cursor.getColumnIndex("dataSourceId")));
                                photosValueList.add(ByteString.copyFrom(cursor.getString(cursor.getColumnIndex("data")), StandardCharsets.UTF_8));
                            }
                            if (ids.size() + photosIds.size() >= bulkSize) {
                                stop = true;
                            }


                        } while (cursor.moveToNext() && !stop);
                    }

                    if (cursor != null) {
                        cursor.close();
                    }

                    int userId = loginPrefs.getInt(AuthActivity.user_id, -1);
                    String email = loginPrefs.getString(AuthActivity.usrEmail, null);

                    ManagedChannel channel = ManagedChannelBuilder.forAddress(
                            getString(R.string.grpc_host),
                            Integer.parseInt(getString(R.string.grpc_port))
                    ).usePlaintext().build();
                    ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);

                    try {
                        Log.e("DataSubmissionService", "Submitting data...");
                        EtService.SubmitDataRecords.Request submitDataRecordsRequest = EtService.SubmitDataRecords.Request.newBuilder()
                                .setUserId(userId)
                                .setEmail(email)
                                .setCampaignId(Integer.parseInt(getString(R.string.campaign_id)))
                                .addAllDataSource(dataSourceIdList)
                                .addAllTimestamp(timestampsList)
                                .addAllValue(valueList)
                                .build();
                        EtService.SubmitDataRecords.Response responseMessage = stub.submitDataRecords(submitDataRecordsRequest);

                        if (responseMessage.getSuccess()) {
                            for (int id : ids) {
                                try {
                                    DbMgr.deleteRecord(id);
                                } catch (Exception exception) {
                                    Log.e("DataSubmissionService", "Error with deleting the record");
                                }
                            }
                        }

                    } catch (StatusRuntimeException e) {
                        Log.e("DataSubmissionService", "DataCollectorService.setUpDataSubmissionThread() exception: " + e.getMessage());
                        uploadingSuccessfully = false;
                        e.printStackTrace();
                    } finally {
                        channel.shutdown();
                        //clear for the next round
                        ids.clear();
                        dataSourceIdList.clear();
                        timestampsList.clear();
                        valueList.clear();
                    }

                }
                if (Tools.isConnectedToWifi(getApplicationContext()) || (Tools.isNetworkAvailable() && lteEnabled)) {
                    Log.e("DataSubmissionService", "Photos submission service running... Network available");
                    uploadingSuccessfully = true;

                    Log.e("TAG", "run: Photos size" + photosIds.size());
                    if (!photosIds.isEmpty()) {
                        Log.e("TAG", "run: Photos size" + photosIds.size());
                        ManagedChannel channel = ManagedChannelBuilder.forAddress(
                                getString(R.string.grpc_host),
                                Integer.parseInt(getString(R.string.grpc_port))
                        ).usePlaintext().build();

                        ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);

                        loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
                        int userId = loginPrefs.getInt(AuthActivity.user_id, -1);
                        String email = loginPrefs.getString(AuthActivity.usrEmail, null);

                        try {
                                for(int i = 0; i < photosIds.size(); i++) {
                                    EtService.SubmitDataRecord.Request submitDataRecordRequest = EtService.SubmitDataRecord.Request.newBuilder()
                                            .setUserId(userId)
                                            .setEmail(email)
                                            .setCampaignId(Integer.parseInt(getString(R.string.campaign_id)))
                                            .setDataSource(photosDataSourceIdList.get(i))
                                            .setTimestamp(photosTimestampsList.get(i))
                                            .setValue(ByteString.copyFrom((photosValueList.get(i).toString()), StandardCharsets.UTF_8))
                                            .build();

                                    EtService.SubmitDataRecord.Response responseMessage = stub.submitDataRecord(submitDataRecordRequest);

                                    if (responseMessage.getSuccess()) {
                                        Log.e("TAG", "run: Deleting photo");
                                        DbMgr.deleteRecord(photosIds.get(i));
                                    }

                                }
                        } catch (StatusRuntimeException e) {
                            Log.e("DataSubmissionService Photos", "DataCollectorService.setUpDataSubmissionThread() exception: " + e.getMessage());
                            uploadingSuccessfully = false;
                            e.printStackTrace();
                        } finally {
                            Log.e("TAG", "run: Both finished");
                            channel.shutdown();

                            photosIds.clear();
                            photosDataSourceIdList.clear();
                            photosTimestampsList.clear();
                            photosValueList.clear();


                        }
                    }

                    dataSubmissionHandler.post(dataSubmissionRunnable);
                }
            }).start();
        }
    };


    @Override
    public void onCreate() {
        dataSubmissionHandler.post(dataSubmissionRunnable);
        Log.e("TAG", "onStartCommand: Data submission runnable posted");

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channel_id = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel() : "";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channel_id)
                .setOngoing(true)
                .setSubText(getString(R.string.noti_data_submission_running))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSmallIcon(R.mipmap.ic_launcher_no_bg)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);
        Notification notification = builder.build();
        startForeground(DATA_SUBMISSION_ID_SERVICE, notification);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public String createNotificationChannel() {
        String id = "YouNoOne_channel_id_data_submission";
        String name = getResources().getString(R.string.younoone_notif_channel);
        String description = getResources().getString(R.string.channel_description);
        NotificationChannel mChannel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW);
        mChannel.setDescription(description);
        mChannel.enableLights(true);
        mChannel.setLightColor(Color.RED);
        mChannel.enableVibration(true);
        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null) {
            mNotificationManager.createNotificationChannel(mChannel);
        }
        return id;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //region Stop foreground service
        stopForeground(false);
        mNotificationManager.cancel(DATA_SUBMISSION_ID_SERVICE);
        dataSubmissionHandler.removeCallbacks(dataSubmissionRunnable);
        //endregion
    }
}
