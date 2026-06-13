package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MiniDrillCard {
    public final String id;
    public final boolean enabled;
    public final String type;
    public final String difficulty;
    public final List<String> tags;
    public final Prompt prompt;
    public final Hint hint;
    public final List<Answer> answers;

    public MiniDrillCard(String id, boolean enabled, String type, String difficulty, List<String> tags, Prompt prompt, Hint hint, List<Answer> answers) {
        this.id = id;
        this.enabled = enabled;
        this.type = type;
        this.difficulty = difficulty;
        this.tags = tags != null ? tags : Collections.emptyList();
        this.prompt = prompt;
        this.hint = hint;
        this.answers = answers != null ? answers : Collections.emptyList();
    }

    public static MiniDrillCard fromJson(JSONObject object, int index) throws JSONException {
        String id = requireString(object, "id", index);
        Prompt prompt = Prompt.fromJson(object.getJSONObject("prompt"), index);
        JSONArray answersJson = object.getJSONArray("answers");
        List<Answer> answers = new ArrayList<>();

        if (answersJson.length() == 0) {
            throw new JSONException("card " + index + " has no answers");
        }

        for (int i = 0; i < answersJson.length(); i++) {
            answers.add(Answer.fromJson(answersJson.getJSONObject(i), index, i));
        }

        return new MiniDrillCard(
                id,
                object.optBoolean("enabled", true),
                object.optString("type", "translation"),
                object.optString("difficulty", "normal"),
                readStringList(object.optJSONArray("tags")),
                prompt,
                Hint.fromJson(object.optJSONObject("hint")),
                answers);
    }

    public Answer getPreferredAnswer() {
        for (Answer answer : answers) {
            if (answer.preferred) {
                return answer;
            }
        }

        return answers.isEmpty() ? null : answers.get(0);
    }

    public String getPromptText() {
        return prompt != null ? prompt.text : "";
    }

    public String getPromptPrefix() {
        return prompt != null && prompt.displayPrefix != null ? prompt.displayPrefix : "Mini Drill";
    }

    public String getAnswerText() {
        StringBuilder builder = new StringBuilder();

        for (Answer answer : answers) {
            if (builder.length() > 0) {
                builder.append("\n");
            }

            builder.append(answer.text);
        }

        return builder.toString();
    }

    private static String requireString(JSONObject object, String key, int index) throws JSONException {
        String value = object.optString(key, null);

        if (value == null || value.trim().isEmpty()) {
            throw new JSONException("card " + index + " missing non-empty '" + key + "'");
        }

        return value.trim();
    }

    private static List<String> readStringList(JSONArray array) throws JSONException {
        if (array == null) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            result.add(array.getString(i));
        }

        return result;
    }

    public static class Prompt {
        public final String language;
        public final String text;
        public final String displayPrefix;
        public final MiniDrillAudio audio;

        private Prompt(String language, String text, String displayPrefix, MiniDrillAudio audio) {
            this.language = language;
            this.text = text;
            this.displayPrefix = displayPrefix;
            this.audio = audio;
        }

        private static Prompt fromJson(JSONObject object, int index) throws JSONException {
            return new Prompt(
                    object.optString("language", null),
                    requireString(object, "text", index),
                    object.optString("displayPrefix", "Kako kazeš?"),
                    MiniDrillAudio.fromJson(object.optJSONObject("audio")));
        }
    }

    public static class Hint {
        public final boolean enabled;
        public final String text;

        private Hint(boolean enabled, String text) {
            this.enabled = enabled;
            this.text = text;
        }

        private static Hint fromJson(JSONObject object) {
            if (object == null) {
                return new Hint(false, null);
            }

            return new Hint(object.optBoolean("enabled", false), object.optString("text", null));
        }
    }

    public static class Answer {
        public final String text;
        public final String language;
        public final boolean preferred;
        public final MiniDrillAudio audio;

        private Answer(String text, String language, boolean preferred, MiniDrillAudio audio) {
            this.text = text;
            this.language = language;
            this.preferred = preferred;
            this.audio = audio;
        }

        private static Answer fromJson(JSONObject object, int cardIndex, int answerIndex) throws JSONException {
            String text = object.optString("text", null);

            if (text == null || text.trim().isEmpty()) {
                throw new JSONException("card " + cardIndex + " answer " + answerIndex + " missing non-empty text");
            }

            return new Answer(
                    text.trim(),
                    object.optString("language", null),
                    object.optBoolean("preferred", false),
                    MiniDrillAudio.fromJson(object.optJSONObject("audio")));
        }
    }
}
