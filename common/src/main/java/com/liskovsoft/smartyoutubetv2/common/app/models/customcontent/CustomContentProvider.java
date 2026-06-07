package com.liskovsoft.smartyoutubetv2.common.app.models.customcontent;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;

public class CustomContentProvider {
    private static final CustomSection STATIC_HOME_SECTION = new CustomSection(
            "tv_tube_custom_hls_row",
            MediaGroup.TYPE_HOME,
            "Custom videos",
            0,
            Arrays.asList(
                    new CustomVideo(
                            "tv_tube_custom_hls_video",
                            "Custom HLS Video",
                            "Custom video",
                            "https://vz-706ad3a6-5f5.b-cdn.net/4a40b7f8-6ec7-432e-9eac-4cc76ba8d450/playlist.m3u8",
                            "https://vz-706ad3a6-5f5.b-cdn.net/4a40b7f8-6ec7-432e-9eac-4cc76ba8d450/thumbnail.jpg"),
                    new CustomVideo(
                            "tv_tube_puss_in_boots_last_wish",
                            "Der.Gestiefelte.Kater.Der.Letzte.Wunsch",
                            "Custom video",
                            "https://vz-706ad3a6-5f5.b-cdn.net/0ad0bf26-bf7e-416a-9694-53b7f61a2916/playlist.m3u8",
                            "https://vz-706ad3a6-5f5.b-cdn.net/0ad0bf26-bf7e-416a-9694-53b7f61a2916/thumbnail_ec6c64a7.jpg"),
                    new CustomVideo(
                            "tv_tube_ratatouille",
                            "Ratatouille",
                            "Custom video",
                            "https://vz-706ad3a6-5f5.b-cdn.net/4e7d0911-e0af-4dba-98f3-ec59a2bb3f96/playlist.m3u8",
                            "https://vz-706ad3a6-5f5.b-cdn.net/4e7d0911-e0af-4dba-98f3-ec59a2bb3f96/thumbnail.jpg"),
                    new CustomVideo(
                            "tv_tube_beear",
                            "Beear",
                            "Custom video",
                            "https://vz-706ad3a6-5f5.b-cdn.net/c830d21f-91a6-423a-a3f9-a6a723c31616/playlist.m3u8",
                            "https://vz-706ad3a6-5f5.b-cdn.net/c830d21f-91a6-423a-a3f9-a6a723c31616/thumbnail_799fef13.jpg")));

    public Observable<List<VideoGroup>> getRowsObserve(BrowseSection section) {
        return Observable.fromCallable(() -> createRows(section));
    }

    private List<VideoGroup> createRows(BrowseSection section) {
        if (section == null) {
            return Collections.emptyList();
        }

        List<VideoGroup> rows = new ArrayList<>();

        for (CustomSection customSection : getCustomSections()) {
            if (customSection.belongsTo(section)) {
                rows.add(customSection.toVideoGroup(section));
            }
        }

        return rows;
    }

    private List<CustomSection> getCustomSections() {
        return Collections.singletonList(STATIC_HOME_SECTION);
    }

    private static class CustomSection {
        private final String mId;
        private final int mBrowseSectionId;
        private final String mTitle;
        private final int mPosition;
        private final List<CustomVideo> mVideos;

        private CustomSection(String id, int browseSectionId, String title, int position, List<CustomVideo> videos) {
            mId = id;
            mBrowseSectionId = browseSectionId;
            mTitle = title;
            mPosition = position;
            mVideos = videos;
        }

        private boolean belongsTo(BrowseSection section) {
            return section.getId() == mBrowseSectionId;
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
