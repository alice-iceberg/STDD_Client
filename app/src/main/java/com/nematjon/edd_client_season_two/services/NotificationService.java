package com.nematjon.edd_client_season_two.services;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.nematjon.edd_client_season_two.CapturedPhotosActivity;
import com.nematjon.edd_client_season_two.DbMgr;
import com.nematjon.edd_client_season_two.EMAActivity;
import com.nematjon.edd_client_season_two.MainActivity;
import com.nematjon.edd_client_season_two.MediaSetActivity;
import com.nematjon.edd_client_season_two.Tools;
import com.nematjon.edd_client_season_two.receivers.EMAAlarmRcvr;

import java.util.Arrays;
import java.util.Calendar;
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
        int notifDataSourceId = confPrefs.getInt("NOTIFICATIONS", -1);
        int smsDataSourceId = confPrefs.getInt("SMS", -1);
        String smsFromNotifDataType = "SMS NOTIFICATION";

        //init DbMgr if it's null
        if (DbMgr.getDB() == null)
            DbMgr.init(getApplicationContext());

        if (notifDataSourceId != -1) {
            DbMgr.saveMixedData(notifDataSourceId, nowTime, 1.0f, nowTime, -1, packageName, NOTIF_TYPE_ARRIVED);
        }

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
        String aimpMusicPlayingDataType = "AIMP";
        String youtubeMusicPlayingDataType = "YOUTUBE MUSIC";
        String samsungMusicPlayingDataType = "SAMSUNG PLAYER";
        String googleMusicPlayingDataType = "GOOGLE PLAYER";
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
        // if "AIMP" music application posts a notification
        else if (packageName.equals("com.aimp.player")) {
            long currentTime = System.currentTimeMillis();
            songName = title;
            if (musicPlayingDataSrcId != -1) {
                if (songName != null) {
                    DbMgr.saveMixedData(musicPlayingDataSrcId, currentTime, 1.0f, currentTime, songName, musicPlayingSongDataType, aimpMusicPlayingDataType);
                }
            }
        }
        // if "Google player" music application posts a notification
        else if (packageName.equals("com.google.android.music")) {
            long currentTime = System.currentTimeMillis();
            songName = title;
            if (musicPlayingDataSrcId != -1) {
                if (songName != null) {
                    DbMgr.saveMixedData(musicPlayingDataSrcId, currentTime, 1.0f, currentTime, songName, musicPlayingSongDataType, googleMusicPlayingDataType);
                }
            }
        }
        // if "Youtube player" music application posts a notification
        else if (packageName.equals("com.google.android.apps.youtube.music")) {
            long currentTime = System.currentTimeMillis();
            songName = title;
            if (musicPlayingDataSrcId != -1) {
                if (songName != null) {
                    DbMgr.saveMixedData(musicPlayingDataSrcId, currentTime, 1.0f, currentTime, songName, musicPlayingSongDataType, youtubeMusicPlayingDataType);
                }
            }
        }
        // if "Samsung player" music application posts a notification
        else if (packageName.equals("com.sec.android.app.music")) {
            long currentTime = System.currentTimeMillis();
            songName = "No title detected";
            if (musicPlayingDataSrcId != -1) {
                DbMgr.saveMixedData(musicPlayingDataSrcId, currentTime, 1.0f, currentTime, songName, musicPlayingSongDataType, samsungMusicPlayingDataType);
            }
        }

        //endregion

        //region EMA received
        else if (packageName.equals("com.nematjon.edd_client_season_two")) {
            //EMA is received
            SharedPreferences rewardPrefs = getSharedPreferences("Rewards", MODE_PRIVATE);
            SharedPreferences loginPrefs = getSharedPreferences("UserLogin", MODE_PRIVATE);
            SharedPreferences.Editor editor = loginPrefs.edit();
            editor.putBoolean("ema_btn_make_visible", true);
            editor.apply();
            Intent intent_ema_alarm_rcvr = new Intent(NotificationService.this, EMAAlarmRcvr.class);
            intent_ema_alarm_rcvr.putExtra("ema_notif", true);
            //show ema alert dialog
            int ema_order = Tools.getEMAOrderFromRangeAfterEMA(Calendar.getInstance());
            if (ema_order != 0 && ema_order != -1) {
                //check whether the EMA was already answered
                if (ema_order == 1) {
                    boolean ema1_answered = rewardPrefs.getBoolean("ema1_answered", false);
                    boolean ema2_answered = rewardPrefs.getBoolean("ema2_answered", false);
                    boolean ema3_answered = rewardPrefs.getBoolean("ema3_answered", false);
                    boolean ema4_answered = rewardPrefs.getBoolean("ema4_answered", false);
                    if (!ema1_answered)
                        startService(new Intent(getApplicationContext(), EMAOverlayShowingService.class));
                    if (ema2_answered || ema3_answered || ema4_answered) {
                        SharedPreferences.Editor rewardsEditor = rewardPrefs.edit();
                        rewardsEditor.putBoolean("ema2_answered", false);
                        rewardsEditor.putBoolean("ema3_answered", false);
                        rewardsEditor.putBoolean("ema4_answered", false);
                        rewardsEditor.apply();
                    }
                } else if (ema_order == 2) {
                    boolean ema2_answered = rewardPrefs.getBoolean("ema2_answered", false);
                    boolean ema3_answered = rewardPrefs.getBoolean("ema3_answered", false);
                    boolean ema4_answered = rewardPrefs.getBoolean("ema4_answered", false);
                    if (!ema2_answered)
                        startService(new Intent(getApplicationContext(), EMAOverlayShowingService.class));
                    if (ema3_answered || ema4_answered) {
                        SharedPreferences.Editor rewardsEditor = rewardPrefs.edit();
                        rewardsEditor.putBoolean("ema3_answered", false);
                        rewardsEditor.putBoolean("ema4_answered", false);
                        rewardsEditor.apply();
                    }
                } else if (ema_order == 3) {
                    boolean ema3_answered = rewardPrefs.getBoolean("ema3_answered", false);
                    boolean ema4_answered = rewardPrefs.getBoolean("ema4_answered", false);
                    if (!ema3_answered)
                        startService(new Intent(getApplicationContext(), EMAOverlayShowingService.class));
                    if (ema4_answered) {
                        SharedPreferences.Editor rewardsEditor = rewardPrefs.edit();
                        rewardsEditor.putBoolean("ema4_answered", false);
                        rewardsEditor.apply();
                    }
                } else if (ema_order == 4) {
                    boolean ema4_answered = rewardPrefs.getBoolean("ema4_answered", false);
                    if (!ema4_answered)
                        startService(new Intent(getApplicationContext(), EMAOverlayShowingService.class));
                }
            }
        } else if (packageName.equals("com.samsung.android.messaging")) {
            try {
                String[] senderWithMessage = nTicker.split(":", 2);
                String sender = senderWithMessage[0];
                int messageLength = senderWithMessage[1].length();
                nowTime = System.currentTimeMillis();

                if (smsDataSourceId != -1) {
                    DbMgr.saveMixedData(smsDataSourceId, nowTime, 1.0f, nowTime, sender, messageLength, smsFromNotifDataType);
                }
            } catch (Exception e) {
                Log.e("SMS", "onNotificationPosted: could not extract message information");
            }

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
            if (dataSourceId != -1) {
                DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, notifKeys.get(sbn.getKey()), nowTime, pckgName, NOTIF_TYPE_DECISION_TIME);
            }
            notifKeys.remove(sbn.getKey());
        }

        // detect click here (reasons: 1);
        if (reason == NotificationService.REASON_CLICK) {
            int dataSourceId = confPrefs.getInt("NOTIFICATIONS", -1);
            if (dataSourceId != -1) {
                nowTime = System.currentTimeMillis();
                DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, -1, pckgName, NOTIF_TYPE_CLICKED);
            }
        }
    }
}
