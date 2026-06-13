package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

import org.json.JSONObject;

public class MiniDrillAudio {
    public static final String KIND_TTS = "tts";
    public static final String KIND_URL = "url";
    public static final String KIND_NONE = "none";

    public final String kind;
    public final String text;
    public final String language;
    public final String url;
    public final MiniDrillAudio fallback;

    public MiniDrillAudio(String kind, String text, String language, String url, MiniDrillAudio fallback) {
        this.kind = kind != null ? kind : KIND_NONE;
        this.text = text;
        this.language = language;
        this.url = url;
        this.fallback = fallback;
    }

    public static MiniDrillAudio none() {
        return new MiniDrillAudio(KIND_NONE, null, null, null, null);
    }

    public static MiniDrillAudio fromJson(JSONObject object) {
        if (object == null) {
            return none();
        }

        return new MiniDrillAudio(
                object.optString("kind", KIND_NONE),
                object.optString("text", null),
                object.optString("language", null),
                object.optString("url", null),
                fromJson(object.optJSONObject("fallback")));
    }

    public MiniDrillAudio resolveForSpeech() {
        if (KIND_TTS.equals(kind)) {
            return this;
        }

        if (KIND_URL.equals(kind) && fallback != null && KIND_TTS.equals(fallback.kind)) {
            return fallback;
        }

        return none();
    }

    public boolean canSpeak() {
        return KIND_TTS.equals(kind) && text != null && !text.trim().isEmpty();
    }
}
