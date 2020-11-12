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
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.TrafficStats;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.github.instagram4j.instagram4j.IGAndroidDevice;
import com.github.instagram4j.instagram4j.IGClient;
import com.github.instagram4j.instagram4j.models.media.reel.ReelMedia;
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineMedia;
import com.github.instagram4j.instagram4j.requests.direct.DirectInboxRequest;
import com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest;
import com.github.instagram4j.instagram4j.requests.feed.FeedUserStoryRequest;
import com.github.instagram4j.instagram4j.requests.users.UsersInfoRequest;
import com.github.instagram4j.instagram4j.responses.direct.DirectInboxResponse;
import com.github.instagram4j.instagram4j.responses.feed.FeedUserResponse;
import com.github.instagram4j.instagram4j.responses.feed.FeedUserStoryResponse;
import com.github.instagram4j.instagram4j.responses.users.UserResponse;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.protobuf.ByteString;
import com.nematjon.edd_client_season_two.AppUseDb;
import com.nematjon.edd_client_season_two.AuthActivity;
import com.nematjon.edd_client_season_two.Camera2Capture;
import com.nematjon.edd_client_season_two.DbMgr;
import com.nematjon.edd_client_season_two.MainActivity;
import com.nematjon.edd_client_season_two.R;
import com.nematjon.edd_client_season_two.StoredMedia;
import com.nematjon.edd_client_season_two.smartwatch.SAPAndroidAgent;
import com.nematjon.edd_client_season_two.Tools;
import com.nematjon.edd_client_season_two.receivers.ActivityTransRcvr;
import com.nematjon.edd_client_season_two.receivers.CallRcvr;
import com.nematjon.edd_client_season_two.receivers.ScreenAndUnlockRcvr;
import com.nematjon.edd_client_season_two.receivers.SignificantMotionDetector;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import inha.nsl.easytrack.ETServiceGrpc;
import inha.nsl.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import static com.nematjon.edd_client_season_two.smartwatch.SAPAndroidAgent.mProviderServiceSocket;


public class MainService extends Service implements SensorEventListener, LocationListener {
    private static final String TAG = MainService.class.getSimpleName();

    //region Constants
    private static final int ID_SERVICE = 101;
    public static final int EMA_NOTIFICATION_ID = 1234; // in sec
    public static final int PERMISSION_REQUEST_NOTIFICATION_ID = 1111; // in sec
    public static final long EMA_RESPONSE_EXPIRE_TIME = 60 * 60;  // in sec
    public static final int SERVICE_START_X_MIN_BEFORE_EMA = 3 * 60; // min
    public static final short HEARTBEAT_PERIOD = 30;  // in sec
    public static final short DATA_SUBMIT_PERIOD = 60;  // in sec
    private static final short AUDIO_RECORDING_PERIOD = 2 * 60;  // in sec
    private static final short LIGHT_SENSOR_PERIOD = 5 * 60;  // in sec
    private static final short PRESSURE_SENSOR_PERIOD = 5 * 60; // in sec
    private static final short GRAVITY_SENSOR_PERIOD = 3; // in sec
    private static final short PRESSURE_SENSOR_DURATION = 2; // in sec
    private static final short AUDIO_RECORDING_DURATION = 5;  // in sec
    private static final int APP_USAGE_SEND_PERIOD = 3; // in sec
    private static final int WIFI_SCANNING_PERIOD = 31 * 60; // in sec
    private static final int TAKE_PHOTO_PERIOD = 15; // in sec
    private static final int INSTAGRAM_PERIOD = 6 * 60 * 60; // in sec
    private static final int SMARTWATCH_PERIOD = 90; // in sec
    private static final int APP_USAGE_PERIOD = 60 * 60; // in sec
    private static final int NETWORK_USAGE_PERIOD = 20 * 60; // in sec
    private static final int STORED_MEDIA_PERIOD = 4 * 60 * 60; // in sec
    private static final int CALENDAR_EVENTS_PERIOD = 4 * 60 * 60; // in sec
    private static final int DEVICE_INFO_PERIOD = 24 * 60 * 60; // in sec
    private static final int INSTAGRAM_POSTS_NUMBER = 5;
    private static final int HOURS24 = 24 * 60 * 60; // in sec
    private static final int LOCATION_UPDATE_MIN_INTERVAL = 5 * 60 * 1000; //milliseconds
    private static final int LOCATION_UPDATE_MIN_DISTANCE = 0; // meters
    private static final float Y_GRAVITY_MIN = 7.6f;
    public static final String LOCATIONS_TXT = "locations.txt";
    //endregion


    //region Variables
    private SensorManager sensorManager;
    private Sensor sensorStepDetect;
    private Sensor sensorPressure;
    private Sensor sensorLight;
    private Sensor sensorSM;
    private Sensor sensorGravity;
    private SignificantMotionDetector SMListener;
    boolean stopPressureSense = true;
    boolean canPressureSense = false;
    boolean canGravitySense = false;

    public static boolean uploadingSuccessfully = true;

    static SharedPreferences loginPrefs;
    static SharedPreferences confPrefs;
    static SharedPreferences phoneUsageVariablesPrefs;
    static SharedPreferences instagramPrefs;
    static SharedPreferences networkPrefs;

    static int stepDetectorDataSrcId;
    static int pressureDataSrcId;
    static int lightDataSrcId;
    static int gravityDataSrcId;
    static int wifiScanDataSrcId;
    static int instagramDataSrcId;

    private static long prevLightStartTime = 0;
    private static long prevGravityStopTime = 0;
    private static long prevPressureStopTime = 0;
    private static long prevAudioRecordStartTime = 0;
    private static long prevWifiScanStartTime = 0;
    private static long prevAppUsageStartTime = System.currentTimeMillis(); //at the beginning app usage db is empty
    private static long prevNetworkUsageStartTime = System.currentTimeMillis();
    private static long prevCalendarEventsStartTime = 0;
    private static long prevStoredMediaStartTime = 0;
    private static long prevDeviceInfoStartTime = 0;

    static NotificationManager mNotificationManager;
    static Boolean permissionNotificationPosted;

    private ScreenAndUnlockRcvr mPhoneUnlockedReceiver;
    private CallRcvr mCallReceiver;

    private AudioFeatureRecorder audioFeatureRecorder;

    private LocationManager locationManager;
    private CameraManager manager;
    private Object cameraCallback;

    private ActivityRecognitionClient activityTransitionClient;
    private PendingIntent activityTransPendingIntent;

    private boolean isCameraAvailable = false;
    public static float x_value_gravity = 0f;
    public static float y_value_gravity = 0f;
    public static float z_value_gravity = 0f;

    int direct_unseen_dialogs_count = 0;
    int direct_pending_requests_dialogs_count = 0;
    int story_viewers_count = 0;
    int story_total_count = 0;
    long story_taken_at_timestamp = 0;
    long story_expires_timestamp = 0;
    long userfeed_taken_at_timestamp = 0;
    int userfeed_like_count = 0;
    int userfeed_comment_count = 0;
    int userfeed_items_count = 0;
    boolean userfeed_likes_photo_himself = false;
    int userinfo_followers_count = 0;
    int userinfo_following_count = 0;
    int userinfo_total_media_count = 0;
    String userinfo_total_igtv_videos = "0";
    String userinfo_besties_count = "0";
    String userinfo_usertags_count = "0";
    String userinfo_following_tag_count = "0";
    String userinfo_recently_bestied_by_count = "0";
    String userinfo_has_highlight_reels = "false";
    String userinfo_total_clips_count = "0";

    String instagram_username_type = "USERNAME";
    String direct_unseen_dialogs_count_type = "UNSEEN DIALOGS";
    String direct_pending_requests_dialogs_count_type = "PENDING DIALOGS";
    String story_viewers_count_type = "STORY VIEWERS";
    String story_total_count_type = "STORIES";
    String story_taken_at_timestamp_type = "STORY UPLOADED TIME";
    String story_expires_timestamp_type = "STORY EXPIRED TIME";
    String userfeed_taken_at_timestamp_type = "USERFEED UPLOADED TIME";
    String userfeed_like_count_type = "USERFEED LIKES";
    String userfeed_comment_count_type = "USERFEED COMMENTS";
    String userfeed_items_count_type = "USERFEED ITEMS";
    String userfeed_likes_photo_himself_type = "LIKES HIMSELF";
    String userinfo_followers_count_type = "FOLLOWERS";
    String userinfo_following_count_type = "FOLLOWING";
    String userinfo_total_media_count_type = "TOTAL MEDIA";
    String userinfo_total_igtv_videos_type = "IGTV VIDEOS";
    String userinfo_besties_count_type = "BESTIES";
    String userinfo_usertags_count_type = "USERTAGS";
    String userinfo_following_tag_count_type = "FOLLOWING TAGS";
    String userinfo_recently_bestied_by_count_type = "BESTIED BY";
    String userinfo_has_highlight_reels_type = "HAS HIGHLIGHT REELS";
    String userinfo_total_clips_count_type = "CLIPS";
    //endregion


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
            boolean canStartAudioRecord = (nowTime > prevAudioRecordStartTime + AUDIO_RECORDING_PERIOD * 1000) && (!CallRcvr.AudioRunningForCall); // we get garbage when accessing mic during calls
            boolean stopAudioRecord = (nowTime > prevAudioRecordStartTime + AUDIO_RECORDING_DURATION * 1000) || CallRcvr.AudioRunningForCall;
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
            String data_type_bssid = "BSSID";
            String data_type_ssid = "SSID";
            ArrayList<String> BSSIDs = new ArrayList<>();
            ArrayList<String> SSIDs = new ArrayList<>();
            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            if (canWifiScan) {
                assert wifiManager != null;
                if (wifiManager.isWifiEnabled()) {
                    wifiManager.startScan();
                    wifiList = wifiManager.getScanResults();
                    for (int i = 0; i < wifiList.size(); i++) {
                        BSSIDs.add(wifiList.get(i).BSSID);
                        SSIDs.add(wifiList.get(i).SSID);

                    }
                    prevWifiScanStartTime = currentTime;
                    if (wifiScanDataSrcId != -1) {
                        DbMgr.saveMixedData(wifiScanDataSrcId, currentTime, 1.0f, currentTime, Arrays.toString(BSSIDs.toArray()).replace(" ", ""), data_type_bssid);
                        currentTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(wifiScanDataSrcId, currentTime, 1.0f, currentTime, Arrays.toString(SSIDs.toArray()).replace(" ", ""), data_type_ssid);
                    }
                }
            }
            //endregion

            // region checking and updating device information
            currentTime = System.currentTimeMillis();
            boolean canDeviceInfoCheck = (currentTime > prevDeviceInfoStartTime + DEVICE_INFO_PERIOD * 1000);
            String current_device_model = Build.MODEL;
            int current_api_level = Build.VERSION.SDK_INT;
            boolean deviceInfoChanged = false;

            int deviceInfoSourceId = confPrefs.getInt("ANDROID_DEVICE_INFO", -1);
            String deviceModelType = "DEVICE MODEL";
            String apiLevelType = "API";

            if (canDeviceInfoCheck) {
                String stored_device_model = loginPrefs.getString("deviceModel", null);
                int stored_api_level = loginPrefs.getInt("apiLevel", 0);


                if (!current_device_model.equals(stored_device_model) || (current_api_level != stored_api_level)) {
                    SharedPreferences.Editor editorLogin = loginPrefs.edit();
                    editorLogin.putString("deviceModel", current_device_model);
                    editorLogin.putInt("apiLevel", current_api_level);
                    editorLogin.apply();
                }

                String updated_device_model = loginPrefs.getString("deviceModel", null);
                int updated_api_level = loginPrefs.getInt("apiLevel", 0);

                if (deviceInfoSourceId != -1) {
                    nowTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(deviceInfoSourceId, nowTime, 1.0f, nowTime, updated_device_model, deviceModelType);
                    nowTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(deviceInfoSourceId, nowTime, 1.0f, nowTime, updated_api_level, apiLevelType);
                }

                prevDeviceInfoStartTime = currentTime;
            }
            // endregion

            //region saving the total number of Calendar Events
            Cursor calendarCursor;
            int total_number_of_events = 0;
            String calendar_type = "EVENT";
            currentTime = System.currentTimeMillis();
            boolean canCalendarEventsScan = (currentTime > prevCalendarEventsStartTime + CALENDAR_EVENTS_PERIOD * 1000);
            if (canCalendarEventsScan) {
                calendarCursor = getApplicationContext().getContentResolver().query(CalendarContract.Events.CONTENT_URI, null, null, null, null);
                if (calendarCursor != null) {
                    total_number_of_events = calendarCursor.getCount();
                }
                int calendarSourceId = confPrefs.getInt("CALENDAR", -1);
                if (calendarSourceId != -1) {
                    nowTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(calendarSourceId, nowTime, 1.0f, nowTime, total_number_of_events, calendar_type);
                }
                assert calendarCursor != null;
                calendarCursor.close();

                prevCalendarEventsStartTime = currentTime;
            }
            //endregion

            //region saving the total number of Images, Music and Video Files stored
            currentTime = System.currentTimeMillis();
            boolean canStoredMediaCheck = (currentTime > prevStoredMediaStartTime + STORED_MEDIA_PERIOD * 1000);
            StoredMedia storedMedia = new StoredMedia();
            String image_media_type = "IMAGE";
            String video_media_type = "VIDEO";
            String music_media_type = "MUSIC";
            int totalNumOfImages = 0;
            int totalNumOfVideoFiles = 0;
            int totalNumOfMusic = 0;
            if (canStoredMediaCheck) {
                totalNumOfImages = storedMedia.totalNumberOfImages(getApplicationContext().getContentResolver());
                totalNumOfVideoFiles = storedMedia.totalNumberOfVideoFiles(getApplicationContext().getContentResolver());
                totalNumOfMusic = storedMedia.totalNumOfMusic(getApplicationContext().getContentResolver());
                int storedMediaSourceId = confPrefs.getInt("STORED_MEDIA", -1);
                if (storedMediaSourceId != -1) {
                    nowTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(storedMediaSourceId, nowTime, 1.0f, nowTime, totalNumOfImages, image_media_type);
                    nowTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(storedMediaSourceId, nowTime, 1.0f, nowTime, totalNumOfVideoFiles, video_media_type);
                    nowTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(storedMediaSourceId, nowTime, 1.0f, nowTime, totalNumOfMusic, music_media_type);
                }
                prevStoredMediaStartTime = currentTime;
            }
            //endregion

            //region saving transmitted & received network data
            currentTime = System.currentTimeMillis();
            boolean canNetworkUsageCheck = (currentTime > prevNetworkUsageStartTime + NETWORK_USAGE_PERIOD * 1000);
            networkPrefs = getSharedPreferences("NetworkVariables", MODE_PRIVATE);
            String usage_tx_type = "TX";
            String usage_rx_type = "RX";

            if (canNetworkUsageCheck) {

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
                prevNetworkUsageStartTime = nowTime;
                //endregion

                // saving app usage data
                currentTime = System.currentTimeMillis();
                boolean canAppUsageUpload = (currentTime > prevAppUsageStartTime + APP_USAGE_PERIOD * 1000);
                if (canAppUsageUpload) {
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

                    prevAppUsageStartTime = currentTime;
                }
                //endregion

            }

            mainHandler.postDelayed(mainRunnable, 5 * 1000);
        }
    };

    private Handler dataSubmissionHandler = new Handler();
    private Runnable dataSubmitRunnable = new Runnable() {
        @Override
        public void run() {
            Log.e(TAG, "Data submission Runnable run()");
            new Thread(() -> {
                Log.e(TAG, "Data submission Runnable run() -> Thread run()");
                DbMgr.cleanupUselessData();
                if (Tools.isConnectedToWifi(getApplicationContext())) {
                    Log.e(TAG, "Data submission Runnable run() -> Thread run() -> Network available condition (True)");
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
                            } while (cursor.moveToNext());
                        } catch (StatusRuntimeException e) {
                            Log.e(TAG, "DataCollectorService.setUpDataSubmissionThread() exception: " + e.getMessage());
                            uploadingSuccessfully = false;
                            e.printStackTrace();
                        } finally {
                            channel.shutdown();
                        }
                    }
                    cursor.close();
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
            Log.e(TAG, "heartbeat");
            //before sending heart-beat check permissions granted or not. If not grant first
            if (!Tools.hasPermissions(getApplicationContext(), Tools.PERMISSIONS) && !permissionNotificationPosted) {
                permissionNotificationPosted = true;
                sendNotificationForPermissionSetting(); // send notification if any permission is disabled
            }
            Tools.sendHeartbeat(MainService.this);
            heartBeatHandler.postDelayed(heartBeatSendRunnable, HEARTBEAT_PERIOD * 1000);
            Log.e(TAG, "run: Heartbeat is sent");
        }
    };


    private Handler takePhotoHandler = new Handler();
    private Runnable takePhotoRunnable = new Runnable() {
        @Override
        public void run() {
            boolean phoneUnlocked = phoneUsageVariablesPrefs.getBoolean("unlocked", false);
            if (phoneUnlocked) {
                // check position of the phone
                if (y_value_gravity > Y_GRAVITY_MIN || y_value_gravity == 0f) { // 0 when the device does not have a gravity sensor
                    //check whether camera is in use
                    boolean cameraAvailable = phoneUsageVariablesPrefs.getBoolean("isCameraAvailable", false);
                    if (cameraAvailable) {
                        //take a photo
                        Camera2Capture camera2Capture = new Camera2Capture(getApplicationContext());
                        camera2Capture.setupCamera2();
                    } else {
                        Log.e(TAG, "Camera not available");
                    }
                }
            }
            takePhotoHandler.postDelayed(takePhotoRunnable, TAKE_PHOTO_PERIOD * 1000);
        }
    };

    private Handler instagramHandler = new Handler();
    private Runnable instagramRunnable = new Runnable() {
        @Override
        public void run() {

            InstagramFeaturesCollector instagramFeaturesCollector = new InstagramFeaturesCollector();
            instagramFeaturesCollector.execute();

            instagramHandler.postDelayed(instagramRunnable, INSTAGRAM_PERIOD * 1000);
        }

    };

    private Handler getDataFromSmartwatchHandler = new Handler();
    private Runnable getDataFromSmartWatchRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (mProviderServiceSocket != null) {
                    boolean sent = SAPAndroidAgent.sendMessage(new byte[]{1});
                    Log.e(TAG, "Request data from SmartWatch : " + sent);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            getDataFromSmartwatchHandler.postDelayed(getDataFromSmartWatchRunnable, SMARTWATCH_PERIOD * 1000);
        }
    };

    private Handler emaPopUpCheckHandler = new Handler();
    private Runnable emaPopUpCheckRunnable = new Runnable() {
        @Override
        public void run() {

        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        //init DbMgr if it's null
        if (DbMgr.getDB() == null)
            DbMgr.init(getApplicationContext());


        loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
        confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        phoneUsageVariablesPrefs = getSharedPreferences("PhoneUsageVariablesPrefs", Context.MODE_PRIVATE);
        instagramPrefs = getSharedPreferences("InstagramPrefs", Context.MODE_PRIVATE);

        stepDetectorDataSrcId = confPrefs.getInt("ANDROID_STEP_DETECTOR", -1);
        pressureDataSrcId = confPrefs.getInt("ANDROID_PRESSURE", -1);
        lightDataSrcId = confPrefs.getInt("ANDROID_LIGHT", -1);
        gravityDataSrcId = confPrefs.getInt("ANDROID_GRAVITY", -1);
        wifiScanDataSrcId = confPrefs.getInt("ANDROID_WIFI", -1);
        instagramDataSrcId = confPrefs.getInt("INSTAGRAM_FEATURES", -1);


        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            sensorPressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
            sensorLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            sensorStepDetect = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            sensorGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            sensorManager.registerListener(this, sensorStepDetect, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, sensorPressure, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, sensorLight, SensorManager.SENSOR_DELAY_NORMAL);
            if (sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
                sensorManager.registerListener(this, sensorGravity, SensorManager.SENSOR_DELAY_NORMAL);
            }
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
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Registered: Activity Transition"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed: Activity Transition " + e.toString()));

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

        //region Checking Camera Availability if phone is unlocked
        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Handler cameraAvailabilityHandler = new Handler();
        assert manager != null;
        cameraCallback = new CameraManager.AvailabilityCallback() {
            @Override
            public void onCameraAvailable(String cameraId) {
                super.onCameraAvailable(cameraId);
                Log.e("TAG", "onCameraAvailable: Camera off");
                isCameraAvailable = true;
                SharedPreferences.Editor editor = phoneUsageVariablesPrefs.edit();
                editor.putBoolean("isCameraAvailable", isCameraAvailable);
                editor.apply();
            }

            @Override
            public void onCameraUnavailable(String cameraId) {
                super.onCameraUnavailable(cameraId);
                Log.e("TAG", "onCameraUnavailable: Camera on");
                //Do your work
                isCameraAvailable = false;
                SharedPreferences.Editor editor = phoneUsageVariablesPrefs.edit();
                editor.putBoolean("isCameraAvailable", isCameraAvailable);
                editor.apply();
            }
        };
        manager.registerAvailabilityCallback((CameraManager.AvailabilityCallback) cameraCallback, cameraAvailabilityHandler);
        //endregion

        mainHandler.post(mainRunnable);
        heartBeatHandler.post(heartBeatSendRunnable);
        appUsageSaveHandler.post(appUsageSaveRunnable);
        dataSubmissionHandler.post(dataSubmitRunnable);
        takePhotoHandler.post(takePhotoRunnable);
        instagramHandler.post(instagramRunnable);
        getDataFromSmartwatchHandler.post(getDataFromSmartWatchRunnable);
        permissionNotificationPosted = false;

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
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        //region Unregister listeners
        sensorManager.unregisterListener(this, sensorPressure);
        sensorManager.unregisterListener(this, sensorLight);
        sensorManager.unregisterListener(this, sensorStepDetect);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
            sensorManager.unregisterListener(this, sensorGravity);
        }
        sensorManager.cancelTriggerSensor(SMListener, sensorSM);
        activityTransitionClient.removeActivityTransitionUpdates(activityTransPendingIntent);
        if (audioFeatureRecorder != null)
            audioFeatureRecorder.stop();
        unregisterReceiver(mPhoneUnlockedReceiver);
        unregisterReceiver(mCallReceiver);
        mainHandler.removeCallbacks(mainRunnable);
        heartBeatHandler.removeCallbacks(heartBeatSendRunnable);
        appUsageSaveHandler.removeCallbacks(appUsageSaveRunnable);
        dataSubmissionHandler.removeCallbacks(dataSubmitRunnable);
        takePhotoHandler.removeCallbacks(takePhotoRunnable);
        instagramHandler.removeCallbacks(instagramRunnable);
        getDataFromSmartwatchHandler.removeCallbacks(getDataFromSmartWatchRunnable);
        manager.unregisterAvailabilityCallback((CameraManager.AvailabilityCallback) cameraCallback);
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
            long nowTime = System.currentTimeMillis();

            if (prevPressureStopTime == 0) {
                prevPressureStopTime = nowTime;
            } else {
                canPressureSense = (nowTime > prevPressureStopTime + PRESSURE_SENSOR_PERIOD * 1000);
                stopPressureSense = (nowTime > prevPressureStopTime + PRESSURE_SENSOR_DURATION * 1000 + PRESSURE_SENSOR_PERIOD * 1000);
                if (canPressureSense) {
                    timestamp = System.currentTimeMillis();
                    DbMgr.saveMixedData(pressureDataSrcId, timestamp, event.accuracy, timestamp, event.values[0]);
                }
                if (stopPressureSense) {
                    prevPressureStopTime = nowTime;
                }
            }
        } else if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            long nowTime = System.currentTimeMillis();
            boolean canLightSense = (nowTime > prevLightStartTime + LIGHT_SENSOR_PERIOD * 1000);
            if (canLightSense) {
                timestamp = System.currentTimeMillis();
                DbMgr.saveMixedData(lightDataSrcId, timestamp, event.accuracy, timestamp, event.values[0]);
                prevLightStartTime = nowTime;
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            long nowTime = System.currentTimeMillis();
            canGravitySense = (nowTime > prevGravityStopTime + GRAVITY_SENSOR_PERIOD * 1000);
            if (canGravitySense) {
                x_value_gravity = event.values[0];
                y_value_gravity = event.values[1];
                z_value_gravity = event.values[2];

                SharedPreferences.Editor editor = phoneUsageVariablesPrefs.edit();
                editor.putFloat("y_value_gravity", y_value_gravity);
                editor.apply();

                DbMgr.saveMixedData(gravityDataSrcId, nowTime, 1.0f, nowTime, x_value_gravity, y_value_gravity, z_value_gravity);
                prevGravityStopTime = nowTime;
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

    public class InstagramFeaturesCollector extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            String instagramUsername = instagramPrefs.getString("instagram_username", "");
            String instagramPassword = instagramPrefs.getString("instagram_password", "");
            if (Tools.isNetworkAvailable()) {
                assert instagramUsername != null;
                assert instagramPassword != null;
                if (!instagramUsername.isEmpty() && !instagramPassword.isEmpty()) {
                    try {
                        //creating instagram client
                        IGClient client = IGClient.builder()
                                .username(instagramUsername)
                                .password(instagramPassword)
                                .login();

                        //region setting the user's device
                        final String androidVersion = Build.VERSION.SDK_INT + "";
                        final String androidRelease = Build.VERSION.RELEASE + "";
                        final String dpi = getResources().getDisplayMetrics().densityDpi + "";
                        final String displayResolution = getResources().getDisplayMetrics().widthPixels + "x" + getResources().getDisplayMetrics().heightPixels;
                        final String manufacturer = Build.MANUFACTURER + "";
                        final String model = Build.MODEL + "";
                        final String device = Build.DEVICE + "";
                        final String cpu = Build.BOARD + "";

                        final String androidDeviceInfo = androidVersion + "/" + androidRelease + "; " + dpi + "dpi; " + displayResolution + "; " + manufacturer + "; " + model + "; " + device + "; " + cpu;
                        IGAndroidDevice igAndroidDevice = new IGAndroidDevice(androidDeviceInfo);
                        client.setDevice(igAndroidDevice);

                        //endregion
                        DbMgr.saveMixedData(instagramDataSrcId, System.currentTimeMillis(), 1.0f, System.currentTimeMillis(), instagramUsername, instagram_username_type);
                        //region user info
                        UsersInfoRequest usersInfoRequest = new UsersInfoRequest((client.getSelfProfile().getPk()));
                        CompletableFuture<UserResponse> userResponse = client.sendRequest(usersInfoRequest);

                        try {
                            userinfo_total_media_count = userResponse.get().getUser().getMedia_count();
                        } catch (Exception e) {
                            userinfo_total_media_count = 0;
                        }

                        try {
                            userinfo_followers_count = userResponse.get().getUser().getFollower_count();
                        } catch (Exception e) {
                            userinfo_followers_count = 0;
                        }

                        try {
                            userinfo_following_count = userResponse.get().getUser().getFollowing_count();
                        } catch (Exception e) {
                            userinfo_following_count = 0;
                        }

                        userinfo_usertags_count = userResponse.get().getUser().get("usertags_count").toString();
                        userinfo_total_igtv_videos = userResponse.get().getUser().get("total_igtv_videos").toString();
                        userinfo_besties_count = userResponse.get().getUser().get("besties_count").toString();
                        userinfo_following_tag_count = userResponse.get().getUser().get("following_tag_count").toString();
                        userinfo_recently_bestied_by_count = userResponse.get().getUser().get("recently_bestied_by_count").toString();
                        userinfo_has_highlight_reels = userResponse.get().getUser().get("has_highlight_reels").toString();
                        userinfo_total_clips_count = userResponse.get().getUser().get("total_clips_count").toString();

                        Thread.sleep(200);
                        //endregion

                        //region direct
                        CompletableFuture<DirectInboxResponse> directInboxResponse = new DirectInboxRequest().execute(client);
                        try {
                            direct_unseen_dialogs_count = directInboxResponse.get().getInbox().getUnseen_count();
                            direct_pending_requests_dialogs_count = directInboxResponse.get().getPending_requests_total();
                        } catch (Exception e) {
                            direct_unseen_dialogs_count = 0;
                            direct_pending_requests_dialogs_count = 0;
                        }

                        Thread.sleep(200);
                        //endregion

                        //region story
                        FeedUserStoryRequest storyRequest = new FeedUserStoryRequest(client.getSelfProfile().getPk());
                        CompletableFuture<FeedUserStoryResponse> feedUserStoryResponse = client.sendRequest(storyRequest);

                        try {
                            story_total_count = feedUserStoryResponse.get().getReel().getMedia_count();
                        } catch (Exception e) {
                            story_total_count = 0;
                        }

                        if (story_total_count != 0) {
                            for (ReelMedia reelMedia : feedUserStoryResponse.get().getReel().getItems()) {
                                story_viewers_count = reelMedia.getViewer_count();
                                story_taken_at_timestamp = reelMedia.getTaken_at();
                                story_expires_timestamp = (HOURS24 * 1000) + story_taken_at_timestamp;
                            }
                        }
                        Thread.sleep(200);
                        //endregion

                        //region user's feed
                        FeedUserRequest feedUserRequest = new FeedUserRequest(client.getSelfProfile().getPk());
                        CompletableFuture<FeedUserResponse> feedUserResponse = client.sendRequest(feedUserRequest);

                        long nowTime = System.currentTimeMillis();
                        for (TimelineMedia timelineMedia : feedUserResponse.get().getItems()) {
                            if (userfeed_items_count < INSTAGRAM_POSTS_NUMBER) {
                                userfeed_taken_at_timestamp = timelineMedia.getTaken_at();
                                userfeed_comment_count = timelineMedia.getComment_count();
                                userfeed_like_count = timelineMedia.getLike_count();
                                userfeed_likes_photo_himself = timelineMedia.isHas_liked();
                                userfeed_items_count++;

                                DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userfeed_taken_at_timestamp, userfeed_taken_at_timestamp_type);
                                nowTime = System.currentTimeMillis();
                                DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userfeed_likes_photo_himself, userfeed_likes_photo_himself_type);
                                nowTime = System.currentTimeMillis();
                                DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userfeed_like_count, userfeed_like_count_type);
                                nowTime = System.currentTimeMillis();
                                DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userfeed_comment_count, userfeed_comment_count_type);
                                nowTime = System.currentTimeMillis();
                            } else {
                                DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userfeed_items_count, userfeed_items_count_type);
                                userfeed_items_count = 0;
                                break;
                            }
                        }

                        //endregion

                        //region Instagram data submission
                        nowTime = System.currentTimeMillis();

                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userinfo_total_media_count, userinfo_total_media_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userinfo_followers_count, userinfo_followers_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userinfo_following_count, userinfo_following_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userinfo_usertags_count, userinfo_usertags_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userinfo_total_igtv_videos, userinfo_total_igtv_videos_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userinfo_besties_count, userinfo_besties_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userinfo_following_tag_count, userinfo_following_tag_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userinfo_recently_bestied_by_count, userinfo_recently_bestied_by_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userinfo_has_highlight_reels, userinfo_has_highlight_reels_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, userinfo_total_clips_count, userinfo_total_clips_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, direct_unseen_dialogs_count, direct_unseen_dialogs_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, direct_pending_requests_dialogs_count, direct_pending_requests_dialogs_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, story_total_count, story_total_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, story_viewers_count, story_viewers_count_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, story_taken_at_timestamp, story_taken_at_timestamp_type);
                        nowTime = System.currentTimeMillis();
                        DbMgr.saveMixedData(instagramDataSrcId, nowTime, 1.0f, nowTime, story_expires_timestamp, story_expires_timestamp_type);

                        //endregion
                    } catch (IOException | InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
    }


}
