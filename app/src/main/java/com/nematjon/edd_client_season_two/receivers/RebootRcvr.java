package com.nematjon.edd_client_season_two.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.nematjon.edd_client_season_two.AuthActivity;

import java.util.Objects;


public class RebootRcvr extends BroadcastReceiver {
    private static final String TAG = RebootRcvr.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {

            // region re-initialize network variables
            SharedPreferences networkPrefs = context.getSharedPreferences("NetworkVariables", Context.MODE_PRIVATE);
            SharedPreferences.Editor networkEditor = networkPrefs.edit();
            networkEditor.putLong("prev_rx_network_data", 0);
            networkEditor.putLong("prev_tx_network_data", 0);
            networkEditor.apply();
            // endregion

            // region Open the app
            Intent intentService = new Intent(context, AuthActivity.class);
            intentService.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intentService.putExtra("fromReboot", true);
            context.startActivity(intentService);
            // endregion

        } else {
            Log.e(TAG, "Received unexpected intent " + intent.toString());
        }
    }
}
