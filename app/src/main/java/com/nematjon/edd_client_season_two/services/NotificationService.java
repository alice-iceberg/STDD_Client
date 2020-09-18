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

        //init DbMgr if it's null
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

        //region Extract music information
        int musicPlayingDataSrcId = confPrefs.getInt("MUSIC_PLAYING", -1);
        String melonMusicPlayingDataType = "MELON";
        String bugsMusicPlayingDataType = "BUGS";
        String genieMusicPlayingDataType = "GENIE";
        String floMusicPlayingDataType = "FLO";
        String vibeMusicPlayingDataType = "VIBE";
        String musicPlayingArtistDataType = "ARTIST";
        String musicPlayingSongDataType = "SONG";
        String artist = "none";
        String songName = "none";

        //if "Melon" music application posts a notification
        if (packageName.equals("com.iloen.melon")) {
            long currentTime = System.currentTimeMillis();
            songName = nTicker;
            artist = title;
            if (musicPlayingDataSrcId != -1) {
                if (songName != null && artist != null) {
                    DbMgr.saveMixedData(musicPlayingDataSrcId, currentTime, 1.0f, currentTime, artist, musicPlayingArtistDataType, melonMusicPlayingDataType);
                    currentTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(musicPlayingDataSrcId, currentTime, 1.0f, currentTime, songName, musicPlayingSongDataType, melonMusicPlayingDataType);
                }
            }
            //if "Bugs" music application posts a notification
        } else if (packageName.equals("com.neowiz.android.bugs")) {
            long currentTime = System.currentTimeMillis();
            songName = title;
            if (musicPlayingDataSrcId != -1) {
                if (songName != null) {
                    DbMgr.saveMixedData(musicPlayingDataSrcId, currentTime, 1.0f, currentTime, songName, musicPlayingSongDataType, bugsMusicPlayingDataType);
                }
            }
        }
        //if "Genie" music application posts a notification
        else if (packageName.equals("com.ktmusic.geniemusic")) {
            long currentTime = System.currentTimeMillis();
            songName = title;
            if (musicPlayingDataSrcId != -1) {
                if (songName != null) {
                    DbMgr.saveMixedData(musicPlayingDataSrcId, currentTime, 1.0f, currentTime, songName, musicPlayingSongDataType, genieMusicPlayingDataType);
                }
            }
        }
        // if "Flo" music application posts a notification
        else if (packageName.equals("skplanet.musicmate")) {
            long currentTime = System.currentTimeMillis();
            songName = title;
            if (musicPlayingDataSrcId != -1) {
                if (songName != null) {
                    DbMgr.saveMixedData(musicPlayingDataSrcId, currentTime, 1.0f, currentTime, songName, musicPlayingSongDataType, floMusicPlayingDataType);
                }
            }
        }
        // if "Vibe" music application posts a notification
        else if (packageName.equals("com.naver.vibe")) {
            long currentTime = System.currentTimeMillis();
            songName = title;
            if (musicPlayingDataSrcId != -1) {
                if (songName != null) {
                    DbMgr.saveMixedData(musicPlayingDataSrcId, currentTime, 1.0f, currentTime, songName, musicPlayingSongDataType, vibeMusicPlayingDataType);
                }
            }
        }

        else if (packageName.equals("com.nematjon.edd_client_season_two")){
            Log.e("TAG", "Notif service firebase");
        }

        //endregion
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
            nowTime = System.currentTimeMillis();
            DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, -1, pckgName, NOTIF_TYPE_CLICKED);
        }
    }
}
