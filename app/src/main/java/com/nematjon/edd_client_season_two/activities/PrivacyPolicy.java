package com.nematjon.edd_client_season_two.activities;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.nematjon.edd_client_season_two.CapturedPhotosActivity;
import com.nematjon.edd_client_season_two.DisclosureDialog;
import com.nematjon.edd_client_season_two.InstagramLoggedInActivity;
import com.nematjon.edd_client_season_two.LocationSetActivity;
import com.nematjon.edd_client_season_two.MainActivity;
import com.nematjon.edd_client_season_two.MediaSetActivity;
import com.nematjon.edd_client_season_two.R;
import com.nematjon.edd_client_season_two.Tools;
import com.nematjon.edd_client_season_two.services.DataSubmissionService;
import com.nematjon.edd_client_season_two.services.MainService;
import com.nematjon.edd_client_season_two.smartwatch.SmartwatchActivity;

public class PrivacyPolicy extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private NavigationView navigationView;
    private DrawerLayout drawerLayout;
    private Intent customSensorsService;
    private Intent dataSubmissionService;
    private AlertDialog dialog;
    private SharedPreferences configPrefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);
        Toolbar toolbar = findViewById(R.id.toolbar1);
        navigationView = findViewById(R.id.nav_view1);
        drawerLayout = findViewById(R.id.drawer_layout1);

        WebView myWebView = findViewById(R.id.webview);
        myWebView.loadUrl(Tools.privacyPolicyUrl);

        setSupportActionBar(toolbar);
        navigationView.bringToFront();


        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_privacy_policy);
        Toast.makeText(this, "Started", Toast.LENGTH_SHORT).show();

    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Toast.makeText(this, "Started", Toast.LENGTH_SHORT).show();

        Log.e("TAG", "onNavigationItemSelected: " + item.getItemId());

        switch (item.getItemId()) {
            case R.id.nav_home:
                Toast.makeText(this, "pressed", Toast.LENGTH_SHORT).show();
                finish();
                navigationView.setCheckedItem(R.id.nav_home);
                startActivity(new Intent(PrivacyPolicy.this, MainActivity.class));
                break;
            case R.id.nav_location:
                finish();
                startActivity(new Intent(PrivacyPolicy.this, LocationSetActivity.class));
                navigationView.setCheckedItem(R.id.nav_location);
                break;
            case R.id.nav_sns:
                navigationView.setCheckedItem(R.id.nav_sns);
                SharedPreferences instagramPrefs = getSharedPreferences("InstagramPrefs", Context.MODE_PRIVATE);
                boolean isLoggedIn = instagramPrefs.getBoolean("is_logged_in", false);

                finish();
                if (isLoggedIn) {
                    startActivity(new Intent(PrivacyPolicy.this, InstagramLoggedInActivity.class));
                } else {
                    startActivity(new Intent(PrivacyPolicy.this, MediaSetActivity.class));
                }
                break;
            case R.id.nav_photos:
                finish();
                startActivity(new Intent(PrivacyPolicy.this, CapturedPhotosActivity.class));
                navigationView.setCheckedItem(R.id.nav_photos);
                break;
            case R.id.nav_smartwatch:
                finish();
                startActivity(new Intent(PrivacyPolicy.this, SmartwatchActivity.class));
                navigationView.setCheckedItem(R.id.nav_smartwatch);
                break;
            case R.id.nav_restart:
                customSensorsService = new Intent(this, MainService.class);
                dataSubmissionService = new Intent(this, DataSubmissionService.class);

                //when the function is called by clicking the button
                stopService(customSensorsService);
                stopService(dataSubmissionService);

                if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                    runOnUiThread(() -> dialog = Tools.requestPermissions(com.nematjon.edd_client_season_two.activities.PrivacyPolicy.this));
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
            case R.id.nav_privacy_policy:
                break;

        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }


}
