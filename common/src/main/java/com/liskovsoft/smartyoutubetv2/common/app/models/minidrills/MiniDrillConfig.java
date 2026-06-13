package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MiniDrillConfig {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public final int schemaVersion;
    public final String contentVersion;
    public final boolean enabled;
    public final String displayName;
    public final SurfaceConfig playbackOverlay;
    public final SurfaceConfig pauseCard;
    public final SurfaceConfig endReview;
    public final FrequencyConfig frequency;
    public final AudioConfig audio;
    public final List<MiniDrillCard> cards;

    private MiniDrillConfig(int schemaVersion, String contentVersion, boolean enabled, String displayName, SurfaceConfig playbackOverlay,
            SurfaceConfig pauseCard, SurfaceConfig endReview, FrequencyConfig frequency, AudioConfig audio, List<MiniDrillCard> cards) {
        this.schemaVersion = schemaVersion;
        this.contentVersion = contentVersion;
        this.enabled = enabled;
        this.displayName = displayName;
        this.playbackOverlay = playbackOverlay;
        this.pauseCard = pauseCard;
        this.endReview = endReview;
        this.frequency = frequency;
        this.audio = audio;
        this.cards = cards != null ? cards : Collections.emptyList();
    }

    public static MiniDrillConfig fromJson(String json) {
        try {
            JSONObject root = new JSONObject(json);

            if (!root.has("schemaVersion")) {
                throw new JSONException("missing schemaVersion");
            }

            int schemaVersion = root.getInt("schemaVersion");

            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                throw new JSONException("unsupported schemaVersion " + schemaVersion);
            }

            JSONObject miniDrills = root.getJSONObject("miniDrills");

            if (!miniDrills.has("enabled")) {
                throw new JSONException("missing miniDrills.enabled");
            }

            JSONArray cardsJson = root.getJSONArray("cards");
            List<MiniDrillCard> cards = new ArrayList<>();

            for (int i = 0; i < cardsJson.length(); i++) {
                cards.add(MiniDrillCard.fromJson(cardsJson.getJSONObject(i), i));
            }

            if (cards.isEmpty()) {
                throw new JSONException("cards array is empty");
            }

            JSONObject surfaces = miniDrills.optJSONObject("surfaces");

            return new MiniDrillConfig(
                    schemaVersion,
                    root.optString("contentVersion", null),
                    miniDrills.getBoolean("enabled"),
                    miniDrills.optString("displayName", "Mini Drills"),
                    SurfaceConfig.fromJson(getObject(surfaces, "playbackOverlay"), true, 8),
                    SurfaceConfig.fromJson(getObject(surfaces, "pauseCard"), true, 0),
                    SurfaceConfig.fromJson(getObject(surfaces, "endReview"), true, 0),
                    FrequencyConfig.fromJson(miniDrills.optJSONObject("frequency")),
                    AudioConfig.fromJson(miniDrills.optJSONObject("audio")),
                    cards);
        } catch (JSONException e) {
            throw new IllegalStateException("Mini Drills JSON is invalid: " + e.getMessage(), e);
        }
    }

    private static JSONObject getObject(JSONObject parent, String key) {
        return parent != null ? parent.optJSONObject(key) : null;
    }

    public static class SurfaceConfig {
        public final boolean enabled;
        public final int visibleSeconds;
        public final boolean autoPlayAnswerAfterReveal;
        public final boolean duckVideoAudioWhileSpeaking;

        private SurfaceConfig(boolean enabled, int visibleSeconds, boolean autoPlayAnswerAfterReveal, boolean duckVideoAudioWhileSpeaking) {
            this.enabled = enabled;
            this.visibleSeconds = visibleSeconds;
            this.autoPlayAnswerAfterReveal = autoPlayAnswerAfterReveal;
            this.duckVideoAudioWhileSpeaking = duckVideoAudioWhileSpeaking;
        }

        private static SurfaceConfig fromJson(JSONObject object, boolean defaultEnabled, int defaultVisibleSeconds) {
            return new SurfaceConfig(
                    object != null ? object.optBoolean("enabled", defaultEnabled) : defaultEnabled,
                    object != null ? object.optInt("visibleSeconds", defaultVisibleSeconds) : defaultVisibleSeconds,
                    true,
                    false);
        }

        private SurfaceConfig withAudio(JSONObject object) {
            return new SurfaceConfig(
                    enabled,
                    visibleSeconds,
                    object != null ? object.optBoolean("autoPlayAnswerAfterReveal", autoPlayAnswerAfterReveal) : autoPlayAnswerAfterReveal,
                    object != null ? object.optBoolean("duckVideoAudioWhileSpeaking", duckVideoAudioWhileSpeaking) : duckVideoAudioWhileSpeaking);
        }
    }

    public static class FrequencyConfig {
        public final String mode;
        public final int intervalSeconds;
        public final int minimumOverlayIntervalSeconds;
        public final int maxOverlayCardsPerVideo;
        public final int cooldownAfterDismissSeconds;
        public final int cooldownAfterAnnoyingSeconds;

        private FrequencyConfig(String mode, int intervalSeconds, int minimumOverlayIntervalSeconds, int maxOverlayCardsPerVideo,
                int cooldownAfterDismissSeconds, int cooldownAfterAnnoyingSeconds) {
            this.mode = mode;
            this.intervalSeconds = intervalSeconds;
            this.minimumOverlayIntervalSeconds = minimumOverlayIntervalSeconds;
            this.maxOverlayCardsPerVideo = maxOverlayCardsPerVideo;
            this.cooldownAfterDismissSeconds = cooldownAfterDismissSeconds;
            this.cooldownAfterAnnoyingSeconds = cooldownAfterAnnoyingSeconds;
        }

        private static FrequencyConfig fromJson(JSONObject object) {
            String mode = object != null ? object.optString("mode", "normal") : "normal";
            JSONObject modes = object != null ? object.optJSONObject("modes") : null;
            int defaultInterval = defaultInterval(mode);
            int interval = intervalFromModes(modes, mode, defaultInterval);

            return new FrequencyConfig(
                    mode,
                    interval,
                    object != null ? object.optInt("minimumOverlayIntervalSeconds", 0) : 0,
                    object != null ? object.optInt("maxOverlayCardsPerVideo", 12) : 12,
                    object != null ? object.optInt("cooldownAfterDismissSeconds", 45) : 45,
                    object != null ? object.optInt("cooldownAfterAnnoyingSeconds", 900) : 900);
        }

        public boolean isOverlayEnabled() {
            return !"off".equals(mode) && intervalSeconds > 0;
        }

        private static int intervalFromModes(JSONObject modes, String mode, int defaultInterval) {
            JSONObject modeConfig = modes != null ? modes.optJSONObject(mode) : null;

            if (modeConfig == null || !modeConfig.optBoolean("enabled", !"off".equals(mode))) {
                return 0;
            }

            return modeConfig.optInt("overlayIntervalSeconds", defaultInterval);
        }

        private static int defaultInterval(String mode) {
            if ("low".equals(mode)) {
                return 540;
            } else if ("high".equals(mode)) {
                return 150;
            } else if ("custom".equals(mode)) {
                return 240;
            } else if ("off".equals(mode)) {
                return 0;
            }

            return 300;
        }
    }

    public static class AudioConfig {
        public final boolean enabled;
        public final SurfaceConfig playbackOverlay;
        public final SurfaceConfig pauseCard;
        public final SurfaceConfig endReview;
        public final float videoDuckVolume;
        public final float speechRate;
        public final float pitch;

        private AudioConfig(boolean enabled, SurfaceConfig playbackOverlay, SurfaceConfig pauseCard, SurfaceConfig endReview,
                float videoDuckVolume, float speechRate, float pitch) {
            this.enabled = enabled;
            this.playbackOverlay = playbackOverlay;
            this.pauseCard = pauseCard;
            this.endReview = endReview;
            this.videoDuckVolume = videoDuckVolume;
            this.speechRate = speechRate;
            this.pitch = pitch;
        }

        private static AudioConfig fromJson(JSONObject object) {
            JSONObject ttsFallback = object != null ? object.optJSONObject("ttsFallback") : null;
            JSONObject overlayAudio = getObject(object, "playbackOverlay");

            return new AudioConfig(
                    object == null || object.optBoolean("enabled", true),
                    new SurfaceConfig(true, 8, true, true).withAudio(overlayAudio),
                    new SurfaceConfig(true, 0, true, false).withAudio(getObject(object, "pauseCard")),
                    new SurfaceConfig(true, 0, false, false).withAudio(getObject(object, "endReview")),
                    overlayAudio != null ? (float) overlayAudio.optDouble("videoDuckVolume", 0.35) : 0.35f,
                    ttsFallback != null ? (float) ttsFallback.optDouble("speechRate", 0.9) : 0.9f,
                    ttsFallback != null ? (float) ttsFallback.optDouble("pitch", 1.0) : 1.0f);
        }
    }
}
