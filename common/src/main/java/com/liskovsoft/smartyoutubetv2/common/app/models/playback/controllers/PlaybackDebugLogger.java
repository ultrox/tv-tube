package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.net.Uri;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PlaybackDebugLogger {
    private static final String TAG = "PlaybackDebug";
    private static final int MAX_FORMATS_TO_LOG = 30;

    private PlaybackDebugLogger() {
    }

    static void logFormatInfo(MediaItemFormatInfo formatInfo) {
        if (!BuildConfig.DEBUG || formatInfo == null) {
            return;
        }

        Log.d(TAG,
                "Resolved playback info: videoId=%s, title=%s, author=%s, client=%s, auth=%s, live=%s, unplayable=%s, " +
                        "dashFormats=%s, sabrFormats=%s, hlsUrl=%s, dashUrl=%s, regularFormats=%s",
                safe(formatInfo.getVideoId()),
                safe(formatInfo.getTitle()),
                safe(formatInfo.getAuthor()),
                clientName(formatInfo),
                formatInfo.isAuth(),
                formatInfo.isLive(),
                formatInfo.isUnplayable(),
                formatInfo.containsDashFormats(),
                formatInfo.containsSabrFormats(),
                formatInfo.containsHlsUrl(),
                formatInfo.containsDashUrl(),
                formatInfo.containsUrlFormats());

        logManifest("hlsManifestUrl", formatInfo.getHlsManifestUrl());
        logManifest("dashManifestUrl", formatInfo.getDashManifestUrl());
        logManifest("serverAbrStreamingUrl", formatInfo.getServerAbrStreamingUrl());
        logFormats("adaptive", formatInfo.getAdaptiveFormats());
        logFormats("regular", formatInfo.getUrlFormats());
    }

    private static void logManifest(String label, String url) {
        if (url == null || url.isEmpty()) {
            return;
        }

        Log.d(TAG, "%s=%s", label, redactUrl(url));
    }

    private static void logFormats(String label, List<MediaFormat> formats) {
        if (formats == null || formats.isEmpty()) {
            Log.d(TAG, "%s formats: none", label);
            return;
        }

        Log.d(TAG, "%s formats: count=%s", label, formats.size());

        int limit = Math.min(formats.size(), MAX_FORMATS_TO_LOG);
        for (int i = 0; i < limit; i++) {
            MediaFormat format = formats.get(i);
            if (format == null) {
                continue;
            }

            Log.d(TAG,
                    "%s[%s]: type=%s, itag=%s, mime=%s, quality=%s, size=%s, fps=%s, bitrate=%s, clen=%s, language=%s, " +
                            "drc=%s, url=%s, sourceUrl=%s, segments=%s, globalSegments=%s",
                    label,
                    i,
                    typeName(format.getFormatType()),
                    safe(format.getITag()),
                    safe(format.getMimeType()),
                    firstNonEmpty(format.getQualityLabel(), format.getQuality()),
                    size(format),
                    safe(format.getFps()),
                    safe(format.getBitrate()),
                    safe(format.getClen()),
                    safe(format.getLanguage()),
                    format.isDrc(),
                    redactUrl(format.getUrl()),
                    redactUrl(format.getSourceUrl()),
                    count(format.getSegmentUrlList()),
                    count(format.getGlobalSegmentList()));
        }

        if (formats.size() > limit) {
            Log.d(TAG, "%s formats: skipped=%s", label, formats.size() - limit);
        }
    }

    private static String redactUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "none";
        }

        try {
            Uri uri = Uri.parse(url);
            StringBuilder result = new StringBuilder();

            if (uri.getScheme() != null) {
                result.append(uri.getScheme()).append("://");
            }

            result.append(safe(uri.getHost()));

            if (uri.getPath() != null) {
                result.append(uri.getPath());
            }

            Set<String> queryNames = uri.getQueryParameterNames();
            if (queryNames != null && !queryNames.isEmpty()) {
                List<String> sortedNames = new ArrayList<>(queryNames);
                Collections.sort(sortedNames);
                List<String> params = new ArrayList<>();
                for (String name : sortedNames) {
                    params.add(name + "=" + safeQueryValue(name, uri.getQueryParameter(name)));
                }
                result.append("?params=").append(params);
            }

            return result.toString();
        } catch (Exception e) {
            return String.format("redacted-url(length=%s)", url.length());
        }
    }

    private static String safeQueryValue(String name, String value) {
        if (value == null) {
            return "none";
        }

        if (isSensitiveQueryKey(name)) {
            return String.format("redacted(len=%s)", value.length());
        }

        String cleaned = value.replace('\r', ' ').replace('\n', ' ');
        return cleaned.length() <= 80 ? cleaned : String.format("%s...(len=%s)", cleaned.substring(0, 80), cleaned.length());
    }

    private static boolean isSensitiveQueryKey(String name) {
        if (name == null) {
            return true;
        }

        String key = name.toLowerCase(Locale.US);

        return key.equals("id") ||
                key.equals("ip") ||
                key.equals("ipbits") ||
                key.equals("n") ||
                key.equals("s") ||
                key.equals("sig") ||
                key.equals("signature") ||
                key.equals("lsig") ||
                key.equals("cpn") ||
                key.equals("pot") ||
                key.equals("po_token") ||
                key.equals("potoken") ||
                key.equals("visitor_data") ||
                key.contains("token") ||
                key.contains("auth") ||
                key.contains("cookie") ||
                key.contains("session");
    }

    private static String clientName(MediaItemFormatInfo formatInfo) {
        MediaItemFormatInfo.ClientInfo clientInfo = formatInfo.getClientInfo();
        if (clientInfo == null) {
            return "none";
        }

        return String.format("%s/%s", safe(clientInfo.getClientName()), safe(clientInfo.getClientVersion()));
    }

    private static String typeName(int type) {
        switch (type) {
            case MediaFormat.FORMAT_TYPE_DASH:
                return "dash";
            case MediaFormat.FORMAT_TYPE_REGULAR:
                return "regular";
            case MediaFormat.FORMAT_TYPE_SABR:
                return "sabr";
            default:
                return String.format("unknown(%s)", type);
        }
    }

    private static String size(MediaFormat format) {
        int width = format.getWidth();
        int height = format.getHeight();
        return width > 0 || height > 0 ? String.format("%sx%s", width, height) : "none";
    }

    private static int count(List<?> list) {
        return list != null ? list.size() : 0;
    }

    private static String firstNonEmpty(String first, String second) {
        return first != null && !first.isEmpty() ? first : safe(second);
    }

    private static String safe(String value) {
        return value != null && !value.isEmpty() ? value : "none";
    }
}
