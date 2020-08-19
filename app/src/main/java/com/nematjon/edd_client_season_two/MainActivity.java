package com.nematjon.edd_client_season_two;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Handler;
import android.widget.Toolbar;

import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nematjon.edd_client_season_two.receivers.EMAAlarmRcvr;
import com.nematjon.edd_client_season_two.services.MainService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import inha.nsl.easytrack.ETServiceGrpc;
import inha.nsl.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import static com.nematjon.edd_client_season_two.EMAActivity.EMA_NOTIF_HOURS;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    //region UI variables
    private Button btnEMA;
    private TextView tvServiceStatus;
    private TextView tvInternetStatus;
    public TextView tvFileCount;
    public TextView tvDayNum;
    public TextView tvEmaNum;
    public TextView tvHBPhone;
    public TextView tvDataLoadedPhone;
    private RelativeLayout loadingPanel;
    private TextView ema_tv_1;
    private TextView ema_tv_2;
    private TextView ema_tv_3;
    private TextView ema_tv_4;
    private TextView tvRewards;
    private TextView tvBonus;
    private TextView tvTotalReward;
    //endregion

    private Intent customSensorsService;

    private SharedPreferences loginPrefs;
    private SharedPreferences configPrefs;
    private SharedPreferences rewardPrefs;

    private AlertDialog dialog;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setActionBar((Toolbar) findViewById(R.id.my_toolbar));

        init();

        final SwipeRefreshLayout pullToRefresh = findViewById(R.id.pullToRefresh);
        pullToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                initUI();
                updateUI();

                Tools.sendHeartbeat(getApplicationContext());
                pullToRefresh.setRefreshing(false);
            }
        });

    }

    public void init() {
        //region Init UI variables
        btnEMA = findViewById(R.id.btn_late_ema);
        tvServiceStatus = findViewById(R.id.tvStatus);
        tvInternetStatus = findViewById(R.id.connectivityStatus);
        tvFileCount = findViewById(R.id.filesCountTextView);
        loadingPanel = findViewById(R.id.loadingPanel);
        tvDayNum = findViewById(R.id.txt_day_num);
        tvEmaNum = findViewById(R.id.ema_responses_phone);
        tvHBPhone = findViewById(R.id.heartbeat_phone);
        tvDataLoadedPhone = findViewById(R.id.data_loaded_phone);
        ema_tv_1 = findViewById(R.id.ema_tv_1);
        ema_tv_2 = findViewById(R.id.ema_tv_2);
        ema_tv_3 = findViewById(R.id.ema_tv_3);
        ema_tv_4 = findViewById(R.id.ema_tv_4);
        tvRewards = findViewById(R.id.reward_points);
        tvBonus = findViewById(R.id.bonus_points);
        tvTotalReward = findViewById(R.id.total_reward_with_bonus);
        //endregion

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
                    Manifest.permission.CAMERA,
            };
        }

        DbMgr.init(getApplicationContext());

        AppUseDb.init(getApplicationContext());
        loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
        configPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        rewardPrefs = getSharedPreferences("Rewards", Context.MODE_PRIVATE);
        setAlarams();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
            dialog = Tools.requestPermissions(MainActivity.this);
        }

        Tools.sendHeartbeat(getApplicationContext());

        customSensorsService = new Intent(this, MainService.class);

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
                restartService(null);
            } catch (JSONException e) {
                e.printStackTrace();
                finish();
                return;
            }
        } else {
            Toast.makeText(this, "Please connect to the Internet for the first launch!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initUI();
        updateUI();
    }

    public void initUI() {
        tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.red));
        tvServiceStatus.setText(getString(R.string.service_stopped));
        tvInternetStatus.setTextColor(ContextCompat.getColor(this, R.color.red));
        tvInternetStatus.setText(getString(R.string.internet_off));

        tvDayNum.setText("Duration:");
        btnEMA.setVisibility(View.GONE);
        tvHBPhone.setText("Last active:");
        tvEmaNum.setText("EMA responses:");
        tvDataLoadedPhone.setText("Data loaded:");

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
    }

    public void updateUI() {

        int ema_order = Tools.getEMAOrderFromRangeAfterEMA(Calendar.getInstance());
        if (ema_order == 0) {
            btnEMA.setVisibility(View.GONE);
        } else {
            boolean ema_btn_make_visible = loginPrefs.getBoolean("ema_btn_make_visible", true);
            if (!ema_btn_make_visible) {
                btnEMA.setVisibility(View.GONE);
            } else {
                btnEMA.setVisibility(View.VISIBLE);
            }
        }

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

        (new Handler()).postDelayed(new Runnable() {
            @Override
            public void run() {
                tvServiceStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.colorAccent));
                tvServiceStatus.setText(getString(R.string.service_runnig));

                // region get EMA ticks when offline
                if (!Tools.isNetworkAvailable()) {
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
                    tvEmaNum.setText(getString(R.string.ema_responses_rate, ema_answered_counter));
                }

                // endregion
            }
        }, 500);
    }

    public void setMainStats() {
        new Thread(new Runnable() {
            @Override
            public void run() {
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
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
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
                                tvDataLoadedPhone.setText(getString(R.string.data_loaded, String.valueOf(samples_amount)));
                                String last_active_text = hb_phone == 0 ? "just now" : Tools.formatMinutes(heart_beat) + " ago";
                                tvHBPhone.setText(getString(R.string.last_active, last_active_text));
                            }
                        });
                    }
                } catch (StatusRuntimeException e) {
                    Log.e("Tools", "DataCollectorService.setUpHeartbeatSubmissionThread() exception: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    channel.shutdown();
                }
                //endregion
            }
        }).start();
    }

    public void setEMAAndRewardsStats() {
        new Thread(new Runnable() {
            @Override
            public void run() {
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
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ema_tv_1.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
                                ema_tv_2.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
                                ema_tv_3.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
                                ema_tv_4.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.unchecked_box, 0, 0);
                                if (responseMessage.getValueList() != null) {
                                    Calendar fromCal = Calendar.getInstance();
                                    fromCal.set(Calendar.HOUR_OF_DAY, 0);
                                    fromCal.set(Calendar.MINUTE, 0);
                                    fromCal.set(Calendar.SECOND, 0);
                                    fromCal.set(Calendar.MILLISECOND, 0);
                                    Calendar tillCal = (Calendar) fromCal.clone();
                                    tillCal.set(Calendar.HOUR_OF_DAY, 23);
                                    tillCal.set(Calendar.MINUTE, 59);
                                    tillCal.set(Calendar.SECOND, 59);

                                    //check for duplicates and get only unique ones
                                    List<String> uniqueValues = new ArrayList<>();
                                    for (String item : responseMessage.getValueList())
                                        if (!uniqueValues.contains(item))
                                            uniqueValues.add(item);

                                    int rewardPoints = uniqueValues.size() * 250;
                                    int bonus = calculateBonusPoints(uniqueValues);

                                    // saving results to Shared Preferences
                                    SharedPreferences.Editor editor = rewardPrefs.edit();
                                    editor.putInt("rewardPoints", rewardPoints);
                                    editor.putInt("bonus", bonus);
                                    editor.putBoolean("ema1_answered", false);
                                    editor.putBoolean("ema2_answered", false);
                                    editor.putBoolean("ema3_answered", false);
                                    editor.putBoolean("ema4_answered", false);
                                    editor.apply();

                                    int ema_answered_count = 0;

                                    for (String val : uniqueValues) {
                                        if (Tools.inRange(Long.parseLong(val.split(Tools.DATA_SOURCE_SEPARATOR)[0]), fromCal.getTimeInMillis(), tillCal.getTimeInMillis())) {
                                            ema_answered_count++;
                                            switch (Integer.parseInt(val.split(Tools.DATA_SOURCE_SEPARATOR)[1])) {
                                                case 1:
                                                    ema_tv_1.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
                                                    editor.putBoolean("ema1_answered", true);
                                                    editor.apply();
                                                    break;
                                                case 2:
                                                    ema_tv_2.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
                                                    editor.putBoolean("ema2_answered", true);
                                                    editor.apply();
                                                    break;
                                                case 3:
                                                    ema_tv_3.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
                                                    editor.putBoolean("ema3_answered", true);
                                                    editor.apply();
                                                    break;
                                                case 4:
                                                    ema_tv_4.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.checked_box, 0, 0);
                                                    editor.putBoolean("ema4_answered", true);
                                                    editor.apply();
                                                    break;
                                                default:
                                                    break;
                                            }
                                        }
                                    }

                                    editor.putInt("ema_answered_count", ema_answered_count);
                                    editor.apply();
                                    tvEmaNum.setText(getString(R.string.ema_responses_rate, ema_answered_count));

                                    if (ema_answered_count == 0) {
                                        editor.putBoolean("ema1_answered", false);
                                        editor.putBoolean("ema2_answered", false);
                                        editor.putBoolean("ema3_answered", false);
                                        editor.putBoolean("ema4_answered", false);
                                        editor.apply();
                                    }
                                }
                            }
                        });
                    }
                } catch (StatusRuntimeException e) {
                    Log.e("Tools", "DataCollectorService.setUpHeartbeatSubmissionThread() exception: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    channel.shutdown();
                }
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
        return total_bonus;
    }

    public void lateEMAClick(View view) {
        int ema_order = Tools.getEMAOrderFromRangeAfterEMA(Calendar.getInstance());
        if (ema_order != 0) {
            Intent intent = new Intent(this, EMAActivity.class);
            intent.putExtra("ema_order", ema_order);
            startActivity(intent);
        }
    }

    public void restartService(MenuItem item) {
        customSensorsService = new Intent(this, MainService.class);

        if (item != null) {
            //when the function is called by clicking the button
            stopService(customSensorsService);
            if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // dialog = Tools.requestPermissions(MainActivity.this); // todo come back
                    }
                });
            } else {
                Log.e(TAG, "restartServiceClick: 3");
                if (configPrefs.getLong("startTimestamp", 0) <= System.currentTimeMillis()) {
                    Log.e(TAG, "RESTART SERVICE");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(customSensorsService);
                    } else {
                        startService(customSensorsService);
                    }
                }
            }
        } else {
            //when the function is called without clicking the button
            if (!Tools.isMainServiceRunning(getApplicationContext())) {
                customSensorsService = new Intent(this, MainService.class);
                stopService(customSensorsService);
                if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // dialog = Tools.requestPermissions(MainActivity.this); // todo come back
                        }
                    });
                } else {
                    if (configPrefs.getLong("startTimestamp", 0) <= System.currentTimeMillis()) {
                        Log.e(TAG, "RESTART SERVICE");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(customSensorsService);
                        } else {
                            startService(customSensorsService);
                        }
                    }
                }
            }
        }
    }

    public void setLocationsClick(MenuItem item) {
        startActivity(new Intent(MainActivity.this, LocationSetActivity.class));
    }

    public void setSocialMediaClick(MenuItem item) {
        SharedPreferences instagramPrefs = getSharedPreferences("InstagramPrefs", Context.MODE_PRIVATE);
        Boolean isLoggedIn = instagramPrefs.getBoolean("is_logged_in", false);

        if (isLoggedIn) {
            startActivity(new Intent(MainActivity.this, InstagramLoggedInActivity.class));
        } else {
            startActivity(new Intent(MainActivity.this, MediaSetActivity.class));
        }
    }

    public void setAlarams() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        Intent intent1 = new Intent(MainActivity.this, EMAAlarmRcvr.class);
        intent1.putExtra("ema_order", 1);
        Intent intent2 = new Intent(MainActivity.this, EMAAlarmRcvr.class);
        intent2.putExtra("ema_order", 2);
        Intent intent3 = new Intent(MainActivity.this, EMAAlarmRcvr.class);
        intent3.putExtra("ema_order", 3);
        Intent intent4 = new Intent(MainActivity.this, EMAAlarmRcvr.class);
        intent4.putExtra("ema_order", 4);

        PendingIntent pendingIntent1 = PendingIntent.getBroadcast(MainActivity.this, 1, intent1, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntent2 = PendingIntent.getBroadcast(MainActivity.this, 2, intent2, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntent3 = PendingIntent.getBroadcast(MainActivity.this, 3, intent3, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent pendingIntent4 = PendingIntent.getBroadcast(MainActivity.this, 4, intent4, PendingIntent.FLAG_UPDATE_CURRENT);
        if (alarmManager == null)
            return;

        Calendar currentCal = Calendar.getInstance();
        long currentTime = currentCal.getTimeInMillis();

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

        if (firingCal1.getTimeInMillis() > currentTime)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, firingCal1.getTimeInMillis(), 30000, pendingIntent1); //set from today
        else if (firingCal2.getTimeInMillis() > currentTime)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, firingCal2.getTimeInMillis(), 30000, pendingIntent2); //set from today
        else if (firingCal3.getTimeInMillis() > currentTime)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, firingCal3.getTimeInMillis(), 30000, pendingIntent3); //set from today
        else if (firingCal4.getTimeInMillis() > currentTime)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, firingCal4.getTimeInMillis(), 30000, pendingIntent4); //set from today
        else if (currentTime > firingCal4.getTimeInMillis()) {
            firingCal1.add(Calendar.DAY_OF_MONTH, 1);
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, firingCal1.getTimeInMillis(), 30000, pendingIntent1); //set from today
        }
    }

    public void logoutClick(MenuItem item) {

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setMessage(getString(R.string.log_out_confirmation));
        alertDialog.setPositiveButton(
                getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Tools.perform_logout(getApplicationContext());
                        stopService(customSensorsService);
                        finish();
                    }
                });
        alertDialog.setNegativeButton(
                getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        alertDialog.show();
    }

    private void loadCampaign() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ManagedChannel channel = ManagedChannelBuilder.forAddress(getString(R.string.grpc_host), Integer.parseInt(getString(R.string.grpc_port))).usePlaintext().build();
                try {
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
                        restartService(null);
                    }
                } catch (StatusRuntimeException | JSONException e) {
                    e.printStackTrace();
                } finally {
                    channel.shutdown();
                }
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
