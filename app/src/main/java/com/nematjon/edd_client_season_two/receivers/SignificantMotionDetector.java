package com.nematjon.edd_client_season_two.receivers;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;

import com.nematjon.edd_client_season_two.DbMgr;


public class SignificantMotionDetector extends TriggerEventListener {

    public static final String TAG = "SigMotionListener";
    private Context context;

    public SignificantMotionDetector(Context con) {
        this.context = con;
    }

    @Override
    public void onTrigger(TriggerEvent event) {
        long nowTime = System.currentTimeMillis();
        if (event.values[0] == 1) {
            final SensorManager mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            assert mSensorManager != null;
            final Sensor sensorSM = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
            SharedPreferences prefs = context.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
            int dataSourceId = prefs.getInt("ANDROID_SIGNIFICANT_MOTION", -1);
            assert dataSourceId != -1;
            DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime);
            mSensorManager.requestTriggerSensor(this, sensorSM);
        }
    }
}
