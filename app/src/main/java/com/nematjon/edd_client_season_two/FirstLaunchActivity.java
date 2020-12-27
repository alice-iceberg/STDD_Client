package com.nematjon.edd_client_season_two;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;


public class FirstLaunchActivity extends Activity {


    private Button acceptButton;

    private String[] permissionText = {
            "Location", "Camera", "Contacts", "Wi-Fi State", "Audio", "SMS Content", "App Usage stats", "Storage", "Bluetooth", "qergerg"
    };

    private String[] permissionContent = {
            "Used for accessing the User’s location. This application may collect location info and can send to the Server for processing.",
            "Used to access  the camera or capturing images.",
            "Used to access the user's list of contacts in the device.",
            ": Used to  access the user's  WIFI state. ",
            "Allows accessing  and recording  audio  from the user’s device.",
            "Used to  collect and process SMS data that is received and sent.",
            "Used to collect data about application usage",
            "Used to steal  your  data",
    };

   /* private Drawable[] drawables = {
            ContextCompat.getDrawable(this, R.drawable.location_icon),
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_camera_alt_24),
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_contact_page_24),
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_wifi_24),
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_audiotrack_24),
            ContextCompat.getDrawable(this, R.drawable.ic_baseline_sms_24),
    };
*/
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_launch);

        List<Permisssion> permisssions = new ArrayList<>();
        acceptButton = findViewById(R.id.accept);
        for (int i = 0; i < 8; i++) {
            permisssions.add(new Permisssion(permissionText[i], permissionContent[i]));
        }


        ListViewAdapter adapter = new ListViewAdapter(this, 0, permisssions);
        ListView listView = findViewById(R.id.listView);
        listView.setAdapter(adapter);


    }

    private  void  acceptPermissions(View view){

    }




}
