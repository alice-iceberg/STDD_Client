package com.nematjon.edd_client_season_two;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.nematjon.edd_client_season_two.services.MainService;
import com.samsung.android.sdk.accessory.SAAgentV2;

import java.util.Calendar;

import inha.nsl.easytrack.ETServiceGrpc;
import inha.nsl.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;


public class SmartwatchActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    static final String TAG = "SAPAndroidAgent";
    static final int RC_OPEN_ET_AUTHENTICATOR = 100;
    static final int RC_OPEN_APP_STORE = 101;

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

        if (authAppIsNotInstalled()) {
            Toast.makeText(this, "Please install this app and reopen the application!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=inha.nsl.easytrack"));
            intent.setPackage("com.android.vending");
            startActivityForResult(intent, RC_OPEN_APP_STORE);
            finish();
        } else {
            SharedPreferences prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);
            if (prefs.getInt("userId", -1) == -1 || prefs.getString("email", null) == null) {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("inha.nsl.easytrack");
                if (launchIntent != null) {
                    launchIntent.setFlags(0);
                    startActivityForResult(launchIntent, RC_OPEN_ET_AUTHENTICATOR);
                }
            } else {
                startSAPAgent();
                startDataCollectionService();
            }
        }

        runThreads = true;
        observerThread = new Thread(() -> {
            while (runThreads) {
                runOnUiThread(() -> {
                    if (ServiceConnection.serviceConnectionAvailable) {
                        btConnectionTextView.setText("ON");
                        btConnectionTextView.setTextColor(Color.GREEN);
                    } else {
                        btConnectionTextView.setText("OFF");
                        btConnectionTextView.setTextColor(Color.RED);
                    }

                    int count;
                    try {
                        count = DbMgr.countSamples();
                    } catch (Exception e) {
                        count = 0;
                    }
                    sampleCountTextView.setText(String.valueOf(count));

                    if (DataCollectorService.uploadingSuccessfully) {
                        uploadStatusTextView.setText("ON");
                        uploadStatusTextView.setTextColor(Color.GREEN);
                    } else {
                        uploadStatusTextView.setText("OFF");
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

    @Override
    protected void onDestroy() {
        if (connectedToWatch) {
            sapAndroidAgent.releaseAgent();
            connectedToWatch = false;
            smartwatchActivityRunning = false;
        }
        runThreads = false;
        try {
            observerThread.interrupt();
            observerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == RC_OPEN_ET_AUTHENTICATOR) {
            if (resultCode == RESULT_OK && data != null) {
                String fullName = data.getStringExtra("fullName");
                String email = data.getStringExtra("email");
                assert email != null;
                int userId = data.getIntExtra("userId", -1);
                new Thread(() -> {
                    ManagedChannel channel = ManagedChannelBuilder.forAddress(getString(R.string.grpc_host), Integer.parseInt(getString(R.string.grpc_port))).usePlaintext().build();
                    ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                    EtService.BindUserToCampaign.Request requestMessage = EtService.BindUserToCampaign.Request.newBuilder()
                            .setUserId(userId)
                            .setEmail(email)
                            .setCampaignId(Integer.parseInt(getString(R.string.campaign_id)))
                            .build();
                    try {
                        final EtService.BindUserToCampaign.Response responseMessage = stub.bindUserToCampaign(requestMessage);
                        if (responseMessage.getSuccess()) runOnUiThread(() -> {
                            SharedPreferences prefs = getSharedPreferences("SmartwatchPrefs", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("fullName", fullName);
                            editor.putString("email", email);
                            editor.putInt("userId", userId);
                            editor.apply();
                            Toast.makeText(getApplicationContext(), "Successfully authorized and connected to the stress sensing campaign!", Toast.LENGTH_SHORT).show();
                            startSAPAgent();
                            startDataCollectionService();
                        });
                        else runOnUiThread(() -> {
                            Calendar cal = Calendar.getInstance();
                            cal.setTimeInMillis(responseMessage.getCampaignStartTimestamp());
                            Toast.makeText(getApplicationContext(), "Campaign hasn't started yet (start time : ${SimpleDateFormat.getDateTimeInstance().format(cal.time)}", Toast.LENGTH_SHORT).show();
                        });
                    } catch (StatusRuntimeException e) {
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Error occurred when joining to the campaign, please try again later!", Toast.LENGTH_SHORT).show());
                        e.printStackTrace();
                    } finally {
                        channel.shutdown();
                    }
                }).start();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    private boolean authAppIsNotInstalled() {
        try {
            getPackageManager().getPackageInfo("inha.nsl.easytrack", 0);
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private void startSAPAgent() {
        SAAgentV2.requestAgent(getApplicationContext(), SAPAndroidAgent.class.getName(), new SAAgentV2.RequestAgentCallback() {
            @Override
            public void onAgentAvailable(SAAgentV2 agent) {
                if (agent != null) {
                    sapAndroidAgent = (SAPAndroidAgent) agent;
                    logTextView.append("Ready to accept connection =)\n");
                    sapAndroidAgent.setConnectionListener((boolean connectedToSmartwatch) -> {
                        SmartwatchActivity.this.connectedToWatch = connectedToSmartwatch;
                        if (connectedToWatch)
                            try {
                                logTextView.append("Device connected =)\n");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                    });
                } else {
                    logTextView.append("Agent unavailable =(\n");
                }
            }

            @Override
            public void onError(int errorCode, String message) {
                logTextView.append("Failed to get agent =(");
                Log.e(TAG, "Failed to get agent : code(" + errorCode + "), message(" + message + ")");
            }
        });
    }

    private void startDataCollectionService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(getApplicationContext(), DataCollectorService.class));
        } else {
            startService(new Intent(getApplicationContext(), DataCollectorService.class));
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
                    Log.e(TAG, "restartServiceClick: 3");
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
