package com.nematjon.edd_client_season_two.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;


import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.nematjon.edd_client_season_two.DbMgr;


public class ActivityTransRcvr extends BroadcastReceiver {
    private static final String TAG = ActivityTransRcvr.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        SharedPreferences confPrefs = context.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        Task task = new Task(pendingResult, intent, confPrefs);
        task.execute();
    }

    private static class Task extends AsyncTask<String, Integer, String> {

        private final PendingResult pendingResult;
        private final Intent intent;
        private final SharedPreferences confPrefs;

        private Task(PendingResult pendingResult, Intent intent, SharedPreferences confPrefs) {
            this.pendingResult = pendingResult;
            this.intent = intent;
            this.confPrefs = confPrefs;
        }

        @Override
        protected String doInBackground(String... string) {
            if (intent != null) {
                if (ActivityTransitionResult.hasResult(intent)) {
                    ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
                    long nowTime = System.currentTimeMillis();
                    int dataSourceId = confPrefs.getInt("ACTIVITY_RECOGNITION", -1);
                    assert dataSourceId != -1;
                    if (result != null)
                        for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                            if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                                String activity_name = "";
                                switch (event.getActivityType()) {
                                    case DetectedActivity.STILL:
                                        activity_name = "STILL";
                                        break;
                                    case DetectedActivity.WALKING:
                                        activity_name = "WALKING";
                                        break;
                                    case DetectedActivity.RUNNING:
                                        activity_name = "RUNNING";
                                        break;
                                    case DetectedActivity.ON_BICYCLE:
                                        activity_name = "ON_BICYCLE";
                                        break;
                                    case DetectedActivity.IN_VEHICLE:
                                        activity_name = "IN_VEHICLE";
                                        break;
                                    default:
                                        break;
                                }
                                DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, activity_name, "ENTER");
                            } else if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                                String activity_name = "";
                                switch (event.getActivityType()) {
                                    case DetectedActivity.STILL:
                                        activity_name = "STILL";
                                        break;
                                    case DetectedActivity.WALKING:
                                        activity_name = "WALKING";
                                        break;
                                    case DetectedActivity.RUNNING:
                                        activity_name = "RUNNING";
                                        break;
                                    case DetectedActivity.ON_BICYCLE:
                                        activity_name = "ON_BICYCLE";
                                        break;
                                    case DetectedActivity.IN_VEHICLE:
                                        activity_name = "IN_VEHICLE";
                                        break;
                                    default:
                                        break;
                                }
                                DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, activity_name, "EXIT");
                            }
                        }
                }
            }
            return "Success";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            // Must call finish() so the BroadcastReceiver can be recycled.
            Log.e(TAG, "Activity recognition result: " + s);
            // sendNotification(s, entered);
            pendingResult.finish();
        }
    }
}
