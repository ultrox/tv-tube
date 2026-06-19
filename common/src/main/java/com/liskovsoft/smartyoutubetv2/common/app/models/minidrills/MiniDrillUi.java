package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

public interface MiniDrillUi {
    void showMiniDrillOverlay(MiniDrillCard card, boolean revealed, boolean committed, int committedCount, Callback callback, int timeoutMs);
    void dismissMiniDrillOverlay();
    boolean isMiniDrillOverlayShown();
    boolean isMiniDrillPlaybackBlocked();

    interface Callback {
        void onCommit(int count);
        void onReveal();
        void onLater();
        void onSkip();
        void onEasy();
        void onHard();
        void onAgainLater();
        void onAnnoying();
        void onIgnored();
        void onDismiss();
    }
}
