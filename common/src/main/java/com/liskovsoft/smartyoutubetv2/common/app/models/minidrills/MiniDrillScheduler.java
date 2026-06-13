package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

public class MiniDrillScheduler {
    public enum Decision {
        SHOW_OVERLAY,
        SHOW_PAUSE_CARD,
        SHOW_END_REVIEW,
        SHOW_NOTHING
    }

    public Decision evaluate(Inputs inputs) {
        if (inputs == null || inputs.config == null || !inputs.config.enabled) {
            return Decision.SHOW_NOTHING;
        }

        if (inputs.videoEnded && inputs.config.endReview.enabled && inputs.hasPendingReview) {
            return Decision.SHOW_END_REVIEW;
        }

        if (inputs.videoPaused && inputs.config.pauseCard.enabled && !inputs.modalVisible) {
            return Decision.SHOW_PAUSE_CARD;
        }

        if (!inputs.videoPlaying || !inputs.config.playbackOverlay.enabled || !inputs.config.frequency.isOverlayEnabled()) {
            return Decision.SHOW_NOTHING;
        }

        if (inputs.overlayVisible || inputs.modalVisible || inputs.overlayCardsShown >= inputs.config.frequency.maxOverlayCardsPerVideo) {
            return Decision.SHOW_NOTHING;
        }

        int intervalSeconds = Math.max(inputs.config.frequency.intervalSeconds, inputs.config.frequency.minimumOverlayIntervalSeconds);

        if (inputs.playbackSeconds - inputs.lastOverlayPlaybackSeconds < intervalSeconds) {
            return Decision.SHOW_NOTHING;
        }

        if (inputs.playbackSeconds < inputs.nextAllowedOverlayPlaybackSeconds) {
            return Decision.SHOW_NOTHING;
        }

        return Decision.SHOW_OVERLAY;
    }

    public static class Inputs {
        public MiniDrillConfig config;
        public long playbackSeconds;
        public boolean videoPlaying;
        public boolean videoPaused;
        public boolean videoEnded;
        public boolean overlayVisible;
        public boolean modalVisible;
        public boolean hasPendingReview;
        public int overlayCardsShown;
        public long lastOverlayPlaybackSeconds;
        public long nextAllowedOverlayPlaybackSeconds;
    }
}
