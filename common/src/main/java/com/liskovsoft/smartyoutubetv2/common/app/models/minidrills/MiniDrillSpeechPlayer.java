package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerManager;

import java.util.HashMap;
import java.util.Locale;

public class MiniDrillSpeechPlayer {
    private static final String TAG = MiniDrillSpeechPlayer.class.getSimpleName();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private TextToSpeech mTextToSpeech;
    private boolean mReady;
    private float mPreviousVolume = -1f;
    private PlayerManager mDuckedPlayer;

    public MiniDrillSpeechPlayer(Context context) {
        mTextToSpeech = new TextToSpeech(context.getApplicationContext(), status -> {
            mReady = status == TextToSpeech.SUCCESS;

            if (!mReady) {
                Log.e(TAG, "TextToSpeech init failed: %s", status);
            }
        });

        mTextToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                restoreVolume();
            }

            @Override
            public void onError(String utteranceId) {
                restoreVolume();
            }
        });
    }

    public void configure(MiniDrillConfig config) {
        if (mTextToSpeech == null || config == null || config.audio == null) {
            return;
        }

        mTextToSpeech.setSpeechRate(config.audio.speechRate);
        mTextToSpeech.setPitch(config.audio.pitch);
    }

    public void speak(MiniDrillAudio audio, boolean duckVideo, float duckVolume, PlayerManager player) {
        MiniDrillAudio speechAudio = audio != null ? audio.resolveForSpeech() : MiniDrillAudio.none();

        if (!mReady || mTextToSpeech == null || !speechAudio.canSpeak()) {
            return;
        }

        setLanguage(speechAudio.language);

        if (duckVideo && player != null) {
            duckVolume(player, duckVolume);
        }

        String utteranceId = "mini_drill_" + System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= 21) {
            Bundle params = new Bundle();
            mTextToSpeech.speak(speechAudio.text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            mTextToSpeech.speak(speechAudio.text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    public void stop() {
        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
        }

        restoreVolume();
    }

    public void shutdown() {
        stop();

        if (mTextToSpeech != null) {
            mTextToSpeech.shutdown();
            mTextToSpeech = null;
        }
    }

    private void setLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return;
        }

        String[] parts = language.split("-");
        Locale locale = parts.length >= 2 ? new Locale(parts[0], parts[1]) : new Locale(parts[0]);
        mTextToSpeech.setLanguage(locale);
    }

    private void duckVolume(PlayerManager player, float duckVolume) {
        if (mDuckedPlayer != null) {
            restoreVolume();
        }

        mDuckedPlayer = player;
        mPreviousVolume = player.getVolume();
        player.setVolume(Math.max(0f, Math.min(1f, duckVolume)));
    }

    private void restoreVolume() {
        mHandler.post(() -> {
            if (mDuckedPlayer != null && mPreviousVolume >= 0) {
                mDuckedPlayer.setVolume(mPreviousVolume);
            }

            mDuckedPlayer = null;
            mPreviousVolume = -1f;
        });
    }
}
