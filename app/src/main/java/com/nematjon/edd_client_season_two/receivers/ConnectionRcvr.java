package com.nematjon.edd_client_season_two.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.util.Log;

import com.nematjon.edd_client_season_two.Tools;

import java.util.Objects;

public class ConnectionRcvr extends BroadcastReceiver {
    private static final String TAG = ConnectionRcvr.class.getSimpleName();

    @Override
    public void onReceive(Context con, Intent intent) {

        if (Objects.equals(intent.getAction(), ConnectivityManager.CONNECTIVITY_ACTION)) {
            if (Tools.isNetworkAvailable()) {
                try {
                    Log.d(TAG, "Network is connected");
                    //TODO: do smth when internet is connected
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Log.d(TAG, "Network is changed or reconnected");
            }
        }
    }
}
