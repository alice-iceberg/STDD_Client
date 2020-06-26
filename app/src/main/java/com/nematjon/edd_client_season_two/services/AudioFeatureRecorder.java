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
    // endregion

    AudioFeatureRecorder(final Context con) {
        final SharedPreferences prefs = con.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
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
                if (pitchDetectionResult.getPitch() > -1.0f && pitchDetectionResult.getPitch() != 918.75f) {
                    long nowTime = System.currentTimeMillis();
                    DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, pitchDetectionResult.getPitch(), sound_feature_type_pitch);
                }
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
