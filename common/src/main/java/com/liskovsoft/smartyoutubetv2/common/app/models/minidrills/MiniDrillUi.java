package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

public interface MiniDrillUi {
    void showMiniDrillOverlay(MiniDrillCard card, boolean revealed, Callback callback, int timeoutMs);
    void dismissMiniDrillOverlay();
    boolean isMiniDrillOverlayShown();

    interface Callback {
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
