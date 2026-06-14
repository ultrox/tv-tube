package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;

public class MiniDrillController extends BasePlayerController {
    private static final String TAG = MiniDrillController.class.getSimpleName();
    private static final long PLAYBACK_OVERLAY_CHECK_MS = 1_000;
    private static final int PLAYBACK_OVERLAY_SEEK_COOLDOWN_SECONDS = 15;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPlaybackOverlayCheck = this::runPlaybackOverlayCheck;
    private final MiniDrillSession mSession = new MiniDrillSession();
    private final MiniDrillScheduler mScheduler = new MiniDrillScheduler();
    private MiniDrillConfig mConfig;
    private MiniDrillSettings mSettings;
    private MiniDrillSpeechPlayer mSpeechPlayer;
    private Disposable mConfigAction;
    private MiniDrillCard mOverlayCard;
    private boolean mOverlayRevealed;
    private boolean mPauseCardShownForCurrentPause;
    private int mOverlayCardsShown;
    private long mLastOverlayPlaybackSeconds;
    private long mNextAllowedOverlayPlaybackSeconds;
    private boolean mOverlayScheduleStarted;

    @Override
    public void onInit() {
        loadConfig();
    }

    @Override
    public void onNewVideo(Video item) {
        mSession.reset();
        mOverlayCard = null;
        mOverlayRevealed = false;
        mPauseCardShownForCurrentPause = false;
        mOverlayCardsShown = 0;
        mLastOverlayPlaybackSeconds = 0;
        mNextAllowedOverlayPlaybackSeconds = 0;
        mOverlayScheduleStarted = false;
        dismissOverlay();
        startPlaybackOverlayChecks();
    }

    @Override
    public void onPlay() {
        mPauseCardShownForCurrentPause = false;
        startPlaybackOverlayChecks();
    }

    @Override
    public void onPause() {
        stopPlaybackOverlayChecks();
    }

    @Override
    public void onPlayEnd() {
        stopPlaybackOverlayChecks();
        maybeShowEndReview();
    }

    @Override
    public void onViewPaused() {
        stopPlaybackOverlayChecks();
    }

    @Override
    public void onViewResumed() {
        startPlaybackOverlayChecks();
    }

    @Override
    public void onSeekPositionChanged(long positionMs) {
        long positionSeconds = positionMs / 1_000;
        mOverlayScheduleStarted = true;
        mLastOverlayPlaybackSeconds = positionSeconds;
        mNextAllowedOverlayPlaybackSeconds = Math.max(
                mNextAllowedOverlayPlaybackSeconds,
                positionSeconds + PLAYBACK_OVERLAY_SEEK_COOLDOWN_SECONDS);
    }

    @Override
    public void onEngineReleased() {
        stopPlaybackOverlayChecks();
        dismissOverlay();

        if (mSpeechPlayer != null) {
            mSpeechPlayer.stop();
        }
    }

    @Override
    public void onFinish() {
        stopPlaybackOverlayChecks();
        RxHelper.disposeActions(mConfigAction);
        dismissOverlay();

        if (mSpeechPlayer != null) {
            mSpeechPlayer.shutdown();
            mSpeechPlayer = null;
        }
    }

    public void reloadConfig() {
        loadConfig();
    }

    private void loadConfig() {
        if (getContext() == null) {
            return;
        }

        if (mSettings == null) {
            mSettings = MiniDrillSettings.instance(getContext());
        }

        if (mSpeechPlayer == null) {
            mSpeechPlayer = new MiniDrillSpeechPlayer(getContext());
        }

        RxHelper.disposeActions(mConfigAction);
        mConfigAction = RxHelper.execute(
                new MiniDrillConfigSource(getContext()).loadObserve(),
                config -> {
                    mConfig = config;
                    mSpeechPlayer.configure(config);
                    startPlaybackOverlayChecks();
                },
                error -> Log.e(TAG, "Config load error: %s", error.getMessage()));
    }

    private void runPlaybackOverlayCheck() {
        maybeShowPlaybackOverlay();
        startPlaybackOverlayChecks();
    }

    private void startPlaybackOverlayChecks() {
        mHandler.removeCallbacks(mPlaybackOverlayCheck);

        if (getPlayer() != null && getPlayer().isPlaying()) {
            mHandler.postDelayed(mPlaybackOverlayCheck, PLAYBACK_OVERLAY_CHECK_MS);
        }
    }

    private void stopPlaybackOverlayChecks() {
        mHandler.removeCallbacks(mPlaybackOverlayCheck);
    }

    private void maybeShowPlaybackOverlay() {
        if (!hasUsableConfig() || getPlayer() == null || !(getPlayer() instanceof MiniDrillUi)) {
            return;
        }

        long positionSeconds = getPlayer().getPositionMs() / 1_000;

        if (!mOverlayScheduleStarted) {
            mOverlayScheduleStarted = true;
            mLastOverlayPlaybackSeconds = positionSeconds;
            return;
        }
        MiniDrillUi ui = (MiniDrillUi) getPlayer();
        MiniDrillScheduler.Inputs inputs = createSchedulerInputs(positionSeconds);
        inputs.videoPlaying = getPlayer().isPlaying();
        inputs.overlayVisible = ui.isMiniDrillOverlayShown() || ui.isMiniDrillPlaybackBlocked() || getPlayer().isOverlayShown();
        inputs.modalVisible = isModalVisible();

        if (mScheduler.evaluate(inputs) != MiniDrillScheduler.Decision.SHOW_OVERLAY) {
            return;
        }

        MiniDrillCard card = mSession.pickCard(mConfig.cards);

        if (card == null) {
            return;
        }

        showOverlay(card, false);
    }

    public RuntimeStatus getRuntimeStatus() {
        PlaybackView player = getPlayer();
        MiniDrillUi miniDrillUi = player instanceof MiniDrillUi ? (MiniDrillUi) player : null;
        RuntimeStatus status = new RuntimeStatus();
        status.configLoaded = mConfig != null;
        status.hasActivePlayer = player != null;
        status.hasMiniDrillUi = miniDrillUi != null;
        status.overlayCardsShown = mOverlayCardsShown;

        long playbackSeconds = 0;
        int effectiveIntervalSeconds = 0;

        if (mConfig != null) {
            status.miniDrillsEnabled = isMiniDrillsEnabled();
            status.hasCards = hasCards();
            status.overlayFrequencyEnabled = isOverlayFrequencyEnabled();
            effectiveIntervalSeconds = getEffectiveOverlayIntervalSeconds();
            status.maxOverlayCardsPerVideo = mConfig.frequency.maxOverlayCardsPerVideo;
            status.playbackOverlayEnabled = mConfig.playbackOverlay.enabled;
        }

        if (player != null) {
            playbackSeconds = player.getPositionMs() / 1_000;
            status.videoPlaying = player.isPlaying();
        }

        updateNextCardTiming(status, playbackSeconds, effectiveIntervalSeconds);

        if (!status.hasActivePlayer) {
            status.reason = "No active playback";
            return status;
        }

        if (!status.configLoaded) {
            status.reason = "No config loaded";
            return status;
        }

        if (!status.hasCards) {
            status.reason = "No cards loaded";
            return status;
        }

        if (!status.hasMiniDrillUi) {
            status.reason = "Player view unavailable";
            return status;
        }

        if (status.videoPlaying && !mOverlayScheduleStarted && effectiveIntervalSeconds > 0) {
            status.reason = "Waiting for first playback tick";
            status.nextEligiblePlaybackSeconds = playbackSeconds + effectiveIntervalSeconds;
            status.secondsUntilNextCard = effectiveIntervalSeconds;
            return status;
        }

        MiniDrillScheduler.Inputs inputs = createSchedulerInputs(playbackSeconds);
        inputs.videoPlaying = status.videoPlaying;
        inputs.overlayVisible = isOverlayVisible(player, miniDrillUi);
        inputs.modalVisible = isModalVisible();

        MiniDrillScheduler.Status schedulerStatus = mScheduler.evaluateStatus(inputs);
        status.reason = schedulerStatus.reason;

        if (schedulerStatus.nextEligiblePlaybackSeconds > 0) {
            status.nextEligiblePlaybackSeconds = schedulerStatus.nextEligiblePlaybackSeconds;
            status.secondsUntilNextCard = schedulerStatus.secondsUntilEligible;
        }

        return status;
    }

    public void showDevOverlayNow() {
        if (!hasUsableConfig() || getPlayer() == null || !(getPlayer() instanceof MiniDrillUi)) {
            return;
        }

        MiniDrillUi ui = (MiniDrillUi) getPlayer();

        if (ui.isMiniDrillOverlayShown()) {
            return;
        }

        MiniDrillCard card = mSession.pickCard(mConfig.cards);

        if (card == null) {
            return;
        }

        showOverlay(card, false, false);
    }

    private void showOverlay(MiniDrillCard card, boolean revealed) {
        showOverlay(card, revealed, true);
    }

    private void showOverlay(MiniDrillCard card, boolean revealed, boolean countForFrequency) {
        if (getPlayer() == null || !(getPlayer() instanceof MiniDrillUi)) {
            return;
        }

        MiniDrillUi ui = (MiniDrillUi) getPlayer();
        mOverlayCard = card;
        mOverlayRevealed = revealed;

        if (!revealed) {
            mSession.markShown(card);
            if (countForFrequency) {
                mOverlayCardsShown++;
                mLastOverlayPlaybackSeconds = getPlayer().getPositionMs() / 1_000;
            }
        }

        int timeoutMs = Math.max(1, mConfig.playbackOverlay.visibleSeconds) * 1_000;
        ui.showMiniDrillOverlay(card, revealed, createOverlayCallback(card), timeoutMs);
    }

    private MiniDrillUi.Callback createOverlayCallback(MiniDrillCard card) {
        return new MiniDrillUi.Callback() {
            @Override
            public void onReveal() {
                mSession.markRevealed(card);
                showOverlay(card, true);
                speakAnswer(card, mConfig.audio.playbackOverlay, true);
            }

            @Override
            public void onLater() {
                mSession.markPending(card);
                dismissOverlayAfterAction(false);
            }

            @Override
            public void onSkip() {
                mSession.markPending(card);
                dismissOverlayAfterAction(false);
            }

            @Override
            public void onEasy() {
                mSession.markEasy(card);
                dismissOverlayAfterAction(true);
            }

            @Override
            public void onHard() {
                mSession.markHard(card);
                dismissOverlayAfterAction(true);
            }

            @Override
            public void onAgainLater() {
                mSession.markSnoozed(card);
                dismissOverlayAfterAction(true);
            }

            @Override
            public void onAnnoying() {
                mSession.markDisabledForSession(card);
                long positionSeconds = getPlayer() != null ? getPlayer().getPositionMs() / 1_000 : 0;
                mNextAllowedOverlayPlaybackSeconds = positionSeconds + mConfig.frequency.cooldownAfterAnnoyingSeconds;
                dismissOverlayAfterAction(mOverlayRevealed);
            }

            @Override
            public void onIgnored() {
                if (!mOverlayRevealed) {
                    mSession.markIgnored(card);
                }

                updateDismissCooldown();
                clearOverlayState();
            }

            @Override
            public void onDismiss() {
                if (!mOverlayRevealed) {
                    mSession.markPending(card);
                }

                dismissOverlayAfterAction(mOverlayRevealed);
            }
        };
    }

    private void dismissOverlayAfterAction(boolean revealed) {
        if (!revealed && mOverlayCard != null) {
            mSession.markPending(mOverlayCard);
        }

        updateDismissCooldown();
        dismissOverlay();
    }

    private void dismissOverlay() {
        if (getPlayer() instanceof MiniDrillUi) {
            ((MiniDrillUi) getPlayer()).dismissMiniDrillOverlay();
        }

        clearOverlayState();
    }

    private void clearOverlayState() {
        mOverlayCard = null;
        mOverlayRevealed = false;
    }

    private void updateDismissCooldown() {
        if (getPlayer() == null || mConfig == null) {
            return;
        }

        long positionSeconds = getPlayer().getPositionMs() / 1_000;
        mNextAllowedOverlayPlaybackSeconds = Math.max(
                mNextAllowedOverlayPlaybackSeconds,
                positionSeconds + mConfig.frequency.cooldownAfterDismissSeconds);
    }

    private void maybeShowPauseCard() {
        if (!hasUsableConfig() || getPlayer() == null || mPauseCardShownForCurrentPause || isModalVisible()) {
            return;
        }

        MiniDrillScheduler.Inputs inputs = createSchedulerInputs(getPlayer().getPositionMs() / 1_000);
        inputs.videoPaused = !getPlayer().isPlaying();
        inputs.modalVisible = isModalVisible();

        if (mScheduler.evaluate(inputs) != MiniDrillScheduler.Decision.SHOW_PAUSE_CARD) {
            return;
        }

        MiniDrillCard card = mSession.pickCard(mConfig.cards);

        if (card == null) {
            return;
        }

        mPauseCardShownForCurrentPause = true;
        mSession.markShown(card);
        showPauseCard(card, false);
    }

    private void showPauseCard(MiniDrillCard card, boolean revealed) {
        AppDialogPresenter dialog = getAppDialogPresenter();
        final boolean[] handled = { false };

        dialog.appendStringsCategory(card.getPromptPrefix(), singleTextItem(card.getPromptText()));

        if (revealed) {
            dialog.appendStringsCategory("Answer", singleTextItem(card.getAnswerText()));
            dialog.appendSingleButton(UiOptionItem.from("Easy", option -> {
                handled[0] = true;
                mSession.markEasy(card);
                dialog.closeDialog();
            }));
            dialog.appendSingleButton(UiOptionItem.from("Hard", option -> {
                handled[0] = true;
                mSession.markHard(card);
                dialog.closeDialog();
            }));
            dialog.appendSingleButton(UiOptionItem.from("Again later", option -> {
                handled[0] = true;
                mSession.markSnoozed(card);
                dialog.closeDialog();
            }));
            dialog.appendSingleButton(UiOptionItem.from("🙄", option -> {
                handled[0] = true;
                mSession.markDisabledForSession(card);
                dialog.closeDialog();
            }));
        } else {
            dialog.appendSingleButton(UiOptionItem.from("Reveal", option -> {
                handled[0] = true;
                mSession.markRevealed(card);
                dialog.closeDialog();
                speakAnswer(card, mConfig.audio.pauseCard, true);
                mHandler.post(() -> showPauseCard(card, true));
            }));
            dialog.appendSingleButton(UiOptionItem.from("Continue without answer", option -> {
                handled[0] = true;
                mSession.markPending(card);
                dialog.closeDialog();

                if (getPlayer() != null) {
                    getPlayer().setPlayWhenReady(true);
                }
            }));
        }

        dialog.setOnFinish(() -> {
            if (!handled[0] && !revealed) {
                mSession.markPending(card);
            }
        });
        dialog.showDialog("Mini Drill");
    }

    private void maybeShowEndReview() {
        if (!hasUsableConfig() || getPlayer() == null || isModalVisible()) {
            return;
        }

        List<MiniDrillCard> pendingCards = mSession.getPendingCards(mConfig.cards);
        MiniDrillScheduler.Inputs inputs = createSchedulerInputs(getPlayer().getPositionMs() / 1_000);
        inputs.videoEnded = true;
        inputs.hasPendingReview = !pendingCards.isEmpty();
        inputs.modalVisible = isModalVisible();

        if (mScheduler.evaluate(inputs) == MiniDrillScheduler.Decision.SHOW_END_REVIEW) {
            showEndReview(pendingCards);
        }
    }

    private void showEndReview(List<MiniDrillCard> pendingCards) {
        if (pendingCards == null || pendingCards.isEmpty()) {
            return;
        }

        AppDialogPresenter dialog = getAppDialogPresenter();
        dialog.appendStringsCategory("Unresolved Mini Drills: " + pendingCards.size(), pendingPromptItems(pendingCards));

        for (int i = 0; i < pendingCards.size(); i++) {
            MiniDrillCard card = pendingCards.get(i);
            int number = i + 1;
            dialog.appendSingleButton(UiOptionItem.from(number + ". Reveal", option -> {
                dialog.closeDialog();
                showEndReviewAnswer(card);
            }));
        }

        dialog.appendSingleButton(UiOptionItem.from("Finish", option -> dialog.closeDialog()));
        dialog.showDialog("End Review");
    }

    private void showEndReviewAnswer(MiniDrillCard card) {
        AppDialogPresenter dialog = getAppDialogPresenter();
        dialog.appendStringsCategory(card.getPromptPrefix(), singleTextItem(card.getPromptText()));
        dialog.appendStringsCategory("Answer", singleTextItem(card.getAnswerText()));
        dialog.appendSingleButton(UiOptionItem.from("Listen", option -> speakAnswer(card, mConfig.audio.endReview, false)));
        dialog.appendSingleButton(UiOptionItem.from("Done", option -> {
            mSession.markRevealed(card);
            dialog.closeDialog();
            mHandler.post(() -> showEndReview(mSession.getPendingCards(mConfig.cards)));
        }));
        dialog.appendSingleButton(UiOptionItem.from("Finish", option -> dialog.closeDialog()));
        dialog.showDialog("End Review");
    }

    private void speakAnswer(MiniDrillCard card, MiniDrillConfig.SurfaceConfig surfaceAudioConfig, boolean allowAutoPlay) {
        if (mConfig == null || mSpeechPlayer == null || !mConfig.audio.enabled || surfaceAudioConfig == null) {
            return;
        }

        if (allowAutoPlay && !surfaceAudioConfig.autoPlayAnswerAfterReveal) {
            return;
        }

        MiniDrillCard.Answer answer = card.getPreferredAnswer();

        if (answer != null) {
            mSpeechPlayer.speak(
                    answer.audio,
                    surfaceAudioConfig.duckVideoAudioWhileSpeaking,
                    mConfig.audio.videoDuckVolume,
                    getPlayer());
        }
    }

    private List<OptionItem> singleTextItem(String text) {
        List<OptionItem> items = new ArrayList<>();
        items.add(UiOptionItem.from(text));
        return items;
    }

    private List<OptionItem> pendingPromptItems(List<MiniDrillCard> pendingCards) {
        List<OptionItem> items = new ArrayList<>();

        for (int i = 0; i < pendingCards.size(); i++) {
            MiniDrillCard card = pendingCards.get(i);
            items.add(UiOptionItem.from((i + 1) + ". " + card.getPromptText()));
        }

        return items;
    }

    private MiniDrillScheduler.Inputs createSchedulerInputs(long positionSeconds) {
        MiniDrillScheduler.Inputs inputs = new MiniDrillScheduler.Inputs();
        inputs.config = mConfig;
        inputs.playbackSeconds = positionSeconds;
        inputs.miniDrillsEnabled = isMiniDrillsEnabled();
        inputs.overlayFrequencyEnabled = isOverlayFrequencyEnabled();
        inputs.overlayIntervalSeconds = getOverlayIntervalSeconds();
        inputs.overlayCardsShown = mOverlayCardsShown;
        inputs.lastOverlayPlaybackSeconds = mLastOverlayPlaybackSeconds;
        inputs.nextAllowedOverlayPlaybackSeconds = mNextAllowedOverlayPlaybackSeconds;
        inputs.hasPendingReview = !mSession.getPendingCards(mConfig.cards).isEmpty();
        return inputs;
    }

    private boolean hasUsableConfig() {
        return isMiniDrillsEnabled() && hasCards();
    }

    private boolean hasCards() {
        return mConfig != null && mConfig.cards != null && !mConfig.cards.isEmpty();
    }

    private boolean isMiniDrillsEnabled() {
        return mConfig != null && (mSettings != null ? mSettings.isEnabled(mConfig) : mConfig.enabled);
    }

    private boolean isOverlayFrequencyEnabled() {
        return mConfig != null && (mSettings != null ? mSettings.isEffectiveOverlayFrequencyEnabled(mConfig) :
                mConfig.frequency.isOverlayEnabled());
    }

    private int getOverlayIntervalSeconds() {
        return mConfig != null ? (mSettings != null ? mSettings.getEffectiveOverlayIntervalSeconds(mConfig) :
                mConfig.frequency.intervalSeconds) : 0;
    }

    private int getEffectiveOverlayIntervalSeconds() {
        return mConfig != null ? Math.max(getOverlayIntervalSeconds(), mConfig.frequency.minimumOverlayIntervalSeconds) : 0;
    }

    private void updateNextCardTiming(RuntimeStatus status, long playbackSeconds, int intervalSeconds) {
        if (intervalSeconds <= 0) {
            return;
        }

        status.nextEligiblePlaybackSeconds = Math.max(
                mLastOverlayPlaybackSeconds + intervalSeconds,
                mNextAllowedOverlayPlaybackSeconds);
        status.secondsUntilNextCard = Math.max(0, status.nextEligiblePlaybackSeconds - playbackSeconds);
    }

    private boolean isOverlayVisible(PlaybackView player, MiniDrillUi miniDrillUi) {
        boolean playerOverlayVisible = player != null && player.isOverlayShown();
        boolean miniDrillVisible = miniDrillUi != null &&
                (miniDrillUi.isMiniDrillOverlayShown() || miniDrillUi.isMiniDrillPlaybackBlocked());

        return playerOverlayVisible || miniDrillVisible;
    }

    private boolean isModalVisible() {
        return getContext() != null && getAppDialogPresenter().isDialogShown();
    }

    public static class RuntimeStatus {
        public boolean configLoaded;
        public boolean hasActivePlayer;
        public boolean hasMiniDrillUi;
        public boolean miniDrillsEnabled;
        public boolean hasCards;
        public boolean playbackOverlayEnabled;
        public boolean overlayFrequencyEnabled;
        public boolean videoPlaying;
        public String reason;
        public int overlayCardsShown;
        public int maxOverlayCardsPerVideo;
        public long nextEligiblePlaybackSeconds;
        public long secondsUntilNextCard;
    }
}
