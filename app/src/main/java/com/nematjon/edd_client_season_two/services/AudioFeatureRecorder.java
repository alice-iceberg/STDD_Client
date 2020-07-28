package com.nematjon.edd_client_season_two.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.nematjon.edd_client_season_two.DbMgr;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

class AudioFeatureRecorder {
    // region Constants
    private static final String TAG = AudioFeatureRecorder.class.getSimpleName();
    private final int SAMPLING_RATE = 11025;
    private final int AUDIO_BUFFER_SIZE = 1024;
    private final double SILENCE_THRESHOLD = -65.0D;
    // endregion

    // region Variables
    private boolean started;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private AudioDispatcher dispatcher;
    float currentPitch = 0;
    // endregion

    AudioFeatureRecorder(final Context con) {
        final SharedPreferences prefs = con.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        //init DbMgr if it's null
        if (DbMgr.getDB() == null)
            DbMgr.init(con);

        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLING_RATE, AUDIO_BUFFER_SIZE, 512);

        final SilenceDetector silenceDetector = new SilenceDetector(SILENCE_THRESHOLD, false);
        final MFCC mfccProcessor = new MFCC(AUDIO_BUFFER_SIZE, SAMPLING_RATE, 13, 30, 133.33f, 8000f);
        final String sound_feature_type_energy = "ENERGY";
        final String sound_feature_type_pitch = "PITCH";
        final String sound_feature_type_mfcc = "MFCC";
        final int dataSourceId = prefs.getInt("SOUND_DATA", -1);

        assert dataSourceId != -1;

        AudioProcessor mainProcessor = new AudioProcessor() {

            @Override
            public boolean process(AudioEvent audioEvent) {
                long nowTime = System.currentTimeMillis();
                if (silenceDetector.currentSPL() >= -110.0D) {
                    DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, silenceDetector.currentSPL(), sound_feature_type_energy);
                }

                float[] mfccs = mfccProcessor.getMFCC();
                DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, Arrays.toString(mfccs).replace(" ", ""), sound_feature_type_mfcc);
                return true;
            }

            @Override
            public void processingFinished() {
            }
        };

        //region Pitch extraction
        PitchDetectionHandler pitchHandler = new PitchDetectionHandler() {
            @Override
            public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {


                currentPitch = pitchDetectionResult.getPitch();
                long nowTime = System.currentTimeMillis();


                if (currentPitch > -1.0f && currentPitch != 918.75f) {
                    DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, currentPitch, sound_feature_type_pitch);
                }

                //speaking duration
                /*prevTimeDuringCall = currentTimeDuringCall;
                if (CallRcvr.AudioRunningForCall) {
                    Log.e(TAG, "ENTERED AUDIORUNNINGFORCALL ");
                    Log.e(TAG, "handlePitch: " + pitchDetectionResult.getPitch() );

                    if (currentPitch > -1.0f && currentPitch != 918.75f) {
                        Log.e(TAG, "CURRENT PITCH " + currentPitch );
                        currentTimeDuringCall = nowTime;
                        if ((currentTimeDuringCall - prevTimeDuringCall) > 2000) { // if the time difference between two consecutive pitches is more than 2 seconds, the speaker is changed
                            Log.e(TAG, "Number of speaker turns: " + numberOfSpeakingTurns);
                            numberOfSpeakingTurns += 1;

                        } else { // the speaker is same
                            speakingTurnDuration += (currentTimeDuringCall - prevTimeDuringCall);
                            Log.e(TAG, "Speaking turn duration in millis" + speakingTurnDuration );
                        }
                    }
                }*/
                /*else if (numberOfSpeakingTurns > 0 && speakingTurnDuration > 0) { //the call is ended
                    DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, numberOfSpeakingTurns, sound_feature_type_speaking_turns);
                    DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, speakingTurnDuration, sound_feature_type_speaking_turn_duration);
                    numberOfSpeakingTurns = 0;
                    speakingTurnDuration = 0;
                    currentTimeDuringCall = 0;
                    prevTimeDuringCall = 0;
                }*/


            }
        };
        PitchProcessor pitchProcessor = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.AMDF, SAMPLING_RATE, AUDIO_BUFFER_SIZE, pitchHandler);
        //endregion

        if (dispatcher == null)
            Log.e(TAG, "Dispatcher is NULL: ");
        dispatcher.addAudioProcessor(silenceDetector);
        dispatcher.addAudioProcessor(pitchProcessor);
        dispatcher.addAudioProcessor(mfccProcessor);
        dispatcher.addAudioProcessor(mainProcessor);
    }

    void start() {
        Log.d(TAG, "Started: AudioFeatureRecorder");
        executor.execute(dispatcher);
        started = true;
    }

    void stop() {
        Log.d(TAG, "Stopped: AudioFeatureRecorder");
        if (started) {
            dispatcher.stop();
            started = false;
        }
    }

}
