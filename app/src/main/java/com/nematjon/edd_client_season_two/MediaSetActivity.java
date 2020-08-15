package com.nematjon.edd_client_season_two;


import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
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
        password.setHint("Password");
        password.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                password.setHint("Password");
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (TextUtils.isEmpty(password.getText().toString())) {
                    password.setHint("Password");
                } else {
                    password.setHint(null);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        //endregion

                Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    IGClient client = IGClient.builder()
                            .username("alice_iceberg")
                            .password("9041121insta")
                            .login();

                    String check_username = client.getSelfProfile().getUsername();
                    Log.e("TAG", "run: USERNAME" + check_username );


                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

        instagramPrefs = getApplicationContext().getSharedPreferences("InstagramPrefs", MODE_PRIVATE);
    }


    //region Button clicks listeners

    public void submitClick(View view) {

        usernameString = username.getText().toString();
        passwordString = password.getText().toString();

        SharedPreferences.Editor editor = instagramPrefs.edit();
        editor.putString("instagram_username", usernameString);
        editor.putString("instagram_password", passwordString);
        editor.apply();

        if(Tools.isNetworkAvailable()) {

            if (usernameString.equals("") && passwordString.equals("")) {
                Toast.makeText(this, "Username and password cannot be empty!", Toast.LENGTH_LONG).show();
                isSuccessfullyLoggedIn = false;
            } else if (usernameString.equals("")) {
                Toast.makeText(this, "Username cannot be empty!", Toast.LENGTH_LONG).show();
                isSuccessfullyLoggedIn = false;
            } else if (passwordString.equals("")) {
                Toast.makeText(this, "Password cannot be empty!", Toast.LENGTH_LONG).show();
                isSuccessfullyLoggedIn = false;
            } else {
                //todo: add name and password check
                isSuccessfullyLoggedIn = loginToInstagram();
                Log.e("TAG", "submitClick: HERE" );
            }

            if (isSuccessfullyLoggedIn) {
                finish();
            } else {
                Toast.makeText(this, "Check your username, password and try again.", Toast.LENGTH_LONG).show();
            }
        } else{
            Toast.makeText(this, "Check Internet connection", Toast.LENGTH_LONG).show();
        }
    }

    public void homeBtnClick(View view) {
        finish();
    }
    //endregion



    public boolean loginToInstagram(){

        final String username;
        final String password;

        username = instagramPrefs.getString("instagram_username", "");
        password = instagramPrefs.getString("instagram_password", "");
        isSuccessfullyLoggedIn = true;

//        Thread thread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    IGClient client = IGClient.builder()
//                            .username("alice_iceberg")
//                            .password("9041121insta")
//                            .login();
//
//                    String check_username = client.getSelfProfile().getUsername();
//                    Log.e("TAG", "run: USERNAME" + check_username );
//
//                    if(client.isLoggedIn()){
//                        isSuccessfullyLoggedIn = true;
//                    }else{
//                        isSuccessfullyLoggedIn = false;
//
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//
//        thread.start();
        return isSuccessfullyLoggedIn;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}

//todo: before any request check Internet connection
