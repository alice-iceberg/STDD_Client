package com.nematjon.edd_client_season_two;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.GridView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.nematjon.edd_client_season_two.services.MainService;
import com.nematjon.edd_client_season_two.smartwatch.SmartwatchActivity;

public class CapturedPhotosActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener{

    private static final String TAG = CapturedPhotosActivity.class.getSimpleName();

    NavigationView navigationView;
    DrawerLayout drawerLayout;
    Toolbar toolbar;
    GridView gridView;

    SharedPreferences configPrefs;
    static Intent customSensorsService;
    private AlertDialog dialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_images);

        configPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);

        customSensorsService = new Intent(CapturedPhotosActivity.this, MainService.class);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
        gridView = findViewById(R.id.gridView);

        gridView.setAdapter(new ImageAdapter(this));
        gridView.setOnItemClickListener((adapterView, view, position, l) -> {
            Intent intent = new Intent(getApplicationContext(), FullScreenImageActivity.class);
            intent.putExtra("id", position);
            startActivity(intent);

        });


        setSupportActionBar(toolbar);
        navigationView.bringToFront();
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_photos);



    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_home:
                finish();
                navigationView.setCheckedItem(R.id.nav_home);
                startActivity(new Intent(CapturedPhotosActivity.this, MainActivity.class));
                break;
            case R.id.nav_location:
                finish();
                startActivity(new Intent(CapturedPhotosActivity.this, LocationSetActivity.class));
                navigationView.setCheckedItem(R.id.nav_location);
                break;
            case R.id.nav_sns:
                finish();
                navigationView.setCheckedItem(R.id.nav_sns);
                SharedPreferences instagramPrefs = getSharedPreferences("InstagramPrefs", Context.MODE_PRIVATE);
                boolean isLoggedIn = instagramPrefs.getBoolean("is_logged_in", false);

                if (isLoggedIn) {
                    startActivity(new Intent(CapturedPhotosActivity.this, InstagramLoggedInActivity.class));
                } else {
                    startActivity(new Intent(CapturedPhotosActivity.this, MediaSetActivity.class));
                }
                break;
            case R.id.nav_photos:
                break;
            case R.id.nav_smartwatch:
                startActivity(new Intent(CapturedPhotosActivity.this, SmartwatchActivity.class));
                navigationView.setCheckedItem(R.id.nav_smartwatch);
                break;
            case R.id.nav_restart:
                navigationView.setCheckedItem(R.id.nav_restart);
                finish();
                customSensorsService = new Intent(this, MainService.class);
                //when the function is called by clicking the button
                stopService(customSensorsService);
                if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog = Tools.requestPermissions(CapturedPhotosActivity.this);
                        }
                    });
                } else {
                    if (configPrefs.getLong("startTimestamp", 0) <= System.currentTimeMillis()) {
                        Log.e(TAG, "RESTART SERVICE");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startActivity(new Intent(CapturedPhotosActivity.this, MainActivity.class));
                            startForegroundService(customSensorsService);
                        } else {
                            startActivity(new Intent(CapturedPhotosActivity.this, MainActivity.class));
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
                            stopService(CapturedPhotosActivity.customSensorsService);
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

    @Override
    public void onBackPressed() {

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            navigationView.setCheckedItem(R.id.nav_home);
            super.onBackPressed();
        }
    }
}
