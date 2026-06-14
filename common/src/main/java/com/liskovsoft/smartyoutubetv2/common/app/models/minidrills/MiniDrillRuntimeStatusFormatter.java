package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

public class MiniDrillRuntimeStatusFormatter {
    private MiniDrillRuntimeStatusFormatter() {
    }

    public static String formatNextCard(MiniDrillController controller) {
        if (controller == null) {
            return "no active playback";
        }

        MiniDrillController.RuntimeStatus runtime = controller.getRuntimeStatus();

        if (!runtime.hasActivePlayer) {
            return "no active playback";
        }

        if (!runtime.configLoaded) {
            return "no config loaded";
        }

        if (!runtime.miniDrillsEnabled) {
            return "disabled";
        }

        if (!runtime.hasCards) {
            return "no cards loaded";
        }

        if (!runtime.hasMiniDrillUi) {
            return "player view unavailable";
        }

        if (!runtime.playbackOverlayEnabled || !runtime.overlayFrequencyEnabled) {
            return "not scheduled";
        }

        if (runtime.maxOverlayCardsPerVideo > 0 && runtime.overlayCardsShown >= runtime.maxOverlayCardsPerVideo) {
            return "card cap reached";
        }

        if (runtime.nextEligiblePlaybackSeconds <= 0) {
            return safe(runtime.reason).toLowerCase();
        }

        String target = formatPlaybackTime(runtime.nextEligiblePlaybackSeconds);

        if (!runtime.videoPlaying) {
            return runtime.secondsUntilNextCard <= 0
                    ? "eligible when playback resumes"
                    : "in " + formatDuration(runtime.secondsUntilNextCard) + " after playback resumes";
        }

        if (runtime.secondsUntilNextCard <= 0) {
            return "eligible now";
        }

        return "in " + formatDuration(runtime.secondsUntilNextCard) + " (at " + target + ")";
    }

    private static String formatDuration(long seconds) {
        seconds = Math.max(0, seconds);
        long hours = seconds / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long remainingSeconds = seconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }

        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        }

        return remainingSeconds + "s";
    }

    private static String formatPlaybackTime(long seconds) {
        seconds = Math.max(0, seconds);
        long hours = seconds / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long remainingSeconds = seconds % 60;

        if (hours > 0) {
            return hours + ":" + twoDigits(minutes) + ":" + twoDigits(remainingSeconds);
        }

        return minutes + ":" + twoDigits(remainingSeconds);
    }

    private static String twoDigits(long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static String safe(String value) {
        return value != null && !value.trim().isEmpty() ? value : "-";
    }
}
