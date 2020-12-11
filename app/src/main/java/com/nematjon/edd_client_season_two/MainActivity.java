package com.nematjon.edd_client_season_two;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.os.Handler;


import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.material.navigation.NavigationView;
import com.google.protobuf.ByteString;
import com.nematjon.edd_client_season_two.receivers.EMAAlarmRcvr;
import com.nematjon.edd_client_season_two.services.DataSubmissionService;
import com.nematjon.edd_client_season_two.services.EMAOverlayShowingService;
import com.nematjon.edd_client_season_two.services.MainService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import inha.nsl.easytrack.ETServiceGrpc;
import inha.nsl.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import kotlin.text.Charsets;

import com.nematjon.edd_client_season_two.smartwatch.ServiceConnection;
import com.nematjon.edd_client_season_two.smartwatch.SmartwatchActivity;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    //region UI variables
    private DrawerLayout drawerLayout;
    Toolbar toolbar;

    private Button btnEMA;
    private TextView tvServiceStatus;
    private TextView tvInternetStatus;
    public TextView tvFileCount;
    public TextView tvDayNum;
    // public TextView tvEmaNum;
    public TextView tvHBPhone;
   // public TextView tvDataLoadedPhone;
    public TextView tvWatchConnected;
    private RelativeLayout loadingPanel;
    private TextView ema_tv_1;
    private TextView ema_tv_2;
    private TextView ema_tv_3;
    private TextView ema_tv_4;
    private TextView tvRewards;
    private TextView tvBonus;
    private TextView tvTotalReward;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch lteSwitch;
    private NavigationView navigationView;
    private AlertDialog dialog;
    Dialog permissionsPopUp;
    //endregion

    private Intent customSensorsService;
    private Intent dataSubmissionService;

    private SharedPreferences loginPrefs;
    private SharedPreferences configPrefs;
    private SharedPreferences rewardPrefs;

    private static List<String> uniqueValues = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
        boolean firstLaunch = loginPrefs.getBoolean("firstLaunch", true);

        if (firstLaunch) {
            showFirstLaunchActivity();
        }

        init();

        // saving switch button state (use LTE or WiFi only)

        lteSwitch.setChecked(loginPrefs.getBoolean("lte_on", false));
        lteSwitch.setOnClickListener(view -> {
            if(lteSwitch.isChecked()){
                SharedPreferences.Editor editor = getSharedPreferences("UserLogin", MODE_PRIVATE).edit();
                editor.putBoolean("lte_on", true);
                editor.apply();
                lteSwitch.setChecked(true);
                Toast.makeText(this, R.string.lte_enabled_toast, Toast.LENGTH_SHORT).show();
            } else{
                SharedPreferences.Editor editor = getSharedPreferences("UserLogin", MODE_PRIVATE).edit();
                editor.putBoolean("lte_on", false);
                editor.apply();
                lteSwitch.setChecked(false);
                Toast.makeText(this, R.string.lte_disabled_toast, Toast.LENGTH_SHORT).show();
            }
        });

        final SwipeRefreshLayout pullToRefresh = findViewById(R.id.pullToRefresh);
        pullToRefresh.setOnRefreshListener(() -> {
            initUI();
            updateUI();

            new Thread(() -> Tools.sendHeartbeat(getApplicationContext())).start();
            pullToRefresh.setRefreshing(false);

        });


    }

    public void init() {
        //region Init UI variables
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        btnEMA = findViewById(R.id.btn_late_ema);
        tvServiceStatus = findViewById(R.id.tvStatus);
        tvInternetStatus = findViewById(R.id.connectivityStatus);
        tvFileCount = findViewById(R.id.filesCountTextView);
        loadingPanel = findViewById(R.id.loadingPanel);
        tvDayNum = findViewById(R.id.txt_day_num);
        lteSwitch = findViewById(R.id.lte_switch);
        // tvEmaNum = findViewById(R.id.ema_responses_phone);
        tvWatchConnected = findViewById(R.id.watch_connected);
        tvHBPhone = findViewById(R.id.heartbeat_phone);
        //tvDataLoadedPhone = findViewById(R.id.data_loaded_phone);
        ema_tv_1 = findViewById(R.id.ema_tv_1);
        ema_tv_2 = findViewById(R.id.ema_tv_2);
        ema_tv_3 = findViewById(R.id.ema_tv_3);
        ema_tv_4 = findViewById(R.id.ema_tv_4);
        tvRewards = findViewById(R.id.reward_points);
        tvBonus = findViewById(R.id.bonus_points);
        tvTotalReward = findViewById(R.id.total_reward_with_bonus);
        //endregion

        setSupportActionBar(toolbar);

        navigationView.bringToFront();
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_home);

        if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            // only for gingerbread and newer versions
            Tools.PERMISSIONS = new String[]{
                    Manifest.permission.ACTIVITY_RECOGNITION,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.PROCESS_OUTGOING_CALLS,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.CAMERA,
            };
        }


        DbMgr.init(getApplicationContext());

        AppUseDb.init(getApplicationContext());
        loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
        configPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        rewardPrefs = getSharedPreferences("Rewards", Context.MODE_PRIVATE);

        setEmaResetAlarm();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume: ");
        navigationView.setCheckedItem(R.id.nav_home);

        if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
            dialog = Tools.requestPermissions(MainActivity.this);
        }

        new Thread(() -> Tools.sendHeartbeat(getApplicationContext())).start();

        customSensorsService = new Intent(this, MainService.class);
        dataSubmissionService = new Intent(this, DataSubmissionService.class);


        if (Tools.isNetworkAvailable()) {
            loadCampaign();
        } else if (configPrefs.getBoolean("campaignLoaded", false)) {
            try {
                setUpCampaignConfigurations(
                        configPrefs.getString("name", null),
                        configPrefs.getString("notes", null),
                        configPrefs.getString("creatorEmail", null),
                        Objects.requireNonNull(configPrefs.getString("configJson", null)),
                        configPrefs.getLong("startTimestamp", -1),
                        configPrefs.getLong("endTimestamp", -1),
                        configPrefs.getInt("participantCount", -1)
                );
                restartService();
            } catch (JSONException e) {
                e.printStackTrace();
                finish();
                return;
            }
        } else {
            Toast.makeText(this, getResources().getString(R.string.connect_internet_first_launch), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initUI();
        updateUI();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(dataSubmissionService);
        }
        else{
            startService(dataSubmissionService);
        }

    }

    public void initUI() {
        tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.red));
        tvServiceStatus.setText(getString(R.string.service_stopped));
        tvInternetStatus.setTextColor(ContextCompat.getColor(this, R.color.red));
        tvInternetStatus.setText(getString(R.string.internet_off));

        tvDayNum.setText(getResources().getString(R.string.day_num_holder));
        btnEMA.setVisibility(View.GONE);
        tvHBPhone.setText(getResources().getString(R.string.last_active_holder));
        //tvEmaNum.setText(getResources().getString(R.string.ema_responses_holder));
        tvWatchConnected.setText(getResources().getString(R.string.smartwatch));
        // tvDataLoadedPhone.setText(getResources().getString(R.string.data_loaded_holder));

        ema_tv_1.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
        ema_tv_2.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
        ema_tv_3.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
        ema_tv_4.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);

        int rewardPoints = rewardPrefs.getInt("rewardPoints", 0);
        int bonus = rewardPrefs.getInt("bonus", 0);
        int total_reward = rewardPoints + bonus;

        tvRewards.setText(getString(R.string.earned_points, rewardPoints));
        tvBonus.setText(getString(R.string.bonus_points, bonus));
        tvTotalReward.setText(getString(R.string.total_reward_with_bonus, total_reward));

        emaButtonVisibilityCheck();
    }

    public void updateUI() {

        emaButtonVisibilityCheck();

//        else {
//            boolean ema_btn_make_visible = loginPrefs.getBoolean("ema_btn_make_visible", true);
//            if (!ema_btn_make_visible) {
//                btnEMA.setVisibility(View.GONE);
//            } else {
//                btnEMA.setVisibility(View.VISIBLE);
//            }
//        }

        if (Tools.isNetworkAvailable()) {
            tvInternetStatus.setTextColor(ContextCompat.getColor(this, R.color.colorAccent));
            tvInternetStatus.setText(getString(R.string.internet_on));
            setMainStats();
            setEMAAndRewardsStats();
        }

        int rewardPoints = rewardPrefs.getInt("rewardPoints", 0);
        int bonus = rewardPrefs.getInt("bonus", 0);
        int total_reward = rewardPoints + bonus;

        tvRewards.setText(getString(R.string.earned_points, rewardPoints));
        tvBonus.setText(getString(R.string.bonus_points, bonus));
        tvTotalReward.setText(getString(R.string.total_reward_with_bonus, total_reward));

        (new Handler()).postDelayed(() -> {
            tvServiceStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.colorAccent));
            tvServiceStatus.setText(getString(R.string.service_running));

            //region get EMA ticks

            boolean ema1_answered = rewardPrefs.getBoolean("ema1_answered", false);
            boolean ema2_answered = rewardPrefs.getBoolean("ema2_answered", false);
            boolean ema3_answered = rewardPrefs.getBoolean("ema3_answered", false);
            boolean ema4_answered = rewardPrefs.getBoolean("ema4_answered", false);
            int ema_answered_counter = rewardPrefs.getInt("ema_answered_count", 0);


            if (ema1_answered) {
                ema_tv_1.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
            }
            if (ema2_answered) {
                ema_tv_2.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
            }
            if (ema3_answered) {
                ema_tv_3.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
            }

            if (ema4_answered) {
                ema_tv_4.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
            }

            if (!ema1_answered) {
                ema_tv_1.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
            }
            if (!ema2_answered) {
                ema_tv_2.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
            }
            if (!ema3_answered) {
                ema_tv_3.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
            }

            if (!ema4_answered) {
                ema_tv_4.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
            }

            // tvEmaNum.setText(getString(R.string.ema_responses_rate, ema_answered_counter));
            if (ServiceConnection.serviceConnectionAvailable) {
                tvWatchConnected.setText(getResources().getString(R.string.smartwatch_connected));
            } else {
                tvWatchConnected.setText(getResources().getString(R.string.smartwatch_not_connected));
            }
            tvTotalReward.setText(getString(R.string.total_reward_with_bonus, total_reward));
            //endregion


        }, 500);
    }

    public void updateEMAresponses(List<String> uniqueValues) {

        Calendar fromCal = Calendar.getInstance();
        fromCal.set(Calendar.HOUR_OF_DAY, 0);
        fromCal.set(Calendar.MINUTE, 0);
        fromCal.set(Calendar.SECOND, 0);
        fromCal.set(Calendar.MILLISECOND, 0);

        Calendar tillCal = (Calendar) fromCal.clone();
        tillCal.set(Calendar.HOUR_OF_DAY, 23);
        tillCal.set(Calendar.MINUTE, 59);
        tillCal.set(Calendar.SECOND, 59);

        if (uniqueValues.size() > 0) {

            int emaAnsweredCountFromET = 0;
            int emaAnsweredCountFromSharedPrefs = rewardPrefs.getInt("ema_answered_count", 0);

            SharedPreferences.Editor editor = rewardPrefs.edit();

            for (String val : uniqueValues) {
                if (Tools.inRange(Long.parseLong(val.split(Tools.DATA_SOURCE_SEPARATOR)[0]), fromCal.getTimeInMillis(), tillCal.getTimeInMillis())) {
                    emaAnsweredCountFromET++;
                    switch (Integer.parseInt(val.split(Tools.DATA_SOURCE_SEPARATOR)[1])) {
                        case 1:
                            editor.putBoolean("ema1_answered", true);
                            editor.apply();
                            break;
                        case 2:
                            editor.putBoolean("ema2_answered", true);
                            editor.apply();
                            break;
                        case 3:
                            editor.putBoolean("ema3_answered", true);
                            editor.apply();
                            break;
                        case 4:
                            editor.putBoolean("ema4_answered", true);
                            editor.apply();
                            break;
                        default:
                            break;
                    }
                }
            }

            if (emaAnsweredCountFromET > emaAnsweredCountFromSharedPrefs) {
                editor.putInt("ema_answered_count", emaAnsweredCountFromET);
                editor.apply();
            }

            if (emaAnsweredCountFromSharedPrefs == 0 && emaAnsweredCountFromET == 0) {
                editor.putBoolean("ema1_answered", false);
                editor.putBoolean("ema2_answered", false);
                editor.putBoolean("ema3_answered", false);
                editor.putBoolean("ema4_answered", false);
                editor.apply();
            }
        }

    }

    public void emaButtonVisibilityCheck() {
        boolean ema_1_answered = rewardPrefs.getBoolean("ema1_answered", false);
        boolean ema_2_answered = rewardPrefs.getBoolean("ema2_answered", false);
        boolean ema_3_answered = rewardPrefs.getBoolean("ema3_answered", false);
        boolean ema_4_answered = rewardPrefs.getBoolean("ema4_answered", false);

        int ema_order = Tools.getEMAOrderFromRangeAfterEMA(Calendar.getInstance());
        if (ema_order == 0) {
            btnEMA.setVisibility(View.GONE);
        } else if (ema_order == 1) {
            if (ema_1_answered) {
                btnEMA.setVisibility(View.GONE);
            } else {
                btnEMA.setVisibility(View.VISIBLE);
            }
        } else if (ema_order == 2) {
            if (ema_2_answered) {
                btnEMA.setVisibility(View.GONE);
            } else {
                btnEMA.setVisibility(View.VISIBLE);
            }
        } else if (ema_order == 3) {
            if (ema_3_answered) {
                btnEMA.setVisibility(View.GONE);
            } else {
                btnEMA.setVisibility(View.VISIBLE);
            }
        } else if (ema_order == 4) {
            if (ema_4_answered) {
                btnEMA.setVisibility(View.GONE);
            } else {
                btnEMA.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onBackPressed() {

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
            navigationView.setCheckedItem(R.id.nav_home);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_home:
                navigationView.setCheckedItem(R.id.nav_home);
                break;
            case R.id.nav_location:
                finish();
                startActivity(new Intent(MainActivity.this, LocationSetActivity.class));
                navigationView.setCheckedItem(R.id.nav_location);
                break;
            case R.id.nav_sns:
                navigationView.setCheckedItem(R.id.nav_sns);
                SharedPreferences instagramPrefs = getSharedPreferences("InstagramPrefs", Context.MODE_PRIVATE);
                boolean isLoggedIn = instagramPrefs.getBoolean("is_logged_in", false);

                if (isLoggedIn) {
                    startActivity(new Intent(MainActivity.this, InstagramLoggedInActivity.class));
                } else {
                    startActivity(new Intent(MainActivity.this, MediaSetActivity.class));
                }
                break;
            case R.id.nav_photos:
                finish();
                startActivity(new Intent(MainActivity.this, CapturedPhotosActivity.class));
                navigationView.setCheckedItem(R.id.nav_photos);
                break;
            case R.id.nav_smartwatch:
                finish();
                startActivity(new Intent(MainActivity.this, SmartwatchActivity.class));
                navigationView.setCheckedItem(R.id.nav_smartwatch);
                break;
            case R.id.nav_restart:
                customSensorsService = new Intent(this, MainService.class);
                dataSubmissionService = new Intent (this, DataSubmissionService.class);

                //when the function is called by clicking the button
                stopService(customSensorsService);
                stopService(dataSubmissionService);

                if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                    runOnUiThread(() -> dialog = Tools.requestPermissions(MainActivity.this));
                } else {
                    if (configPrefs.getLong("startTimestamp", 0) <= System.currentTimeMillis()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(customSensorsService);
                            startForegroundService(dataSubmissionService);
                        } else {
                            startService(customSensorsService);
                            startService(dataSubmissionService);
                        }
                    }
                }
                break;

            case R.id.nav_logout:
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                alertDialog.setMessage(getString(R.string.log_out_confirmation));
                alertDialog.setPositiveButton(
                        getString(R.string.yes), (dialog, which) -> {
                            Tools.perform_logout(getApplicationContext());
                            stopService(customSensorsService);
                            stopService(dataSubmissionService);
                            finish();
                        });
                alertDialog.setNegativeButton(
                        getString(R.string.cancel), (dialog, which) -> dialog.cancel());
                alertDialog.show();
                break;
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    public void setMainStats() {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(getString(R.string.grpc_host), Integer.parseInt(getString(R.string.grpc_port))).usePlaintext().build();
            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            // region Retrieve main stats
            EtService.RetrieveParticipantStats.Request retrieveParticipantStatsRequestMessage = EtService.RetrieveParticipantStats.Request.newBuilder()
                    .setUserId(loginPrefs.getInt(AuthActivity.user_id, -1))
                    .setEmail(loginPrefs.getString(AuthActivity.usrEmail, null))
                    .setTargetEmail(loginPrefs.getString(AuthActivity.usrEmail, null))
                    .setTargetCampaignId(Integer.parseInt(getString(R.string.campaign_id)))
                    .build();
            try {
                EtService.RetrieveParticipantStats.Response responseMessage = stub.retrieveParticipantStats(retrieveParticipantStatsRequestMessage);
                if (responseMessage.getSuccess()) {
                    final long join_timestamp = responseMessage.getCampaignJoinTimestamp();
                    final long hb_phone = responseMessage.getLastHeartbeatTimestamp();
                    final int samples_amount = responseMessage.getAmountOfSubmittedDataSamples();
                    runOnUiThread(() -> {
                        long nowTime = System.currentTimeMillis();

                        float joinTimeDif = nowTime - join_timestamp;
                        int dayNum = (int) Math.ceil(joinTimeDif / 1000 / 3600 / 24); // in days
                        float hbTimeDif = nowTime - hb_phone;
                        int heart_beat = (int) Math.ceil(hbTimeDif / 1000 / 60); // in minutes

                        if (heart_beat > 30)
                            tvHBPhone.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.red));
                        else
                            tvHBPhone.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.colorAccent));
                        tvDayNum.setText(getString(R.string.day_num, dayNum));
                        // tvDataLoadedPhone.setText(getString(R.string.data_loaded, String.valueOf(samples_amount)));
                        String last_active_text = hb_phone == 0 ? "just now" : Tools.formatMinutes(heart_beat, getApplicationContext()) + " " + getResources().getString(R.string.ago);
                        tvHBPhone.setText(getString(R.string.last_active, last_active_text));
                    });
                }
            } catch (StatusRuntimeException e) {
                Log.e("Tools", "DataCollectorService.retrieveParticipantStats() exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                channel.shutdown();
            }
            //endregion
        }).start();
    }

    public void setEMAAndRewardsStats() {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(getString(R.string.grpc_host), Integer.parseInt(getString(R.string.grpc_port))).usePlaintext().build();
            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);

            Calendar tillCal = Calendar.getInstance();
            tillCal.set(Calendar.HOUR_OF_DAY, 23);
            tillCal.set(Calendar.MINUTE, 59);
            tillCal.set(Calendar.SECOND, 59);
            EtService.RetrieveFilteredDataRecords.Request retrieveFilteredEMARecordsRequest = EtService.RetrieveFilteredDataRecords.Request.newBuilder()
                    .setUserId(loginPrefs.getInt(AuthActivity.user_id, -1))
                    .setEmail(loginPrefs.getString(AuthActivity.usrEmail, null))
                    .setTargetEmail(loginPrefs.getString(AuthActivity.usrEmail, null))
                    .setTargetCampaignId(Integer.parseInt(getString(R.string.campaign_id)))
                    .setTargetDataSourceId(configPrefs.getInt("SURVEY_EMA", -1))
                    .setFromTimestamp(0)
                    .setTillTimestamp(tillCal.getTimeInMillis())
                    .build();
            try {
                final EtService.RetrieveFilteredDataRecords.Response responseMessage = stub.retrieveFilteredDataRecords(retrieveFilteredEMARecordsRequest);
                if (responseMessage.getSuccess()) {
                    runOnUiThread(() -> {

                        if (responseMessage.getValueList() != null) {
                            Calendar fromCal = Calendar.getInstance();
                            fromCal.set(Calendar.HOUR_OF_DAY, 0);
                            fromCal.set(Calendar.MINUTE, 0);
                            fromCal.set(Calendar.SECOND, 0);
                            fromCal.set(Calendar.MILLISECOND, 0);
                            Calendar tillCal1 = (Calendar) fromCal.clone();
                            tillCal1.set(Calendar.HOUR_OF_DAY, 23);
                            tillCal1.set(Calendar.MINUTE, 59);
                            tillCal1.set(Calendar.SECOND, 59);

                            //check for duplicates and get only unique ones
                            // List<String> uniqueValues = new ArrayList<>();
                            for (ByteString item : responseMessage.getValueList()) {
                                String strItem = item.toString(Charsets.UTF_8);
                                if (!uniqueValues.contains(strItem))
                                    uniqueValues.add(strItem);
                            }

                            updateEMAresponses(uniqueValues);

                            int rewardPointsFromSharedPrefs = rewardPrefs.getInt("rewardPoints", 0);
                            int rewardPointsFromET = uniqueValues.size() * 250;

                            int bonus = calculateBonusPoints(uniqueValues);
                            SharedPreferences.Editor editor = rewardPrefs.edit();

                            if (rewardPointsFromSharedPrefs > rewardPointsFromET) {
                                //send more updated reward value to ET
                                String reward_from_sharedPrefs_no_bonus_type = "TOTAL REWARD FROM SHARED PREFS WITHOUT BONUS";
                                long nowTime = System.currentTimeMillis();
                                int dataSourceId = configPrefs.getInt("REWARD_POINTS", -1);
                                if (dataSourceId != -1) {
                                    DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, rewardPointsFromSharedPrefs, reward_from_sharedPrefs_no_bonus_type);
                                }

                            } else if (rewardPointsFromSharedPrefs < rewardPointsFromET) {
                                // save more updated reward to Shared Preferences
                                editor.putInt("rewardPoints", rewardPointsFromET);
                            }

                            editor.putInt("bonus", bonus);
                            editor.apply();


                        }
                    });
                }
            } catch (StatusRuntimeException e) {
                Log.e("Tools", "DataCollectorService.retrieveFilteredDataRecords() exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                channel.shutdown();
            }
        }).start();
    }

    public int calculateBonusPoints(List<String> emaValues) {

        int total_bonus = 0;
        int ema_answered_counter = 1;
        int conseq_counter = 0;
        int prev_day = 0;

        // survey_ema values
        for (String val : emaValues) {

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(Long.parseLong(val.split(Tools.DATA_SOURCE_SEPARATOR)[0]));

            int day = cal.get(Calendar.DAY_OF_MONTH);

            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
                //bonus calculation is restarted on Sundays
                conseq_counter = 0;
            }

            cal.add(Calendar.DAY_OF_YEAR, -1);
            int yesterday = cal.get(Calendar.DAY_OF_MONTH);

            if (day == prev_day) {
                ema_answered_counter++;

                if (ema_answered_counter == 4) {
                    conseq_counter++;
                    if (conseq_counter > 1) {
                        total_bonus += (conseq_counter - 1) * 100;
                    }
                }
            } else if ((day != prev_day && prev_day == yesterday) || (day != prev_day && prev_day == 0)) {

                if (ema_answered_counter < 4) {
                    conseq_counter = 0;
                }
                ema_answered_counter = 1; //day changed, count from the first again
            } else {
                ema_answered_counter = 1;
                conseq_counter = 0; //if one day or more there were no EMAs replies
            }

            prev_day = day;
        }

        //region Submit bonus points to easytrack
        String bonus_type = "BONUS";
        long currentTime = System.currentTimeMillis();
        int rewardDataSourceId = configPrefs.getInt("REWARD_POINTS", -1);
        if (rewardDataSourceId != -1) {
            DbMgr.saveMixedData(rewardDataSourceId, currentTime, 1.0f, currentTime, total_bonus, bonus_type);
        }
        //endregion

        return total_bonus;
    }

    public void lateEMAClick(View view) {
        int ema_order = Tools.getEMAOrderFromRangeAfterEMA(Calendar.getInstance());
        if (ema_order != 0 && ema_order != -1) {
            Intent intent = new Intent(this, EMAActivity.class);
            intent.putExtra("ema_order", ema_order);
            startActivity(intent);
        }
    }

    public void restartService() {
        customSensorsService = new Intent(this, MainService.class);
        dataSubmissionService = new Intent(this, DataSubmissionService.class);
        //when the function is called without clicking the button
        if (!Tools.isMainServiceRunning(getApplicationContext())) {
            customSensorsService = new Intent(this, MainService.class);
            stopService(customSensorsService);
            if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                runOnUiThread(() -> dialog = Tools.requestPermissions(MainActivity.this));
            } else {
                if (configPrefs.getLong("startTimestamp", 0) <= System.currentTimeMillis()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(customSensorsService);
                    } else {
                        startService(customSensorsService);
                    }
                }
            }
        }

        if (!Tools.isDataSubmissionServiceRunning(getApplicationContext())) {
            dataSubmissionService = new Intent(this, DataSubmissionService.class);
            stopService(dataSubmissionService);
            if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                runOnUiThread(() -> dialog = Tools.requestPermissions(MainActivity.this));
            } else {
                if (configPrefs.getLong("startTimestamp", 0) <= System.currentTimeMillis()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(dataSubmissionService);
                    } else {
                        startService(dataSubmissionService);
                    }
                }
            }
        }

    }

    public void setEmaResetAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        AlarmManager alarmManagerMorning = (AlarmManager) getSystemService(ALARM_SERVICE);

        Intent intent_reset = new Intent(MainActivity.this, EMAAlarmRcvr.class);
        intent_reset.putExtra("ema_reset", true); //time to reset EMA to 0

        Intent intent_reset_morning = new Intent(MainActivity.this, EMAAlarmRcvr.class);
        intent_reset.putExtra("ema_reset_morning", true); //time to reset EMA to 0

        Intent intent_remove = new Intent(MainActivity.this, EMAOverlayShowingService.class);
        intent_remove.setAction("EMA_POP_UP_REMOVE");
        intent_remove.putExtra("ema_pop_up_remove", true); // time to remove EMA pop up


        PendingIntent pendingIntentReset = PendingIntent.getBroadcast(MainActivity.this, 10, intent_reset, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntentResetMorning = PendingIntent.getBroadcast(MainActivity.this, 8, intent_reset_morning, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntentRemove = PendingIntent.getBroadcast(MainActivity.this, 11, intent_remove, PendingIntent.FLAG_UPDATE_CURRENT);

        if (alarmManager == null)
            return;

        if(alarmManagerMorning == null){
            return;
        }

        Calendar firingCalReset = Calendar.getInstance();
        firingCalReset.set(Calendar.HOUR_OF_DAY, 23); // at 11:59pm
        firingCalReset.set(Calendar.MINUTE, 59); // Particular minute
        firingCalReset.set(Calendar.SECOND, 0); // particular second
        firingCalReset.set(Calendar.MILLISECOND, 0); // particular second

        Calendar firingCalResetMorning = Calendar.getInstance();
        firingCalResetMorning.set(Calendar.HOUR_OF_DAY, 9); // at 9:30am
        firingCalResetMorning.set(Calendar.MINUTE, 30); // Particular minute
        firingCalResetMorning.set(Calendar.SECOND, 0); // particular second
        firingCalResetMorning.set(Calendar.MILLISECOND, 0); // particular second

        //remove EMA pop up
        Calendar firingCalRemove11 = Calendar.getInstance();
        firingCalRemove11.set(Calendar.HOUR_OF_DAY, 11); // at 11:01am
        firingCalRemove11.set(Calendar.MINUTE, 1); // Particular minute
        firingCalRemove11.set(Calendar.SECOND, 0); // particular second
        firingCalRemove11.set(Calendar.MILLISECOND, 0); // particular second

        Calendar firingCalRemove15 = Calendar.getInstance();
        firingCalRemove15.set(Calendar.HOUR_OF_DAY, 15); // at 3:01pm
        firingCalRemove15.set(Calendar.MINUTE, 1); // Particular minute
        firingCalRemove15.set(Calendar.SECOND, 0); // particular second
        firingCalRemove15.set(Calendar.MILLISECOND, 0); // particular second

        Calendar firingCalRemove19 = Calendar.getInstance();
        firingCalRemove19.set(Calendar.HOUR_OF_DAY, 19); // at 7:01pm
        firingCalRemove19.set(Calendar.MINUTE, 1); // Particular minute
        firingCalRemove19.set(Calendar.SECOND, 0); // particular second
        firingCalRemove19.set(Calendar.MILLISECOND, 0); // particular second

        Calendar firingCalRemove23 = Calendar.getInstance();
        firingCalRemove23.set(Calendar.HOUR_OF_DAY, 23); // at 11:01pm
        firingCalRemove23.set(Calendar.MINUTE, 1); // Particular minute
        firingCalRemove23.set(Calendar.SECOND, 0); // particular second
        firingCalRemove23.set(Calendar.MILLISECOND, 0); // particular second

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, firingCalReset.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntentReset); //repeat every day
        alarmManagerMorning.setRepeating(AlarmManager.RTC_WAKEUP, firingCalResetMorning.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntentResetMorning); //repeat every day
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, firingCalRemove11.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntentRemove); //repeat every day
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, firingCalRemove15.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntentRemove); //repeat every day
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, firingCalRemove19.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntentRemove); //repeat every day
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, firingCalRemove23.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntentRemove); //repeat every day
    }


    private void loadCampaign() {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(getString(R.string.grpc_host), Integer.parseInt(getString(R.string.grpc_port))).usePlaintext().build();
            Log.e(TAG, "loadCampaign: Channel built");
            try {
                Log.e(TAG, "loadCampaign: Trying to load");
                ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                EtService.RetrieveCampaign.Request retrieveCampaignRequest = EtService.RetrieveCampaign.Request.newBuilder()
                        .setUserId(loginPrefs.getInt(AuthActivity.user_id, -1))
                        .setEmail(loginPrefs.getString(AuthActivity.usrEmail, null))
                        .setCampaignId(Integer.parseInt(getString(R.string.campaign_id)))
                        .build();

                EtService.RetrieveCampaign.Response retrieveCampaignResponse = stub.retrieveCampaign(retrieveCampaignRequest);
                if (retrieveCampaignResponse.getSuccess()) {
                    setUpCampaignConfigurations(
                            retrieveCampaignResponse.getName(),
                            retrieveCampaignResponse.getNotes(),
                            retrieveCampaignResponse.getCreatorEmail(),
                            retrieveCampaignResponse.getConfigJson(),
                            retrieveCampaignResponse.getStartTimestamp(),
                            retrieveCampaignResponse.getEndTimestamp(),
                            retrieveCampaignResponse.getParticipantCount()
                    );

                    Log.e(TAG, "loadCampaign: Campaign retrieved");
                    SharedPreferences.Editor editor = configPrefs.edit();
                    editor.putString("name", retrieveCampaignResponse.getName());
                    editor.putString("notes", retrieveCampaignResponse.getNotes());
                    editor.putString("creatorEmail", retrieveCampaignResponse.getCreatorEmail());
                    editor.putString("configJson", retrieveCampaignResponse.getConfigJson());
                    editor.putLong("startTimestamp", retrieveCampaignResponse.getStartTimestamp());
                    editor.putLong("endTimestamp", retrieveCampaignResponse.getEndTimestamp());
                    editor.putInt("participantCount", retrieveCampaignResponse.getParticipantCount());
                    editor.putBoolean("campaignLoaded", true);
                    editor.apply();
                    restartService();
                }
            } catch (StatusRuntimeException | JSONException e) {
                e.printStackTrace();
            } finally {
                channel.shutdown();
            }
        }).start();
    }

    private void setUpCampaignConfigurations(String name, String notes, String creatorEmail, String configJson, long startTimestamp, long endTimestamp, int participantCount) throws JSONException {
        String oldConfigJson = configPrefs.getString(String.format(Locale.getDefault(), "%s_configJson", name), null);
        if (configJson.equals(oldConfigJson))
            return;

        SharedPreferences.Editor editor = configPrefs.edit();
        editor.putString(String.format(Locale.getDefault(), "%s_configJson", name), configJson);

        JSONArray dataSourceConfigurations = new JSONArray(configJson);
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < dataSourceConfigurations.length(); n++) {
            JSONObject dataSourceConfig = dataSourceConfigurations.getJSONObject(n);
            String _name = dataSourceConfig.getString("name");
            int _dataSourceId = dataSourceConfig.getInt("data_source_id");
            editor.putInt(_name, _dataSourceId);
            String _json = dataSourceConfig.getString("config_json");
            editor.putString(String.format(Locale.getDefault(), "config_json_%s", _name), _json);
            sb.append(_name).append(',');
        }
        if (sb.length() > 0)
            sb.replace(sb.length() - 1, sb.length(), "");
        editor.putString("dataSourceNames", sb.toString());
        editor.apply();
    }

//    public void showAboutPermissionsPopUp(){
//        permissionsPopUp.setContentView(R.layout.popup_about_permissions);
//        Button okBtn = permissionsPopUp.findViewById(R.id.okBtnId);
//        okBtn.setOnClickListener(v -> permissionsPopUp.dismiss());
//
//        Objects.requireNonNull(permissionsPopUp.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
//        permissionsPopUp.setCanceledOnTouchOutside(false);
//        permissionsPopUp.show();
//    }

    public void showFirstLaunchActivity() {
        startActivity(new Intent(MainActivity.this, FirstLaunchActivity.class));
        SharedPreferences.Editor editor = loginPrefs.edit();
        editor.putBoolean("firstLaunch", false);
        editor.apply();
    }

    @Override
    protected void onStop() {
        super.onStop();
        loadingPanel.setVisibility(View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loadingPanel.setVisibility(View.GONE);
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

}
