package com.nematjon.edd_client_season_two.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.nematjon.edd_client_season_two.AuthActivity;
import com.nematjon.edd_client_season_two.DbMgr;
import com.nematjon.edd_client_season_two.MainActivity;
import com.nematjon.edd_client_season_two.R;
import com.nematjon.edd_client_season_two.Tools;
import com.nematjon.edd_client_season_two.receivers.ActivityTransRcvr;
import com.nematjon.edd_client_season_two.receivers.CallRcvr;
import com.nematjon.edd_client_season_two.receivers.ScreenAndUnlockRcvr;
import com.nematjon.edd_client_season_two.receivers.SignificantMotionDetector;

import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import inha.nsl.easytrack.ETServiceGrpc;
import inha.nsl.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

public class MainService extends Service implements SensorEventListener, LocationListener {
    private static final String TAG = MainService.class.getSimpleName();

    //region Constants
    private static final int ID_SERVICE = 101;
    public static final int EMA_NOTIFICATION_ID = 1234; //in sec
    public static final int PERMISSION_REQUEST_NOTIFICATION_ID = 1111; //in sec
    public static final long EMA_RESPONSE_EXPIRE_TIME = 60 * 60;  //in sec
    public static final int SERVICE_START_X_MIN_BEFORE_EMA = 3 * 60; //min
    public static final short HEARTBEAT_PERIOD = 30;  //in sec
    public static final short DATA_SUBMIT_PERIOD = 5 * 60;  //in sec
    private static final short AUDIO_RECORDING_PERIOD = 5 * 60;  //in sec
    private static final short LIGHT_SENSOR_PERIOD = 30;  //in sec
    private static final short AUDIO_RECORDING_DURATION = 5;  //in sec
    private static final int APP_USAGE_SEND_PERIOD = 3; //in sec
    private static final int WIFI_SCANNING_PERIOD = 31 * 60; //in sec

    private static final int LOCATION_UPDATE_MIN_INTERVAL = 5 * 60 * 1000; //milliseconds
    private static final int LOCATION_UPDATE_MIN_DISTANCE = 0; // meters
    public static final String LOCATIONS_TXT = "locations.txt";
    //endregion


    private SensorManager sensorManager;
    private Sensor sensorStepDetect;
    private Sensor sensorPressure;
    private Sensor sensorLight;
    private Sensor sensorSM;
    private SignificantMotionDetector SMListener;


    static SharedPreferences loginPrefs;
    static SharedPreferences confPrefs;

    static int stepDetectorDataSrcId;
    static int pressureDataSrcId;
    static int lightDataSrcId;
    static int wifiScanDataSrcId;

    private static long prevLightStartTime = 0;
    private static long prevAudioRecordStartTime = 0;
    private static long prevWifiScanStartTime = 0;

    static NotificationManager mNotificationManager;
    static Boolean permissionNotificationPosted;

    private ScreenAndUnlockRcvr mPhoneUnlockedReceiver;
    private CallRcvr mCallReceiver;

    private AudioFeatureRecorder audioFeatureRecorder;

    private LocationManager locationManager;

    private ActivityRecognitionClient activityTransitionClient;
    private PendingIntent activityTransPendingIntent;

    private Handler mainHandler = new Handler();
    private Runnable mainRunnable = new Runnable() {
        @Override
        public void run() {

            //check if all permissions are set then dismiss notification for request
            if (Tools.hasPermissions(getApplicationContext(), Tools.PERMISSIONS)) {
                mNotificationManager.cancel(PERMISSION_REQUEST_NOTIFICATION_ID);
                permissionNotificationPosted = false;
            }

            //region Registering Audio recorder periodically
            long nowTime = System.currentTimeMillis();
            boolean canStartAudioRecord = (nowTime > prevAudioRecordStartTime + AUDIO_RECORDING_PERIOD * 1000) || CallRcvr.AudioRunningForCall;
            boolean stopAudioRecord = (nowTime > prevAudioRecordStartTime + AUDIO_RECORDING_DURATION * 1000);
            if (canStartAudioRecord) {
                if (audioFeatureRecorder == null)
                    audioFeatureRecorder = new AudioFeatureRecorder(MainService.this);
                audioFeatureRecorder.start();
                prevAudioRecordStartTime = nowTime;
            } else if (stopAudioRecord) {
                if (audioFeatureRecorder != null) {
                    audioFeatureRecorder.stop();
                    audioFeatureRecorder = null;
                }
            }
            //endregion

            //region Scanning WiFi Fingerprints periodically
            long currentTime = System.currentTimeMillis();
            boolean canWifiScan = (currentTime > prevWifiScanStartTime + WIFI_SCANNING_PERIOD * 1000);
            List<ScanResult> wifiList;
            ArrayList<String> BSSIDs = new ArrayList<>();
            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            if (canWifiScan) {
                assert wifiManager != null;
                if (wifiManager.isWifiEnabled()) {
                    wifiManager.startScan();
                    wifiList = wifiManager.getScanResults();
                    for (int i = 0; i < wifiList.size(); i++) {
                        BSSIDs.add(wifiList.get(i).BSSID);
                    }
                    DbMgr.saveMixedData(wifiScanDataSrcId, currentTime, 1.0f, currentTime, BSSIDs);
                    prevWifiScanStartTime = currentTime;
                }
            }
            //endregion

            mainHandler.postDelayed(mainRunnable, 5 * 1000);
        }
    };

    private Handler dataSubmissionHandler = new Handler();
    private Runnable dataSubmitRunnable = new Runnable() {
        @Override
        public void run() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (Tools.isNetworkAvailable()) {
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
                                    EtService.SubmitDataRecordRequestMessage submitDataRecordRequestMessage = EtService.SubmitDataRecordRequestMessage.newBuilder()
                                            .setUserId(userId)
                                            .setEmail(email)
                                            .setDataSource(cursor.getInt(cursor.getColumnIndex("dataSourceId")))
                                            .setTimestamp(cursor.getLong(cursor.getColumnIndex("timestamp")))
                                            .setValues(cursor.getString(cursor.getColumnIndex("data")))
                                            .build();

                                    EtService.DefaultResponseMessage responseMessage = stub.submitDataRecord(submitDataRecordRequestMessage);
                                    if (responseMessage.getDoneSuccessfully()) {
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
                        cursor.close();
                    }
                }
            }).start();
            dataSubmissionHandler.postDelayed(dataSubmitRunnable, DATA_SUBMIT_PERIOD * 1000);
        }
    };

    private Handler appUsageSaveHandler = new Handler();
    private Runnable appUsageSaveRunnable = new Runnable() {
        public void run() {
            Tools.checkAndSaveUsageAccessStats(getApplicationContext());
            appUsageSaveHandler.postDelayed(this, APP_USAGE_SEND_PERIOD * 1000);
        }
    };

    private Handler heartBeatHandler = new Handler();
    private Runnable heartBeatSendRunnable = new Runnable() {
        public void run() {
            //before sending heart-beat check permissions granted or not. If not grant first
            if (!Tools.hasPermissions(getApplicationContext(), Tools.PERMISSIONS) && !permissionNotificationPosted) {
                permissionNotificationPosted = true;
                sendNotificationForPermissionSetting(); // send notification if any permission is disabled
            }
            Tools.sendHeartbeat(MainService.this);
            heartBeatHandler.postDelayed(heartBeatSendRunnable, HEARTBEAT_PERIOD * 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
        confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        stepDetectorDataSrcId = confPrefs.getInt("ANDROID_STEP_DETECTOR", -1);
        pressureDataSrcId = confPrefs.getInt("ANDROID_PRESSURE", -1);
        lightDataSrcId = confPrefs.getInt("ANDROID_LIGHT", -1);
        wifiScanDataSrcId = confPrefs.getInt("ANDROID_WIFI", -1);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            sensorPressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            sensorLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            sensorStepDetect = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            sensorManager.registerListener(this, sensorStepDetect, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, sensorPressure, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, sensorLight, SensorManager.SENSOR_DELAY_NORMAL);
            SMListener = new SignificantMotionDetector(this);
            sensorSM = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
            if (sensorSM != null) {
                sensorManager.requestTriggerSensor(SMListener, sensorSM);
            } else {
                Log.e(TAG, "Significant motion sensor is NOT available");
            }
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_MIN_INTERVAL, LOCATION_UPDATE_MIN_DISTANCE, this);
        }

        activityTransitionClient = ActivityRecognition.getClient(getApplicationContext());
        activityTransPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(getApplicationContext(), ActivityTransRcvr.class), PendingIntent.FLAG_UPDATE_CURRENT);
        activityTransitionClient.requestActivityTransitionUpdates(new ActivityTransitionRequest(getActivityTransitions()), activityTransPendingIntent)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Registered: Activity Transition");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Failed: Activity Transition " + e.toString());
                    }
                });

        //region Register Phone unlock & Screen On state receiver
        mPhoneUnlockedReceiver = new ScreenAndUnlockRcvr();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mPhoneUnlockedReceiver, filter);
        //endregion

        //region Register Phone call logs receiver
        mCallReceiver = new CallRcvr();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        intentFilter.addAction(Intent.EXTRA_PHONE_NUMBER);
        registerReceiver(mCallReceiver, intentFilter);
        //endregion

        //region Posting Foreground notification when service is started
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
        //endregion

        mainHandler.post(mainRunnable);
        heartBeatHandler.post(heartBeatSendRunnable);
        appUsageSaveHandler.post(appUsageSaveRunnable);
        dataSubmissionHandler.post(dataSubmitRunnable);
        permissionNotificationPosted = false;

        /*sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new SpeechRec());

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"com.nematjon.edd_client_season_two");
//        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
        sr.startListening(intent);*/
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public String createNotificationChannel() {
        String id = "YouNoOne_channel_id";
        String name = "You no one channel id";
        String description = "This is description";
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        //region Unregister listeners
        sensorManager.unregisterListener(this, sensorPressure);
        sensorManager.unregisterListener(this, sensorLight);
        sensorManager.unregisterListener(this, sensorStepDetect);
        sensorManager.cancelTriggerSensor(SMListener, sensorSM);
        // activityRecognitionClient.removeActivityUpdates(activityRecPendingIntent);
        activityTransitionClient.removeActivityTransitionUpdates(activityTransPendingIntent);
        if (audioFeatureRecorder != null)
            audioFeatureRecorder.stop();
        //stopService(stationaryDetector);
        unregisterReceiver(mPhoneUnlockedReceiver);
        unregisterReceiver(mCallReceiver);
        mainHandler.removeCallbacks(mainRunnable);
        heartBeatHandler.removeCallbacks(heartBeatSendRunnable);
        appUsageSaveHandler.removeCallbacks(appUsageSaveRunnable);
        dataSubmissionHandler.removeCallbacks(dataSubmitRunnable);
        locationManager.removeUpdates(this);  //remove location listener
        //endregion

        //region Stop foreground service
        stopForeground(false);
        mNotificationManager.cancel(ID_SERVICE);
        mNotificationManager.cancel(PERMISSION_REQUEST_NOTIFICATION_ID);
        //endregion

        Tools.sleep(1000);

        super.onDestroy();
    }

    private List<ActivityTransition> getActivityTransitions() {
        List<ActivityTransition> transitionList = new ArrayList<>();
        ArrayList<Integer> activities = new ArrayList<>(Arrays.asList(
                DetectedActivity.STILL,
                DetectedActivity.WALKING,
                DetectedActivity.RUNNING,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.IN_VEHICLE));
        for (int activity : activities) {
            transitionList.add(new ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build());

            transitionList.add(new ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build());
        }
        return transitionList;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendNotificationForPermissionSetting() {
        final NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(MainService.this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(MainService.this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String channelId = "YouNoOne_permission_notif";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this.getApplicationContext(), channelId);
        builder.setContentTitle(this.getString(R.string.app_name))
                .setContentText(this.getString(R.string.grant_permissions))
                .setTicker("New Message Alert!")
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher_no_bg)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, this.getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        final Notification notification = builder.build();
        if (notificationManager != null) {
            notificationManager.notify(PERMISSION_REQUEST_NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long timestamp = System.currentTimeMillis();
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            DbMgr.saveMixedData(stepDetectorDataSrcId, timestamp, event.accuracy, timestamp);
        } else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            //Sampling rate is 5~6 samples per second for SENSOR_DELAY_NORMAL
            DbMgr.saveMixedData(pressureDataSrcId, timestamp, event.accuracy, timestamp, event.values[0]);
        } else if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            long nowTime = System.currentTimeMillis();
            boolean canLightSense = (nowTime > prevLightStartTime + LIGHT_SENSOR_PERIOD * 1000);
            if (canLightSense) {
                DbMgr.saveMixedData(lightDataSrcId, timestamp, event.accuracy, timestamp, event.values[0]);
                prevLightStartTime = nowTime;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onLocationChanged(Location location) {
        Log.e(TAG, "Reporting location");
        long nowTime = System.currentTimeMillis();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HH:mm:ss", Locale.KOREA).format(Calendar.getInstance().getTime());
        String resultString = timeStamp + "," + location.getLatitude() + "," + location.getLongitude() + "\n";
        try {
            SharedPreferences prefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
            int dataSourceId = prefs.getInt("LOCATION_GPS", -1);
            // assert dataSourceId != -1;
            DbMgr.saveMixedData(dataSourceId, nowTime, location.getAccuracy(), nowTime, location.getLatitude(), location.getLongitude(), location.getSpeed(), location.getAccuracy(), location.getAltitude());
            FileOutputStream fileOutputStream = openFileOutput(LOCATIONS_TXT, Context.MODE_APPEND);
            fileOutputStream.write(resultString.getBytes());
            fileOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

}
