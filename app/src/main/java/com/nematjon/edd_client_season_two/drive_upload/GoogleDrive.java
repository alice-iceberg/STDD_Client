package com.nematjon.edd_client_season_two.drive_upload;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.navigation.NavigationView;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.nematjon.edd_client_season_two.CapturedPhotosActivity;
import com.nematjon.edd_client_season_two.InstagramLoggedInActivity;
import com.nematjon.edd_client_season_two.LocationSetActivity;
import com.nematjon.edd_client_season_two.MainActivity;
import com.nematjon.edd_client_season_two.MediaSetActivity;
import com.nematjon.edd_client_season_two.R;
import com.nematjon.edd_client_season_two.Tools;
import com.nematjon.edd_client_season_two.services.MainService;
import com.nematjon.edd_client_season_two.smartwatch.SmartwatchActivity;

import java.io.File;
import java.util.Collections;

public class GoogleDrive extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {


    //region vars
    //UI  vars
    DrawerLayout drawerLayout;
    NavigationView navigationView;
    Toolbar toolbar;
    private AlertDialog dialog;
    Thread loadingThread;
    ProgressDialog progressDialog;


    //member  vars
    GoogleDriveHelper driveServiceHelper;
    SharedPreferences configPrefs;
    static Intent customSensorsService;
    private static final String TAG = "GoogleDrive";
    File filePath;

    //endregion
    //region  overrides
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drive);
        navigationView = findViewById(R.id.nav_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        toolbar = findViewById(R.id.toolbar);


        customSensorsService = new Intent(this, MainService.class);
        configPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);


        //initialize UI vars
        setSupportActionBar(toolbar);
        navigationView.bringToFront();
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_upload);

        //SignIn
        requestSignIn();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 400) {
            if (resultCode == RESULT_OK) {
                onAuthSuccess(data);
            }
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_home:
                finish();
                navigationView.setCheckedItem(R.id.nav_home);
                startActivity(new Intent(GoogleDrive.this, MainActivity.class));
                break;
            case R.id.nav_location:
                finish();
                startActivity(new Intent(GoogleDrive.this, LocationSetActivity.class));
                navigationView.setCheckedItem(R.id.nav_location);
                break;
            case R.id.nav_sns:
                finish();
                navigationView.setCheckedItem(R.id.nav_sns);
                SharedPreferences instagramPrefs = getSharedPreferences("InstagramPrefs", Context.MODE_PRIVATE);
                boolean isLoggedIn = instagramPrefs.getBoolean("is_logged_in", false);

                if (isLoggedIn) {
                    startActivity(new Intent(GoogleDrive.this, InstagramLoggedInActivity.class));
                } else {
                    startActivity(new Intent(GoogleDrive.this, MediaSetActivity.class));
                }
                break;
            case R.id.nav_photos:
                finish();
                startActivity(new Intent(GoogleDrive.this, CapturedPhotosActivity.class));
                navigationView.setCheckedItem(R.id.nav_photos);
                break;

            case R.id.nav_upload:
                break;
            case R.id.nav_smartwatch:
                startActivity(new Intent(GoogleDrive.this, SmartwatchActivity.class));
                navigationView.setCheckedItem(R.id.nav_smartwatch);
                break;
            case R.id.nav_restart:
                finish();
                navigationView.setCheckedItem(R.id.nav_restart);
                customSensorsService = new Intent(this, MainService.class);
                //when the function is called by clicking the button
                stopService(customSensorsService);
                if (!Tools.hasPermissions(this, Tools.PERMISSIONS)) {
                    runOnUiThread(() -> dialog = Tools.requestPermissions(GoogleDrive.this));
                } else {
                    if (configPrefs.getLong("startTimestamp", 0) <= System.currentTimeMillis()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startActivity(new Intent(GoogleDrive.this, MainActivity.class));
                            startForegroundService(customSensorsService);
                        } else {
                            startActivity(new Intent(GoogleDrive.this, MainActivity.class));
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
                            stopService(GoogleDrive.customSensorsService);
                            finish();
                        });
                alertDialog.setNegativeButton(
                        getString(R.string.cancel), (dialog, which) -> dialog.cancel());
                alertDialog.show();
                break;
        }
        return false;
    }

    //endregion
    //region build google  drive
    public void onAuthSuccess(Intent data) {
        GoogleSignIn.getSignedInAccountFromIntent(data).addOnSuccessListener(googleSignInAccount -> buildDrive(googleSignInAccount.getAccount())).addOnFailureListener(e -> Toast.makeText(this, "error", Toast.LENGTH_SHORT).show());
    }

    public void upload(View view) {

        if (Tools.isNetworkAvailable()) {
            progressDialog = ProgressDialog.show(this, "Submitting  to Google  Drive", "in progress...");
            loadingThread = new Thread(progressDialog::show);
            loadingThread.start();

            filePath = getDatabasePath("com.nematjon.edd_client_season_two");
            driveServiceHelper.createDBFile(GoogleDrive.this, filePath.getPath()).addOnSuccessListener(s -> {
                Toast.makeText(this, getString(R.string.success), Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
                loadingThread.interrupt();
                try {
                    loadingThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).addOnFailureListener(e -> {
                Toast.makeText(this,getString( R.string.try_again), Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
                loadingThread.interrupt();
                try {
                    loadingThread.join();
                } catch (InterruptedException er) {
                    er.printStackTrace();
                }

            }).addOnCanceledListener(() -> {
                Toast.makeText(this, getString(R.string.cancelled), Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
                loadingThread.interrupt();
                try {
                    loadingThread.join();
                } catch (InterruptedException er) {
                    er.printStackTrace();
                }
            });
        } else Toast.makeText(this, "Check your Internet connection!", Toast.LENGTH_SHORT).show();


    }

    private void requestSignIn() {
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(new Scope(DriveScopes.DRIVE)).build();
        GoogleSignInClient googleSignIn = GoogleSignIn.getClient(this, signInOptions);
        startActivityForResult(googleSignIn.getSignInIntent(), 400);
    }

    private void buildDrive(Account account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(this, Collections.singleton(DriveScopes.DRIVE));
        credential.setSelectedAccount(account);
        Drive drive = new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential).setApplicationName("Drive").build();
        driveServiceHelper = new GoogleDriveHelper(drive);
    }
    //endregion
}
