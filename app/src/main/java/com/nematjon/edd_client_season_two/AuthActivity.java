package com.nematjon.edd_client_season_two;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import inha.nsl.easytrack.ETServiceGrpc;
import inha.nsl.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

public class AuthActivity extends Activity {
    private static final int RC_OPEN_ET_AUTHENTICATOR = 100;
    private static final int RC_OPEN_MAIN_ACTIVITY = 101;
    private static final int RC_OPEN_APP_STORE = 102;

    // region Variables
    private static final String TAG = AuthActivity.class.getSimpleName();
    private SharedPreferences loginPrefs;
    public static final String user_id = "userId", usrEmail = "email", name = "fullname", device_model = "deviceModel", api_level = "apiLevel";
    // endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);


        if (authAppIsNotInstalled()) {
            Toast.makeText(this, "Please install the EasyTrack Authenticator and reopen the application!", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=inha.nsl.easytrack"));
            intent.setPackage("com.android.vending");
            startActivityForResult(intent, RC_OPEN_APP_STORE);
        } else {
            loginPrefs = getApplicationContext().getSharedPreferences("UserLogin", MODE_PRIVATE);
            if (loginPrefs.getBoolean("logged_in", false))
                startMainActivity();
        }
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(0);
        startActivityForResult(intent, RC_OPEN_MAIN_ACTIVITY);
        overridePendingTransition(0, 0);
    }

    private boolean authAppIsNotInstalled() {
        try {
            getPackageManager().getPackageInfo("inha.nsl.easytrack", 0);
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_OPEN_ET_AUTHENTICATOR) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    final String fullName = data.getStringExtra("fullName");
                    final String email = data.getStringExtra("email");
                    final int userId = data.getIntExtra("userId", -1);
                    final String deviceModel = Build.MODEL;
                    final int apiLevel = Build.VERSION.SDK_INT;

                    new Thread(() -> {
                        ManagedChannel channel = ManagedChannelBuilder.forAddress(
                                getString(R.string.grpc_host),
                                Integer.parseInt(getString(R.string.grpc_port))
                        ).usePlaintext().build();


                        ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                        EtService.BindUserToCampaign.Request requestMessage = EtService.BindUserToCampaign.Request.newBuilder()
                                .setUserId(userId)
                                .setEmail(email)
                                .setCampaignId(Integer.parseInt(getString(R.string.campaign_id)))
                                .build();

                        try {
                            final EtService.BindUserToCampaign.Response responseMessage = stub.bindUserToCampaign(requestMessage);
                            if (responseMessage.getSuccess())
                                runOnUiThread(() -> {
                                    Toast.makeText(AuthActivity.this, "Successfully authorized and connected to the EasyTrack campaign!", Toast.LENGTH_SHORT).show();
                                    loginPrefs = getApplicationContext().getSharedPreferences("UserLogin", MODE_PRIVATE);
                                    SharedPreferences.Editor editor = loginPrefs.edit();
                                    editor.putString(name, fullName);
                                    editor.putString(usrEmail, email);
                                    editor.putInt(user_id, userId);
                                    editor.putString(device_model, deviceModel);
                                    editor.putInt(api_level, apiLevel);
                                    editor.putBoolean("logged_in", true);
                                    editor.apply();
                                    startMainActivity();
                                });
                            else
                                runOnUiThread(() -> {
                                    Calendar cal = Calendar.getInstance();
                                    cal.setTimeInMillis(responseMessage.getCampaignStartTimestamp());
                                    String txt = String.format(Locale.getDefault(), "EasyTrack campaign hasn't started. Campaign start time is: %s",
                                            SimpleDateFormat.getDateTimeInstance().format(cal.getTime()));
                                    Toast.makeText(AuthActivity.this, txt, Toast.LENGTH_LONG).show();
                                });
                        } catch (StatusRuntimeException e) {
                            runOnUiThread(() -> Toast.makeText(AuthActivity.this, "An error occurred when connecting to the EasyTrack campaign. Please try again later!", Toast.LENGTH_SHORT).show());
                            Log.e(TAG, "onCreate: gRPC server unavailable");
                        } finally {
                            channel.shutdown();
                        }
                    }).start();
                }
            } else if (resultCode == Activity.RESULT_FIRST_USER)
                Toast.makeText(this, "Canceled", Toast.LENGTH_SHORT).show();
            else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Technical issue. Please check your internet connectivity and try again!", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == RC_OPEN_MAIN_ACTIVITY) {
            finish();
        } else if (requestCode == RC_OPEN_APP_STORE) {
            if (authAppIsNotInstalled())
                finish();
        }
    }

    public void authenticateClick(View view) {

        if (authAppIsNotInstalled())
            Toast.makeText(this, "Please install the EasyTrack Authenticator and reopen the application!", Toast.LENGTH_SHORT).show();
        else {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("inha.nsl.easytrack");
            if (launchIntent != null) {
                launchIntent.setFlags(0);
                startActivityForResult(launchIntent, RC_OPEN_ET_AUTHENTICATOR);
            }
        }
    }
}
