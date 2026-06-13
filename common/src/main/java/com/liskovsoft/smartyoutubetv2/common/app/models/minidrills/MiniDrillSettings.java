package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

import android.content.Context;
import android.content.SharedPreferences;

public class MiniDrillSettings {
    public static final String ENABLED_REMOTE = "remote";
    public static final String ENABLED_ON = "on";
    public static final String ENABLED_OFF = "off";

    public static final String FREQUENCY_REMOTE = "remote";
    public static final String FREQUENCY_OFF = "off";
    public static final String FREQUENCY_LOW = "low";
    public static final String FREQUENCY_NORMAL = "normal";
    public static final String FREQUENCY_HIGH = "high";

    private static final String PREFS_NAME = "mini_drill_settings";
    private static final String KEY_ENABLED_OVERRIDE = "enabled_override";
    private static final String KEY_FREQUENCY_OVERRIDE = "frequency_override";

    private static MiniDrillSettings sInstance;

    private final SharedPreferences mPrefs;

    private MiniDrillSettings(Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static MiniDrillSettings instance(Context context) {
        if (sInstance == null) {
            sInstance = new MiniDrillSettings(context);
        }

        return sInstance;
    }

    public String getEnabledOverride() {
        return mPrefs.getString(KEY_ENABLED_OVERRIDE, ENABLED_REMOTE);
    }

    public void setEnabledOverride(String mode) {
        mPrefs.edit().putString(KEY_ENABLED_OVERRIDE, normalizeEnabled(mode)).apply();
    }

    public String getFrequencyOverride() {
        return mPrefs.getString(KEY_FREQUENCY_OVERRIDE, FREQUENCY_REMOTE);
    }

    public void setFrequencyOverride(String mode) {
        mPrefs.edit().putString(KEY_FREQUENCY_OVERRIDE, normalizeFrequency(mode)).apply();
    }

    public void resetOverrides() {
        mPrefs.edit()
                .putString(KEY_ENABLED_OVERRIDE, ENABLED_REMOTE)
                .putString(KEY_FREQUENCY_OVERRIDE, FREQUENCY_REMOTE)
                .apply();
    }

    public boolean isEnabled(MiniDrillConfig config) {
        String mode = getEnabledOverride();

        if (ENABLED_ON.equals(mode)) {
            return true;
        }

        if (ENABLED_OFF.equals(mode)) {
            return false;
        }

        return config != null && config.enabled;
    }

    public String getEffectiveFrequencyMode(MiniDrillConfig config) {
        String override = getFrequencyOverride();

        if (!FREQUENCY_REMOTE.equals(override)) {
            return override;
        }

        return config != null && config.frequency != null ? config.frequency.mode : FREQUENCY_NORMAL;
    }

    public int getEffectiveOverlayIntervalSeconds(MiniDrillConfig config) {
        String override = getFrequencyOverride();

        if (!FREQUENCY_REMOTE.equals(override)) {
            return defaultIntervalSeconds(override);
        }

        return config != null && config.frequency != null ? config.frequency.intervalSeconds : defaultIntervalSeconds(FREQUENCY_NORMAL);
    }

    public boolean isEffectiveOverlayFrequencyEnabled(MiniDrillConfig config) {
        String mode = getEffectiveFrequencyMode(config);
        return !FREQUENCY_OFF.equals(mode) && getEffectiveOverlayIntervalSeconds(config) > 0;
    }

    public static int defaultIntervalSeconds(String mode) {
        if (FREQUENCY_LOW.equals(mode)) {
            return 540;
        }

        if (FREQUENCY_HIGH.equals(mode)) {
            return 150;
        }

        if (FREQUENCY_OFF.equals(mode)) {
            return 0;
        }

        return 300;
    }

    private String normalizeEnabled(String mode) {
        if (ENABLED_ON.equals(mode) || ENABLED_OFF.equals(mode)) {
            return mode;
        }

        return ENABLED_REMOTE;
    }

    private String normalizeFrequency(String mode) {
        if (FREQUENCY_OFF.equals(mode) || FREQUENCY_LOW.equals(mode) || FREQUENCY_NORMAL.equals(mode) || FREQUENCY_HIGH.equals(mode)) {
            return mode;
        }

        return FREQUENCY_REMOTE;
    }
}
