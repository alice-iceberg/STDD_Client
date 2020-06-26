package com.nematjon.edd_client_season_two.services;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.nematjon.edd_client_season_two.DbMgr;

import java.util.HashMap;

public class NotificationService extends NotificationListenerService {

    HashMap<String, Long> notifKeys = new HashMap<>();
    private String NOTIF_TYPE_ARRIVED = "ARRIVED";
    private String NOTIF_TYPE_CLICKED = "CLICKED";
    private String NOTIF_TYPE_DECISION_TIME = "DECISION_TIME";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        long nowTime = System.currentTimeMillis();
        SharedPreferences confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        String packageName = sbn.getPackageName();
        String notifKey = sbn.getKey();
        notifKeys.put(notifKey, System.currentTimeMillis());
        int dataSourceId = confPrefs.getInt("NOTIFICATIONS", -1);
        assert dataSourceId != -1;

        if (DbMgr.getDB() == null)
            DbMgr.init(getApplicationContext());
        DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, -1, packageName, NOTIF_TYPE_ARRIVED);

        String nTicker = "";
        if (sbn.getNotification().tickerText != null) {
            nTicker = sbn.getNotification().tickerText.toString();
        }
        Bundle extras = sbn.getNotification().extras;
        String title = extras.getString(Notification.EXTRA_TITLE);

        Log.e("Package: ", packageName);
        Log.e("Key: ", notifKey);
        Log.e("Ticker: ", nTicker);
        if (title != null)
            Log.e("Title: ", title);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        super.onNotificationRemoved(sbn, rankingMap, reason);
        long nowTime = System.currentTimeMillis();
        SharedPreferences confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        String pckgName = sbn.getPackageName();
        Log.e("Notification Removed", "Reason: " + reason);
        // any code is decision
        if (notifKeys.containsKey(sbn.getKey())) {
            int dataSourceId = confPrefs.getInt("NOTIFICATIONS", -1);
            assert dataSourceId != -1;
            DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, notifKeys.get(sbn.getKey()), nowTime, pckgName, NOTIF_TYPE_DECISION_TIME);
            notifKeys.remove(sbn.getKey());
        }

        // detect click here (reasons: 1);
        if (reason == NotificationService.REASON_CLICK) {
            int dataSourceId = confPrefs.getInt("NOTIFICATIONS", -1);
            assert dataSourceId != -1;
            DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, -1, pckgName, NOTIF_TYPE_CLICKED);
        }
    }
}
