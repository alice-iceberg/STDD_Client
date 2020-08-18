package com.nematjon.edd_client_season_two;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.instagram4j.instagram4j.IGClient;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;

public class MediaSetActivity extends AppCompatActivity {


    EditText username;
    EditText password;
    TextInputLayout textInputLayout;
    Button homeBtn;

    String usernameString = null;
    String passwordString = null;
    String usernameCheck = "";

    boolean isSuccessfullyLoggedIn = false;

    SharedPreferences instagramPrefs;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_socialmedia_setting);

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        homeBtn = findViewById(R.id.home_btn);
        textInputLayout = findViewById(R.id.etPasswordLayout);


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

        instagramPrefs = getApplicationContext().getSharedPreferences("InstagramPrefs", MODE_PRIVATE);
    }


    //region Button clicks listeners

    public void submitClick(View view) {

        usernameString = username.getText().toString();
        passwordString = password.getText().toString();

        //removing spaces at the beginning and at the end
        usernameString = usernameString.trim();
        passwordString = passwordString.trim();

        if (Tools.isNetworkAvailable()) {

            if (usernameString == null && passwordString == null) {
                Toast.makeText(this, R.string.toast_username_password_empty, Toast.LENGTH_LONG).show();
                isSuccessfullyLoggedIn = false;
            } else if (usernameString == null) {
                Toast.makeText(this, R.string.toast_username_empty, Toast.LENGTH_LONG).show();
                isSuccessfullyLoggedIn = false;
            } else if (passwordString == null) {
                Toast.makeText(this, R.string.toast_password_empty, Toast.LENGTH_LONG).show();
                isSuccessfullyLoggedIn = false;
            } else {
                //todo: add name and password check
                SharedPreferences.Editor editor = instagramPrefs.edit();
                editor.putString("instagram_username", usernameString);
                editor.putString("instagram_password", passwordString);
                editor.apply();

                isSuccessfullyLoggedIn = loginToInstagram(usernameString, passwordString);

                if (isSuccessfullyLoggedIn) {
                    Toast.makeText(this, R.string.toast_success_login, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, R.string.toast_username_password_check, Toast.LENGTH_SHORT).show();
                }
            }


        } else {
            Toast.makeText(this, R.string.toast_check_internet, Toast.LENGTH_LONG).show();
        }
    }

    public void homeBtnClick(View view) {
        finish();
    }
    //endregion


    public boolean loginToInstagram(String username, String password) {


        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    IGClient client = IGClient.builder()
                            .username(username)
                            .password(password)
                            .login();
                    usernameCheck = client.getSelfProfile().getFull_name();

                    if (!usernameCheck.equals("")) {
                        isSuccessfullyLoggedIn = true;
                    } else {
                        isSuccessfullyLoggedIn = false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
        return isSuccessfullyLoggedIn;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
