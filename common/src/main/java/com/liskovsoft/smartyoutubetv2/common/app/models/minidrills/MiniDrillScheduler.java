package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

public class MiniDrillScheduler {
    public enum Decision {
        SHOW_OVERLAY,
        SHOW_PAUSE_CARD,
        SHOW_END_REVIEW,
        SHOW_NOTHING
    }

    public Decision evaluate(Inputs inputs) {
        return evaluateStatus(inputs).decision;
    }

    public Status evaluateStatus(Inputs inputs) {
        Status status = new Status();
        status.decision = Decision.SHOW_NOTHING;

        if (inputs == null || inputs.config == null || !inputs.miniDrillsEnabled) {
            status.reason = inputs == null || inputs.config == null ? "No config loaded" : "Mini Drills disabled";
            return status;
        }

        if (inputs.videoEnded && inputs.config.endReview.enabled && inputs.hasPendingReview) {
            status.decision = Decision.SHOW_END_REVIEW;
            status.reason = "End review ready";
            return status;
        }

        if (inputs.videoPaused && inputs.config.pauseCard.enabled && !inputs.modalVisible) {
            status.decision = Decision.SHOW_PAUSE_CARD;
            status.reason = "Pause card ready";
            return status;
        }

        if (!inputs.videoPlaying) {
            status.reason = "Video is not playing";
            return status;
        }

        if (!inputs.config.playbackOverlay.enabled) {
            status.reason = "Playback overlay disabled";
            return status;
        }

        if (!inputs.overlayFrequencyEnabled) {
            status.reason = "Playback overlay frequency off";
            return status;
        }

        if (inputs.nextOverlayDueTimeMs <= 0) {
            status.reason = "No scheduled card";
            return status;
        }

        long nextEligibleTimeMs = Math.max(inputs.nextOverlayDueTimeMs, inputs.nextAllowedOverlayTimeMs);
        status.nextEligibleTimeMs = nextEligibleTimeMs;
        status.millisecondsUntilEligible = Math.max(0, status.nextEligibleTimeMs - inputs.currentTimeMs);

        if (status.millisecondsUntilEligible > 0) {
            status.reason = inputs.nextAllowedOverlayTimeMs >= inputs.nextOverlayDueTimeMs ?
                    "Waiting for cooldown" : "Waiting for clock";
            return status;
        }

        if (inputs.overlayWindowEndPlaybackSeconds < 0) {
            status.reason = "No valid overlay window";
            return status;
        }

        if (inputs.playbackSeconds < inputs.overlayWindowStartPlaybackSeconds) {
            status.reason = "Waiting for video start window";
            return status;
        }

        if (inputs.overlayWindowEndPlaybackSeconds > 0 && inputs.playbackSeconds > inputs.overlayWindowEndPlaybackSeconds) {
            status.reason = "Past overlay window";
            return status;
        }

        if (inputs.overlayVisible || inputs.modalVisible || inputs.overlayCardsShown >= inputs.config.frequency.maxOverlayCardsPerVideo) {
            if (inputs.overlayVisible) {
                status.reason = "Blocked by player UI";
            } else if (inputs.modalVisible) {
                status.reason = "Blocked by open dialog";
            } else {
                status.reason = "Card cap reached";
            }
            return status;
        }

        status.decision = Decision.SHOW_OVERLAY;
        status.reason = "Ready";
        return status;
    }

    public static class Status {
        public Decision decision;
        public String reason;
        public long nextEligibleTimeMs;
        public long millisecondsUntilEligible;
    }

    public static class Inputs {
        public MiniDrillConfig config;
        public long playbackSeconds;
        public boolean miniDrillsEnabled;
        public boolean overlayFrequencyEnabled;
        public boolean videoPlaying;
        public boolean videoPaused;
        public boolean videoEnded;
        public boolean overlayVisible;
        public boolean modalVisible;
        public boolean hasPendingReview;
        public int overlayCardsShown;
        public long currentTimeMs;
        public long nextOverlayDueTimeMs;
        public long nextAllowedOverlayTimeMs;
        public long overlayWindowStartPlaybackSeconds;
        public long overlayWindowEndPlaybackSeconds;
    }
}
