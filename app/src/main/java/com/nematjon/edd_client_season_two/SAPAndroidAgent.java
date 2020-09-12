package com.nematjon.edd_client_season_two;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.accessory.SA;
import com.samsung.android.sdk.accessory.SAAgentV2;
import com.samsung.android.sdk.accessory.SAPeerAgent;
import com.samsung.android.sdk.accessory.SASocket;

import java.io.IOException;

import static com.nematjon.edd_client_season_two.SmartwatchActivity.TAG;

public class SAPAndroidAgent extends SAAgentV2 {
    private static final int CHANNEL_ID = 110;
    static SASocket mProviderServiceSocket;
    static boolean runThreads = true;
    private static boolean sent = false;

    private ConnectionListener connectionListener;


    public SAPAndroidAgent(Context context) {

        super(TAG, context, ServiceConnection.class);

        //init DbMgr if it's null
        if (DbMgr.getDB() == null)
            DbMgr.init(getApplicationContext());

        SA mAccessory = new SA();
        try {
            mAccessory.initialize(context);
        } catch (SsdkUnsupportedException e0) {
            Log.e(TAG, "SsdkUnsupportedException : ${e0.message}");
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    @Override
    protected void onFindPeerAgentsResponse(SAPeerAgent[] peerAgents, int result) {
        if (result == PEER_AGENT_FOUND) {
            for (SAPeerAgent peerAgent : peerAgents)
                requestServiceConnection(peerAgent);
            Log.e(TAG, "Peer agents found");
        } else {
            Log.e(TAG, "Failed to find a peer agent : " + result);
        }
    }

    @Override
    protected void onServiceConnectionRequested(SAPeerAgent peerAgent) {
        if (peerAgent != null) {
            acceptServiceConnectionRequest(peerAgent);
            Log.e(TAG, "Incoming service connection request accepted");
        }
    }

    @Override
    protected void onServiceConnectionResponse(SAPeerAgent peerAgent, SASocket socket, int result) {
        switch (result) {
            case CONNECTION_SUCCESS:
                mProviderServiceSocket = socket;
                if (connectionListener != null)
                    connectionListener.run(true);
                Toast.makeText(getApplicationContext(), "Gear connection is established", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Gear S3 connection established");
                break;
            case CONNECTION_ALREADY_EXIST:
                mProviderServiceSocket = socket;
                if (connectionListener != null)
                    connectionListener.run(true);
                Toast.makeText(getApplicationContext(), "Gear connection already exists", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Gear S3 connection established");
                break;
            default:
                Log.e(TAG, "Failed to establish Gear S3 connection : " + result);
                break;
        }
    }

    @Override
    protected void onPeerAgentsUpdated(SAPeerAgent[] peerAgents, int result) {
        if (peerAgents != null)
            if (result == PEER_AGENT_AVAILABLE) {
                for (SAPeerAgent peerAgent : peerAgents)
                    requestServiceConnection(peerAgent);
            }
    }

    @Override
    protected void onError(SAPeerAgent peerAgent, String message, int result) {
        super.onError(peerAgent, message, result);
        runThreads = false;
    }

    static boolean sendMessage(byte[] bytes) {
        sent = false;
        Thread thread = new Thread(() -> {
            try {
                mProviderServiceSocket.send(CHANNEL_ID, bytes);
                sent = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return sent;
    }


    interface ConnectionListener {
        void run(boolean connected);
    }

    void setConnectionListener(ConnectionListener listener) {
        listener.run(mProviderServiceSocket != null);
        connectionListener = listener;
    }
}
