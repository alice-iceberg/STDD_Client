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
import android.widget.Toolbar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;

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

        instagramPrefs = getApplicationContext().getSharedPreferences("InstagramPrefs", MODE_PRIVATE);
    }


    //region Button clicks listeners

    public void submitClick(View view) {

        usernameString = username.getText().toString();
        passwordString = password.getText().toString();

        SharedPreferences.Editor editor = instagramPrefs.edit();
        editor.putString("username", usernameString);
        editor.putString("password", passwordString);
        editor.apply();

        //todo: add name and password check
        isSuccessfullyLoggedIn = true;

        if(isSuccessfullyLoggedIn){
            finish();
        }
    }

    public void homeBtnClick(View view){
        finish();
    }

    //endregion


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
