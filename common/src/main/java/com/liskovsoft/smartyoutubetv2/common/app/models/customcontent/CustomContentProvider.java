package com.liskovsoft.smartyoutubetv2.common.app.models.customcontent;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CustomContentProvider {
    private static final String CUSTOM_VIDEOS_URL =
            "https://gist.githubusercontent.com/ultrox/4f5acc32de839d1023a8c8969700a5f4/raw/videos.json";
    private static final String ROW_ID = "tv_tube_custom_hls_row";
    private static final String ROW_TITLE = "Custom videos";
    private static final String SUBTITLE = "Custom video";

    public Observable<List<VideoGroup>> getRowsObserve(BrowseSection section) {
        return RxHelper.fromCallable(() -> createRows(section));
    }

    private List<VideoGroup> createRows(BrowseSection section) {
        if (section == null || section.getId() != MediaGroup.TYPE_HOME) {
            return Collections.emptyList();
        }

        List<VideoGroup> rows = new ArrayList<>();
        CustomSection customSection = new CustomSection(ROW_ID, ROW_TITLE, 0, fetchCustomVideos());

        rows.add(customSection.toVideoGroup(section));

        return rows;
    }

    private List<CustomVideo> fetchCustomVideos() {
        String json = fetchJson(addCacheBust(CUSTOM_VIDEOS_URL));
        return parseCustomVideos(json);
    }

    private String fetchJson(String url) {
        try (Response response = OkHttpManager.instance().doGetRequest(url)) {
            if (response == null) {
                throw new IllegalStateException("Custom videos request failed: empty response");
            }

            ResponseBody body = response.body();

            if (!response.isSuccessful()) {
                throw new IllegalStateException(String.format("Custom videos request failed: HTTP %s", response.code()));
            }

            if (body == null) {
                throw new IllegalStateException("Custom videos request failed: empty body");
            }

            return body.string();
        } catch (Exception e) {
            throw new IllegalStateException("Custom videos request failed: " + e.getMessage(), e);
        }
    }

    private List<CustomVideo> parseCustomVideos(String json) {
        try {
            JSONArray items = new JSONArray(json);

            if (items.length() == 0) {
                throw new JSONException("root array is empty");
            }

            List<CustomVideo> videos = new ArrayList<>();

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String title = requireString(item, "title", i);
                String src = requireString(item, "src", i);
                String thumbnailUrl = firstNonEmpty(
                        item.optString("thumbnailUrl", null),
                        item.optString("thumbnail", null),
                        item.optString("thumb", null),
                        deriveThumbnailUrl(src));

                if (!Helpers.isValidUrl(src)) {
                    throw new JSONException(String.format("item %s field 'src' is not a URL", i));
                }

                if (thumbnailUrl != null && !Helpers.isValidUrl(thumbnailUrl)) {
                    throw new JSONException(String.format("item %s thumbnail field is not a URL", i));
                }

                videos.add(new CustomVideo(createVideoId(title, src), title, SUBTITLE, src, thumbnailUrl));
            }

            return videos;
        } catch (JSONException e) {
            throw new IllegalStateException("Custom videos JSON is invalid: " + e.getMessage(), e);
        }
    }

    private String addCacheBust(String url) {
        return String.format("%s?bust=%s", url, System.currentTimeMillis());
    }

    private String requireString(JSONObject item, String key, int index) throws JSONException {
        String value = item.optString(key, null);

        if (value == null || value.trim().isEmpty()) {
            throw new JSONException(String.format("item %s missing non-empty '%s'", index, key));
        }

        return value.trim();
    }

    private String createVideoId(String title, String src) {
        return String.format("tv_tube_custom_%s", Helpers.hashCodeAny(title, src));
    }

    private String deriveThumbnailUrl(String src) {
        String marker = "playlist.m3u8";
        int index = src != null ? src.indexOf(marker) : -1;

        if (index == -1) {
            return null;
        }

        return src.substring(0, index) + "thumbnail.jpg";
    }

    private String firstNonEmpty(String... items) {
        if (items == null) {
            return null;
        }

        for (String item : items) {
            if (item != null && !item.trim().isEmpty()) {
                return item.trim();
            }
        }

        return null;
    }

    private static class CustomSection {
        private final String mId;
        private final String mTitle;
        private final int mPosition;
        private final List<CustomVideo> mVideos;

        private CustomSection(String id, String title, int position, List<CustomVideo> videos) {
            mId = id;
            mTitle = title;
            mPosition = position;
            mVideos = videos;
        }

        private VideoGroup toVideoGroup(BrowseSection section) {
            List<Video> videos = new ArrayList<>();

            for (CustomVideo customVideo : mVideos) {
                videos.add(customVideo.toVideo());
            }

            VideoGroup videoGroup = VideoGroup.from(videos, section);
            videoGroup.setId(mId.hashCode());
            videoGroup.setTitle(mTitle);
            videoGroup.setPosition(mPosition);
            return videoGroup;
        }
    }

    private static class CustomVideo {
        private final String mId;
        private final String mTitle;
        private final String mSubtitle;
        private final String mHlsManifestUrl;
        private final String mThumbnailUrl;

        private CustomVideo(String id, String title, String subtitle, String hlsManifestUrl, String thumbnailUrl) {
            mId = id;
            mTitle = title;
            mSubtitle = subtitle;
            mHlsManifestUrl = hlsManifestUrl;
            mThumbnailUrl = thumbnailUrl;
        }

        private Video toVideo() {
            Video video = new Video();
            video.videoId = mId;
            video.hlsManifestUrl = mHlsManifestUrl;
            video.title = mTitle;
            video.secondTitle = mSubtitle;
            video.author = mSubtitle;
            video.cardImageUrl = mThumbnailUrl;
            video.bgImageUrl = mThumbnailUrl;
            return video;
        }
    }
}
