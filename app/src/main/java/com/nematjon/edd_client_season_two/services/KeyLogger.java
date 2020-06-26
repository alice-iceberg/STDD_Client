package com.nematjon.edd_client_season_two.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.nematjon.edd_client_season_two.DbMgr;

public class KeyLogger extends AccessibilityService {

    public static final String TAG = KeyLogger.class.getSimpleName();
    private SharedPreferences keyLogPrefs, confPrefs;
    private String prevText;

    private String KEYPRESS_TYPE_BACKSPACE = "BACKSPACE";
    private String KEYPRESS_TYPE_OTHER = "OTHER";
    private String KEYPRESS_TYPE_AUTOCORRECT = "AUTOCORRECT";


    @Override
    public void onServiceConnected() {
        Log.d("Keylogger", "Starting service");
        keyLogPrefs = getSharedPreferences("KeyLogVariables", MODE_PRIVATE);
        confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        //Log.e(TAG, "EVENT TRIGGERED");

        long nowTime = System.currentTimeMillis();
        //DateFormat df = new SimpleDateFormat("MM/dd/yyyy, HH:mm:ss z", Locale.US);
        //String time = df.format(Calendar.getInstance().getTime());

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            String text = event.getText().toString();
            String beforeText = event.getBeforeText().toString();
            int dataSourceId = confPrefs.getInt("KEYSTROKE_LOG", -1);
            assert dataSourceId != -1;

            //case when backspace pressed (length of prev text is more than length of current text)
            if (beforeText.length() > (text.length() - 2)) {
                // Log.e(TAG, "Backspace pressed! Time: " + nowTime);
                DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, -1, event.getPackageName(), KEYPRESS_TYPE_BACKSPACE);
            } else {
                //case when any key is pressed
                // Log.e(TAG, "Key pressed! Time: " + nowTime);
                DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, -1, event.getPackageName(), KEYPRESS_TYPE_OTHER);

                String cleanText = text.substring(1, text.length() - 1);
                if (cleanText.endsWith(" ") ||
                        cleanText.endsWith(".") ||
                        cleanText.endsWith(",") ||
                        cleanText.endsWith("!") ||
                        cleanText.endsWith("?")) {
                    if (!prevText.equals(cleanText.substring(0, cleanText.length() - 1))) { // compare the text without punctuation mark to prev text
                        // Log.e(TAG, "Auto-correction: YES");
                        DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, "YES", event.getPackageName(), KEYPRESS_TYPE_AUTOCORRECT);
                    } else {
                        // Log.e(TAG, "Auto-correction: NO");
                        DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, "NO", event.getPackageName(), KEYPRESS_TYPE_AUTOCORRECT);
                    }
                } else {
                    prevText = text.substring(1, text.length() - 1);
                }


                //case when typing started from 0 characters
                if (beforeText.length() == 0) {
                    Log.e(TAG, "Typing started from beginning! Time: " + nowTime);
                    long lastKeyPressTime = keyLogPrefs.getLong("last_key_press_time", 0);
                    long prevTypingStartTime = keyLogPrefs.getLong("previous_typing_start_time", 0);
                    if (lastKeyPressTime != 0) {
                        Log.e(TAG, "Previous typing end! Time: " + lastKeyPressTime);
                        dataSourceId = confPrefs.getInt("TYPING", -1);
                        assert dataSourceId != -1;
                        DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, prevTypingStartTime, lastKeyPressTime, event.getPackageName());
                    }
                    SharedPreferences.Editor editor = keyLogPrefs.edit();
                    editor.putLong("previous_typing_start_time", nowTime);
                    editor.apply();
                } else {
                    SharedPreferences.Editor editor = keyLogPrefs.edit();
                    editor.putLong("last_key_press_time", nowTime);
                    editor.apply();
                }
            }
        }
    }

    @Override
    public void onInterrupt() {

    }
}
