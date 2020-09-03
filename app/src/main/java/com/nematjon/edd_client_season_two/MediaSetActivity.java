package com.nematjon.edd_client_season_two;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.github.instagram4j.instagram4j.IGClient;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputLayout;
import com.nematjon.edd_client_season_two.services.MainService;

import java.io.IOException;

public class MediaSetActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MediaSetActivity.class.getSimpleName();

    EditText username;
    EditText password;
    TextInputLayout textInputLayout;
    DrawerLayout drawerLayout;
    NavigationView navigationView;
    Toolbar toolbar;

    String usernameString = null;
    String passwordString = null;
    String usernameCheck = "";

    boolean isSuccessfullyLoggedIn = false;

    SharedPreferences instagramPrefs;
    SharedPreferences configPrefs;
    static Intent customSensorsService;
    private AlertDialog dialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_socialmedia_setting);

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        textInputLayout = findViewById(R.id.etPasswordLayout);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);

        customSensorsService = new Intent(MediaSetActivity.this, MainService.class);

        //region password hint
        password.setHint(R.string.password);
        password.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                password.setHint(R.string.password);
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (TextUtils.isEmpty(password.getText().toString())) {
                    password.setHint(R.string.password);
                } else {
                    password.setHint(null);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        //endregion

        setSupportActionBar(toolbar);
        navigationView.bringToFront();
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_sns);

        instagramPrefs = getApplicationContext().getSharedPreferences("InstagramPrefs", MODE_PRIVATE);
        configPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
    }


    //region Button clicks listeners

    public void submitClick(View view) {

        usernameString = username.getText().toString();
        passwordString = password.getText().toString();

        if (Tools.isNetworkAvailable()) {

            if (usernameString == null && passwordString == null || usernameString.isEmpty() && passwordString.isEmpty()) {
                Toast.makeText(this, R.string.toast_username_password_empty, Toast.LENGTH_LONG).show();
                isSuccessfullyLoggedIn = false;
            } else if (usernameString == null || usernameString.isEmpty()) {
                Toast.makeText(this, R.string.toast_username_empty, Toast.LENGTH_LONG).show();
                isSuccessfullyLoggedIn = false;
            } else if (passwordString == null || passwordString.isEmpty()) {
                Toast.makeText(this, R.string.toast_password_empty, Toast.LENGTH_LONG).show();
                isSuccessfullyLoggedIn = false;
            } else {
                loginToInstagram(usernameString, passwordString);
            }
        } else {
            Toast.makeText(this, R.string.toast_check_internet, Toast.LENGTH_LONG).show();
        }
    }

    //endregion


    public void loginToInstagram(String username, String password) {

        String usernameNoSpaces = username.replace(" ", "");
        String passwordNoSpaces = password.replace(" ", "");

        LoggingInProgressTask loggingInProgressTask = new LoggingInProgressTask();
        loggingInProgressTask.execute(usernameNoSpaces, passwordNoSpaces);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private class LoggingInProgressTask extends AsyncTask<String, Void, Boolean> {

        LoadingDialog loadingDialog = new LoadingDialog(MediaSetActivity.this);

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            try {
                loadingDialog.startLoadingDialog();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Boolean doInBackground(String... strings) {

            String usernameNoSpaces = strings[0];
            String passwordNoSpaces = strings[1];

            try {
                IGClient client = IGClient.builder()
                        .username(usernameNoSpaces)
                        .password(passwordNoSpaces)
                        .login();
                usernameCheck = client.getSelfProfile().getFull_name();

                isSuccessfullyLoggedIn = !usernameCheck.equals("");
            } catch (IOException e) {
                e.printStackTrace();
            }

            SharedPreferences.Editor editor = instagramPrefs.edit();
            editor.putBoolean("is_logged_in", isSuccessfullyLoggedIn);
            editor.apply();
            return isSuccessfullyLoggedIn;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            loadingDialog.dismissDialog();

            if (isSuccessfullyLoggedIn) {
                Toast.makeText(MediaSetActivity.this, R.string.toast_success_login, Toast.LENGTH_SHORT).show();
                SharedPreferences.Editor editor = instagramPrefs.edit();
                editor.putString("instagram_username", usernameString.trim());
                editor.putString("instagram_password", passwordString.trim());
                editor.putBoolean("is_logged_in", isSuccessfullyLoggedIn);
                editor.apply();
                finish();
            } else {
                Toast.makeText(MediaSetActivity.this, R.string.toast_username_password_check, Toast.LENGTH_SHORT).show();
                SharedPreferences.Editor editor = instagramPrefs.edit();
                editor.putBoolean("is_logged_in", isSuccessfullyLoggedIn);
                editor.apply();
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_home:
                finish();
                navigationView.setCheckedItem(R.id.nav_home);
                startActivity(new Intent(MediaSetActivity.this, MainActivity.class));
                break;
            case R.id.nav_location:
                finish();
                startActivity(new Intent(MediaSetActivity.this, LocationSetActivity.class));
                navigationView.setCheckedItem(R.id.nav_location);
                break;
            case R.id.nav_sns:
                navigationView.setCheckedItem(R.id.nav_sns);
                SharedPreferences instagramPrefs = getSharedPreferences("InstagramPrefs", Context.MODE_PRIVATE);
                boolean isLoggedIn = instagramPrefs.getBoolean("is_logged_in", false);
                if (isLoggedIn) {
                    finish();
                    startActivity(new Intent(MediaSetActivity.this, InstagramLoggedInActivity.class));
                }
                break;
            case R.id.nav_photos:
                finish();
                startActivity(new Intent(MediaSetActivity.this, CapturedPhotosActivity.class));
                navigationView.setCheckedItem(R.id.nav_photos);
                break;
            case R.id.nav_restart:
                finish();
                navigationView.setCheckedItem(R.id.nav_restart);
                customSensorsService = new Intent(this, MainService.class);

                //when the function is called by clicking the button
                stopService(customSensorsService);
                if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                    runOnUiThread(() -> dialog = Tools.requestPermissions(MediaSetActivity.this));
                } else {
                    Log.e(TAG, "restartServiceClick: 3");
                    if (configPrefs.getLong("startTimestamp", 0) <= System.currentTimeMillis()) {
                        Log.e(TAG, "RESTART SERVICE");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startActivity(new Intent(MediaSetActivity.this, MainActivity.class));
                            startForegroundService(customSensorsService);
                        } else {
                            startActivity(new Intent(MediaSetActivity.this, MainActivity.class));
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
