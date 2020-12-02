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

import androidx.annotation.Nullable;
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

import static com.nematjon.edd_client_season_two.services.MainService.ID_SERVICE;
import static com.nematjon.edd_client_season_two.services.MainService.PERMISSION_REQUEST_NOTIFICATION_ID;
import static com.nematjon.edd_client_season_two.services.MainService.mNotificationManager;

public class DataSubmissionService extends Service {

    public int bulkSize = 500;
    public int dataSourceIdCheck = -1;
    public static boolean uploadingSuccessfully = true;
    ArrayList<Integer> ids = new ArrayList<>();
    ArrayList<Long> timestampsList = new ArrayList<Long>();
    ArrayList<Integer> dataSourceIdList = new ArrayList<Integer>();
    ArrayList<ByteString> valueList = new ArrayList<ByteString>();

    int DATASUBMISSION_PERIOD = 60; // in sec

    private Handler dataSubmissionHandler = new Handler();
    private Runnable dataSubmissionRunnable = new Runnable() {
        @Override
        public void run() {

            SharedPreferences loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
            SharedPreferences confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);

            boolean lteEnabled = loginPrefs.getBoolean("lte_on", false);
            int capturedPhotosDataSourceId = confPrefs.getInt("CAPTURED_PHOTOS", -1);

            //init DbMgr if it's null
            if (DbMgr.getDB() == null)
                DbMgr.init(getApplicationContext());

            Log.e("DataSubmissionService", "onCreate: Created Data submission service");
            DbMgr.cleanupUselessData();

            if (Tools.isConnectedToWifi(getApplicationContext()) || (Tools.isNetworkAvailable() && lteEnabled)) {
                Log.e("DataSubmissionService", "Data submission service running... Network available");
                uploadingSuccessfully = true;

                ids.clear();
                dataSourceIdList.clear();
                timestampsList.clear();
                valueList.clear();

                Cursor cursor = DbMgr.getSensorData();

                if (cursor != null && cursor.moveToFirst()) {
                    boolean stop = false;
                    do {
                        dataSourceIdCheck = cursor.getInt(cursor.getColumnIndex("dataSourceId"));

                        if (dataSourceIdCheck!=capturedPhotosDataSourceId) {
                            ids.add(cursor.getInt(cursor.getColumnIndex("id")));
                            timestampsList.add(cursor.getLong(cursor.getColumnIndex("timestamp")));
                            dataSourceIdList.add(cursor.getInt(cursor.getColumnIndex("dataSourceId")));
                            valueList.add(ByteString.copyFrom(cursor.getString(cursor.getColumnIndex("data")), StandardCharsets.UTF_8));

                            if (ids.size() == bulkSize) {
                                stop = true;
                                Log.e("DataSubmissionService", "Bulk size is 500");
                            }

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

                try{
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
                        Log.e("DataSubmissionService", "SUCCESS");
                        for (int id : ids) {
                            try {
                                DbMgr.deleteRecord(id);
                            } catch (Exception exception) {
                                Log.e("DataSubmissionService", "Error with deleting the record");
                            }
                        }
                    }

                }catch (StatusRuntimeException e) {
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

                Cursor cursor = DbMgr.getSensorData();

                if (cursor.moveToFirst()) {
                    ManagedChannel channel = ManagedChannelBuilder.forAddress(
                            getString(R.string.grpc_host),
                            Integer.parseInt(getString(R.string.grpc_port))
                    ).usePlaintext().build();

                    ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);

                    loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
                    int userId = loginPrefs.getInt(AuthActivity.user_id, -1);
                    String email = loginPrefs.getString(AuthActivity.usrEmail, null);

                    try {
                        do {
                            dataSourceIdCheck = cursor.getColumnIndex("dataSourceId");
                            if(dataSourceIdCheck == capturedPhotosDataSourceId) {
                                long timestamp = cursor.getLong(cursor.getColumnIndex("timestamp"));
                                EtService.SubmitDataRecord.Request submitDataRecordRequest = EtService.SubmitDataRecord.Request.newBuilder()
                                        .setUserId(userId)
                                        .setEmail(email)
                                        .setCampaignId(Integer.parseInt(getString(R.string.campaign_id)))
                                        .setDataSource(cursor.getInt(cursor.getColumnIndex("dataSourceId")))
                                        .setTimestamp(timestamp)
                                        .setValue(ByteString.copyFrom(cursor.getString(cursor.getColumnIndex("data")), StandardCharsets.UTF_8))
                                        .build();

                                EtService.SubmitDataRecord.Response responseMessage = stub.submitDataRecord(submitDataRecordRequest);

                                if (responseMessage.getSuccess()) {
                                    DbMgr.deleteRecord(cursor.getInt(cursor.getColumnIndex("id")));
                                }
                            }
                        } while (cursor.moveToNext());
                    } catch (StatusRuntimeException e) {
                        Log.e("DataSubmissionService Photos", "DataCollectorService.setUpDataSubmissionThread() exception: " + e.getMessage());
                        uploadingSuccessfully = false;
                        e.printStackTrace();
                    } finally {
                        channel.shutdown();
                    }
                }
                cursor.close();
            }



            dataSubmissionHandler.postDelayed(dataSubmissionRunnable, DATASUBMISSION_PERIOD * 1000);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        dataSubmissionHandler.post(dataSubmissionRunnable);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channel_id = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel() : "";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channel_id)
                .setOngoing(true)
                .setSubText(getString(R.string.noti_service_running))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSmallIcon(R.mipmap.ic_launcher_no_bg)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);
        Notification notification = builder.build();
        startForeground(ID_SERVICE, notification);

        return START_STICKY;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public String createNotificationChannel() {
        String id = "YouNoOne_channel_id";
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
        mNotificationManager.cancel(ID_SERVICE);
        mNotificationManager.cancel(PERMISSION_REQUEST_NOTIFICATION_ID);
        dataSubmissionHandler.removeCallbacks(dataSubmissionRunnable);
        //endregion
    }
}
