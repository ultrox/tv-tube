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
    private static final String KEY_LAST_SOURCE = "last_source";
    private static final String KEY_LAST_FETCH_TIME_MS = "last_fetch_time_ms";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String SOURCE_REMOTE = "remote";
    private static final String SOURCE_LATEST_GOOD = "latest-good";
    private static final String SOURCE_BUNDLED = "bundled";
    private static final String SOURCE_UNKNOWN = "unknown";

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

    public Observable<MiniDrillConfig> refreshObserve() {
        return RxHelper.fromCallable(this::refresh);
    }

    public MiniDrillConfig refresh() {
        sLatestConfig = null;
        return loadFresh();
    }

    public MiniDrillConfig load() {
        if (sLatestConfig != null) {
            return sLatestConfig;
        }

        return loadFresh();
    }

    public Status getStatus() {
        SharedPreferences prefs = getPrefs();
        MiniDrillConfig config = sLatestConfig;
        return new Status(
                prefs.getString(KEY_LAST_SOURCE, SOURCE_UNKNOWN),
                config != null ? config.contentVersion : null,
                config != null && config.cards != null ? config.cards.size() : -1,
                prefs.getLong(KEY_LAST_FETCH_TIME_MS, 0),
                prefs.getString(KEY_LAST_ERROR, null));
    }

    private MiniDrillConfig loadFresh() {
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
            getPrefs().edit()
                    .putString(KEY_LATEST_GOOD_JSON, json)
                    .putString(KEY_LAST_SOURCE, SOURCE_REMOTE)
                    .putLong(KEY_LAST_FETCH_TIME_MS, System.currentTimeMillis())
                    .remove(KEY_LAST_ERROR)
                    .apply();
            Log.d(TAG, "Loaded remote Mini Drills config: %s", config.contentVersion);
            return config;
        } catch (Exception e) {
            getPrefs().edit()
                    .putLong(KEY_LAST_FETCH_TIME_MS, System.currentTimeMillis())
                    .putString(KEY_LAST_ERROR, e.getMessage())
                    .apply();
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
            getPrefs().edit().putString(KEY_LAST_SOURCE, SOURCE_LATEST_GOOD).apply();
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
            getPrefs().edit().putString(KEY_LAST_SOURCE, SOURCE_BUNDLED).apply();
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

    public static class Status {
        public final String source;
        public final String contentVersion;
        public final int cardCount;
        public final long lastFetchTimeMs;
        public final String lastError;

        private Status(String source, String contentVersion, int cardCount, long lastFetchTimeMs, String lastError) {
            this.source = source;
            this.contentVersion = contentVersion;
            this.cardCount = cardCount;
            this.lastFetchTimeMs = lastFetchTimeMs;
            this.lastError = lastError;
        }
    }

    private SharedPreferences getPrefs() {
        return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private String addCacheBust(String url) {
        return url + (url.contains("?") ? "&" : "?") + "bust=" + System.currentTimeMillis();
    }
}
