package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import io.reactivex.Observable;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MiniDrillConfigSource {
    private static final String TAG = MiniDrillConfigSource.class.getSimpleName();
    private static final String CONFIG_URL = "https://gist.githubusercontent.com/ultrox/eea9769ac10209a5942264a33aea6f4e/raw/mini-drils.json";
    private static final String PREFS_NAME = "mini_drills";
    private static final String KEY_LATEST_GOOD_JSON = "latest_good_json";

    private static MiniDrillConfig sLatestConfig;

    public static void prefetch(Context context) {
        RxHelper.execute(new MiniDrillConfigSource(context).loadObserve(), config -> {}, error ->
                Log.e(TAG, "prefetch error: %s", error.getMessage()));
    }

    private final Context mContext;

    public MiniDrillConfigSource(Context context) {
        mContext = context.getApplicationContext();
    }

    public Observable<MiniDrillConfig> loadObserve() {
        return RxHelper.fromCallable(this::load);
    }

    public MiniDrillConfig load() {
        if (sLatestConfig != null) {
            return sLatestConfig;
        }

        sLatestConfig = loadRemote();

        if (sLatestConfig == null) {
            sLatestConfig = loadLatestGood();
        }

        if (sLatestConfig == null) {
            sLatestConfig = loadBundledFallback();
        }

        return sLatestConfig;
    }

    private MiniDrillConfig loadRemote() {
        try {
            String json = fetchJson(addCacheBust(CONFIG_URL));
            MiniDrillConfig config = MiniDrillConfig.fromJson(json);
            getPrefs().edit().putString(KEY_LATEST_GOOD_JSON, json).apply();
            Log.d(TAG, "Loaded remote Mini Drills config: %s", config.contentVersion);
            return config;
        } catch (Exception e) {
            Log.e(TAG, "Remote Mini Drills config failed: %s", e.getMessage());
            return null;
        }
    }

    private MiniDrillConfig loadLatestGood() {
        try {
            String json = getPrefs().getString(KEY_LATEST_GOOD_JSON, null);

            if (json == null || json.trim().isEmpty()) {
                return null;
            }

            MiniDrillConfig config = MiniDrillConfig.fromJson(json);
            Log.d(TAG, "Loaded latest-good Mini Drills config: %s", config.contentVersion);
            return config;
        } catch (Exception e) {
            Log.e(TAG, "Latest-good Mini Drills config failed: %s", e.getMessage());
            return null;
        }
    }

    private MiniDrillConfig loadBundledFallback() {
        try {
            MiniDrillConfig config = MiniDrillConfig.fromJson(readRawResource(R.raw.mini_drills_fallback));
            Log.d(TAG, "Loaded bundled Mini Drills config: %s", config.contentVersion);
            return config;
        } catch (Exception e) {
            throw new IllegalStateException("Bundled Mini Drills config is invalid: " + e.getMessage(), e);
        }
    }

    private String fetchJson(String url) throws Exception {
        try (Response response = OkHttpManager.instance().doGetRequest(url)) {
            if (response == null) {
                throw new IllegalStateException("empty response");
            }

            ResponseBody body = response.body();

            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP " + response.code());
            }

            if (body == null) {
                throw new IllegalStateException("empty body");
            }

            return body.string();
        }
    }

    private String readRawResource(int resId) throws Exception {
        StringBuilder builder = new StringBuilder();

        try (InputStream input = mContext.getResources().openRawResource(resId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }

        return builder.toString();
    }

    private SharedPreferences getPrefs() {
        return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String addCacheBust(String url) {
        return url + (url.contains("?") ? "&" : "?") + "bust=" + System.currentTimeMillis();
    }
}
