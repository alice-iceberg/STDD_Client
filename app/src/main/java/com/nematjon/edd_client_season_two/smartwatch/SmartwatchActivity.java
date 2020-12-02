package com.nematjon.edd_client_season_two.smartwatch;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.nematjon.edd_client_season_two.CapturedPhotosActivity;
import com.nematjon.edd_client_season_two.DbMgr;
import com.nematjon.edd_client_season_two.InstagramLoggedInActivity;
import com.nematjon.edd_client_season_two.LocationSetActivity;
import com.nematjon.edd_client_season_two.MainActivity;
import com.nematjon.edd_client_season_two.MediaSetActivity;
import com.nematjon.edd_client_season_two.R;
import com.nematjon.edd_client_season_two.Tools;
import com.nematjon.edd_client_season_two.services.MainService;
import com.samsung.android.sdk.accessory.SAAgentV2;


public class SmartwatchActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    static final String TAG = "SAPAndroidAgent";

    private SAPAndroidAgent sapAndroidAgent;
    private boolean connectedToWatch = false;
    private TextView logTextView;
    private ScrollView logScrollView;
    private TextView btConnectionTextView;
    private TextView sampleCountTextView;
    private TextView uploadStatusTextView;
    private boolean smartwatchActivityRunning = false;
    private Thread observerThread;
    private boolean runThreads = true;

    NavigationView navigationView;
    DrawerLayout drawerLayout;
    Toolbar toolbar;

    private static Intent customSensorsService;
    AlertDialog dialog;
    SharedPreferences configPrefs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smartwatch);
        configPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        smartwatchActivityRunning = true;

        logTextView = findViewById(R.id.logTextView);
        logScrollView = findViewById(R.id.logScrollView);
        btConnectionTextView = findViewById(R.id.btConnectionTextView);
        sampleCountTextView = findViewById(R.id.sampleCountTextView);
        uploadStatusTextView = findViewById(R.id.uploadStatusTextView);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        navigationView.bringToFront();
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_smartwatch);

        ServiceConnection.setOnReceiveListener(sampleCount -> {
        });
        logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));

        startSAPAgent();


        runThreads = true;
        observerThread = new Thread(() -> {
            while (runThreads) {
                runOnUiThread(() -> {
                    if (ServiceConnection.serviceConnectionAvailable) {
                        btConnectionTextView.setText(R.string.ON);
                        btConnectionTextView.setTextColor(Color.GREEN);
                    } else {
                        btConnectionTextView.setText(R.string.OFF);
                        btConnectionTextView.setTextColor(Color.RED);
                    }

                    int count;
                    try {
                        count = DbMgr.countSamples();
                    } catch (Exception e) {
                        count = 0;
                    }
                    sampleCountTextView.setText(String.valueOf(count));

                    if (MainService.uploadingSuccessfully) {
                        uploadStatusTextView.setText(R.string.ON);
                        uploadStatusTextView.setTextColor(Color.GREEN);
                    } else {
                        uploadStatusTextView.setText(R.string.OFF);
                        uploadStatusTextView.setTextColor(Color.RED);
                    }
                });
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        observerThread.start();
    }

    private void startSAPAgent() {
        SAAgentV2.requestAgent(getApplicationContext(), SAPAndroidAgent.class.getName(), new SAAgentV2.RequestAgentCallback() {
            @Override
            public void onAgentAvailable(SAAgentV2 agent) {
                if (agent != null) {
                    sapAndroidAgent = (SAPAndroidAgent) agent;
                    logTextView.append(getResources().getString(R.string.ready_to_accept_connection) + "\n");
                    sapAndroidAgent.setConnectionListener((boolean connectedToSmartwatch) -> {
                        SmartwatchActivity.this.connectedToWatch = connectedToSmartwatch;
                        if (connectedToWatch)
                            try {
                                logTextView.append(getResources().getString(R.string.device_connected) + "\n");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                    });
                } else {
                    logTextView.append(getResources().getString(R.string.agent_unavailable) + "\n");
                }
            }

            @Override
            public void onError(int errorCode, String message) {
                logTextView.append(getResources().getString(R.string.failed_to_get_agent) + "\n");
                Log.e(TAG, "Failed to get agent : code(" + errorCode + "), message(" + message + ")");
            }
        });
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
                finish();
                startActivity(new Intent(SmartwatchActivity.this, MainActivity.class));
                navigationView.setCheckedItem(R.id.nav_home);
                break;
            case R.id.nav_location:
                finish();
                startActivity(new Intent(SmartwatchActivity.this, LocationSetActivity.class));
                navigationView.setCheckedItem(R.id.nav_location);
                break;
            case R.id.nav_sns:
                finish();
                navigationView.setCheckedItem(R.id.nav_sns);
                SharedPreferences instagramPrefs = getSharedPreferences("InstagramPrefs", Context.MODE_PRIVATE);
                boolean isLoggedIn = instagramPrefs.getBoolean("is_logged_in", false);

                if (isLoggedIn) {
                    startActivity(new Intent(SmartwatchActivity.this, InstagramLoggedInActivity.class));
                } else {
                    startActivity(new Intent(SmartwatchActivity.this, MediaSetActivity.class));
                }
                break;
            case R.id.nav_photos:
                finish();
                startActivity(new Intent(SmartwatchActivity.this, CapturedPhotosActivity.class));
                navigationView.setCheckedItem(R.id.nav_photos);
                break;
            case R.id.nav_smartwatch:
                break;
            case R.id.nav_restart:
                navigationView.setCheckedItem(R.id.nav_restart);
                customSensorsService = new Intent(this, MainService.class);
                finish();

                //when the function is called by clicking the button
                stopService(customSensorsService);
                if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog = Tools.requestPermissions(SmartwatchActivity.this);
                        }
                    });
                } else {
                    if (configPrefs.getLong("startTimestamp", 0) <= System.currentTimeMillis()) {
                        Log.e(TAG, "RESTART SERVICE");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startActivity(new Intent(SmartwatchActivity.this, MainActivity.class));
                            startForegroundService(customSensorsService);
                        } else {
                            startActivity(new Intent(SmartwatchActivity.this, MainActivity.class));
                            startService(customSensorsService);
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
                            stopService(SmartwatchActivity.customSensorsService);
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
}
