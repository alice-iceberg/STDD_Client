package com.nematjon.edd_client_season_two;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class InstagramLoggedInActivity extends AppCompatActivity {

    TextView instagramUsername;
    SharedPreferences instagramPrefs;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instagram_loggedin);

        instagramUsername = findViewById(R.id.instagram_username);
        instagramPrefs = getSharedPreferences("InstagramPrefs", Context.MODE_PRIVATE);
        String username = instagramPrefs.getString("instagram_username", " ");

        instagramUsername.setText(username);
    }


    //Button click listeners
    public void homeBtnLoggedinClick(View view) {
        finish();
    }

    public void instagramLogOutClick(View view) {
        finish();
        startActivity(new Intent(InstagramLoggedInActivity.this, MediaSetActivity.class));
        //deleting username and password from shared prefs
        SharedPreferences.Editor editor = instagramPrefs.edit();
        editor.putString("instagram_username", "");
        editor.putString("instagram_password", "");
        editor.putBoolean("is_logged_in", false);
        editor.apply();
    }
}
