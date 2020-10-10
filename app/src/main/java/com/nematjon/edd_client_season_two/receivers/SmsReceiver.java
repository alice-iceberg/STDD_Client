package com.nematjon.edd_client_season_two.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import com.nematjon.edd_client_season_two.DbMgr;

import java.util.Objects;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = SmsReceiver.class.getSimpleName();
    public static final String pdu_type = "pdus";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        //init DbMgr if it's null
        if (DbMgr.getDB() == null)
            DbMgr.init(context);

        if (Objects.equals(intent.getAction(), SMS_RECEIVED)) {
            // Get the SMS message
            Log.e(TAG, "SMS on received ");
            SharedPreferences confPrefs = context.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
            long nowTime = System.currentTimeMillis();
            Bundle bundle = intent.getExtras();
            SmsMessage[] msgs;
            if (bundle != null) {
                String format = bundle.getString("format");

                // Retrieve the SMS message received.
                Object[] pdus = (Object[]) bundle.get(pdu_type);
                if (pdus != null) {
                    msgs = new SmsMessage[pdus.length];
                    for (int i = 0; i < msgs.length; i++) {
                        msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                        // Build the message to show.

                        // Log and display the SMS message.
                        Log.e(TAG, "onReceive: " + msgs[i].getOriginatingAddress() + " : " + msgs[i].getMessageBody());
                        int dataSourceId = confPrefs.getInt("SMS", -1);
                        assert dataSourceId != -1;
                        DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, msgs[i].getOriginatingAddress(), msgs[i].getMessageBody().length());
                    }
                }
            }
        }
    }
}
