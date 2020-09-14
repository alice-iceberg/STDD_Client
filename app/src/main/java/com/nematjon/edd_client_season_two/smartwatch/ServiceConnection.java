package com.nematjon.edd_client_season_two.smartwatch;

import android.util.Log;

import com.nematjon.edd_client_season_two.DbMgr;
import com.samsung.android.sdk.accessory.SASocket;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.nematjon.edd_client_season_two.smartwatch.SmartwatchActivity.TAG;

public class ServiceConnection extends SASocket {

    private static OnReceiveListener onReceiveListener;
    public static boolean serviceConnectionAvailable = false;

    public ServiceConnection() {
        super(ServiceConnection.class.getName());
        Log.e(TAG, "ServiceConnection created");
        serviceConnectionAvailable = true;
    }

    @Override
    public void onReceive(int channelId, byte[] bytes) {
        serviceConnectionAvailable = true;
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n");
        for (String line : lines) {
            String[] elements = line.split(",");
            if (elements.length < 3)
                continue;
            int dataSource = Integer.parseInt(elements[0]);
            long timestamp = Long.parseLong(elements[1]);
            StringBuilder values = new StringBuilder();
            for (String value : Arrays.copyOfRange(elements, 2, elements.length))
                values.append(value).append(',');
            if (values.length() > 0)
                values.replace(values.length() - 1, values.length(), "");
            DbMgr.saveMixedData(dataSource, timestamp, 0.0f, values);
            if (onReceiveListener != null)
                try {
                    int samplesCount = Math.max(lines.length - 1, 0);
                    if (samplesCount > 0)
                        onReceiveListener.onReceive(samplesCount);
                } catch (Exception e) {
                    Log.i(TAG, "onReceiveListener error : " + e.getMessage());
                }
        }
    }

    @Override
    public void onError(int channelId, String errorMessage, int errorCode) {
        serviceConnectionAvailable = false;
        Log.e(TAG, "Error : channel(" + channelId + "), errorCode(" + errorCode + "), message(" + errorMessage + ")");
    }

    @Override
    protected void onServiceConnectionLost(int errorCode) {
        serviceConnectionAvailable = false;
        Log.e(TAG, "Service connection lost, errorCode : " + errorCode);
    }

    static void setOnReceiveListener(OnReceiveListener onReceiveListener) {
        ServiceConnection.onReceiveListener = onReceiveListener;
    }

    interface OnReceiveListener {
        void onReceive(int sampleCount);
    }
}
