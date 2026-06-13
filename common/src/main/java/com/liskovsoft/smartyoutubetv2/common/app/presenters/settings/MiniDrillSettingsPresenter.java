package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.minidrills.MiniDrillCard;
import com.liskovsoft.smartyoutubetv2.common.app.models.minidrills.MiniDrillConfig;
import com.liskovsoft.smartyoutubetv2.common.app.models.minidrills.MiniDrillConfigSource;
import com.liskovsoft.smartyoutubetv2.common.app.models.minidrills.MiniDrillController;
import com.liskovsoft.smartyoutubetv2.common.app.models.minidrills.MiniDrillSettings;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MiniDrillSettingsPresenter extends BasePresenter<Void> {
    private static final String TITLE = "Mini Drills";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final MiniDrillConfigSource mConfigSource;
    private final MiniDrillSettings mSettings;

    private MiniDrillSettingsPresenter(Context context) {
        super(context);
        mConfigSource = new MiniDrillConfigSource(context);
        mSettings = MiniDrillSettings.instance(context);
    }

    public static MiniDrillSettingsPresenter instance(Context context) {
        return new MiniDrillSettingsPresenter(context);
    }

    public void show() {
        RxHelper.execute(
                mConfigSource.loadObserve(),
                this::showSettings,
                error -> showLoadError(error.getMessage()));
    }

    private void showSettings(MiniDrillConfig config) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());

        appendStatus(dialog, config);
        appendEnabledOverride(dialog);
        appendFrequencyOverride(dialog, config);
        appendActions(dialog, config);

        dialog.showDialog(TITLE);
    }

    private void appendStatus(AppDialogPresenter dialog, MiniDrillConfig config) {
        MiniDrillConfigSource.Status status = mConfigSource.getStatus();
        List<OptionItem> items = new ArrayList<>();

        items.add(UiOptionItem.from("Effective: " + (mSettings.isEnabled(config) ? "enabled" : "disabled")));
        items.add(UiOptionItem.from("Source: " + safe(status.source)));
        items.add(UiOptionItem.from("Content: " + safe(status.contentVersion)));
        items.add(UiOptionItem.from("Cards: " + (status.cardCount >= 0 ? status.cardCount : 0)));
        items.add(UiOptionItem.from("Last refresh: " + formatTime(status.lastFetchTimeMs)));

        if (status.lastError != null && !status.lastError.trim().isEmpty()) {
            items.add(UiOptionItem.from("Last error: " + status.lastError));
        }

        items.add(UiOptionItem.from("Remote enabled: " + (config != null && config.enabled)));
        items.add(UiOptionItem.from("Remote frequency: " + remoteFrequency(config)));
        items.add(UiOptionItem.from("Effective frequency: " + effectiveFrequency(config)));

        dialog.appendStringsCategory("Status", items);
    }

    private void appendEnabledOverride(AppDialogPresenter dialog) {
        String current = mSettings.getEnabledOverride();
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from("Use remote", option -> mSettings.setEnabledOverride(MiniDrillSettings.ENABLED_REMOTE),
                MiniDrillSettings.ENABLED_REMOTE.equals(current)));
        options.add(UiOptionItem.from("Force enabled", option -> mSettings.setEnabledOverride(MiniDrillSettings.ENABLED_ON),
                MiniDrillSettings.ENABLED_ON.equals(current)));
        options.add(UiOptionItem.from("Force disabled", option -> mSettings.setEnabledOverride(MiniDrillSettings.ENABLED_OFF),
                MiniDrillSettings.ENABLED_OFF.equals(current)));

        dialog.appendRadioCategory("Enabled", options);
    }

    private void appendFrequencyOverride(AppDialogPresenter dialog, MiniDrillConfig config) {
        String current = mSettings.getFrequencyOverride();
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from("Use remote (" + remoteFrequency(config) + ")",
                option -> mSettings.setFrequencyOverride(MiniDrillSettings.FREQUENCY_REMOTE),
                MiniDrillSettings.FREQUENCY_REMOTE.equals(current)));
        options.add(UiOptionItem.from("Off", option -> mSettings.setFrequencyOverride(MiniDrillSettings.FREQUENCY_OFF),
                MiniDrillSettings.FREQUENCY_OFF.equals(current)));
        options.add(UiOptionItem.from("Low", option -> mSettings.setFrequencyOverride(MiniDrillSettings.FREQUENCY_LOW),
                MiniDrillSettings.FREQUENCY_LOW.equals(current)));
        options.add(UiOptionItem.from("Normal", option -> mSettings.setFrequencyOverride(MiniDrillSettings.FREQUENCY_NORMAL),
                MiniDrillSettings.FREQUENCY_NORMAL.equals(current)));
        options.add(UiOptionItem.from("High", option -> mSettings.setFrequencyOverride(MiniDrillSettings.FREQUENCY_HIGH),
                MiniDrillSettings.FREQUENCY_HIGH.equals(current)));

        dialog.appendRadioCategory("Playback overlay frequency", options);
    }

    private void appendActions(AppDialogPresenter dialog, MiniDrillConfig config) {
        dialog.appendSingleButton(UiOptionItem.from("Refresh config now", option -> refreshConfig(dialog)));
        dialog.appendSingleButton(UiOptionItem.from("Show cards", option -> {
            dialog.closeDialog();
            mHandler.post(() -> showCards(config));
        }));
        dialog.appendSingleButton(UiOptionItem.from("Reset local overrides", option -> {
            mSettings.resetOverrides();
            dialog.closeDialog();
            mHandler.post(this::show);
        }));
    }

    private void refreshConfig(AppDialogPresenter dialog) {
        MessageHelpers.showLongMessage(getContext(), "Refreshing Mini Drills config...");
        RxHelper.execute(
                mConfigSource.refreshObserve(),
                config -> {
                    reloadActivePlaybackConfig();
                    MiniDrillConfigSource.Status status = mConfigSource.getStatus();
                    if (status.lastError != null && !status.lastError.trim().isEmpty() && !"remote".equals(status.source)) {
                        MessageHelpers.showLongMessage(getContext(), "Mini Drills remote failed; using " + status.source + ": " + status.lastError);
                    } else {
                        MessageHelpers.showLongMessage(getContext(), "Mini Drills config refreshed");
                    }
                    dialog.closeDialog();
                    mHandler.post(this::show);
                },
                error -> {
                    MessageHelpers.showLongMessage(getContext(), "Mini Drills refresh failed: " + error.getMessage());
                    dialog.closeDialog();
                    mHandler.post(this::show);
                });
    }

    private void reloadActivePlaybackConfig() {
        MiniDrillController controller = PlaybackPresenter.instance(getContext()).getController(MiniDrillController.class);

        if (controller != null) {
            controller.reloadConfig();
        }
    }

    private void showCards(MiniDrillConfig config) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        List<OptionItem> items = new ArrayList<>();

        if (config != null && config.cards != null) {
            for (int i = 0; i < config.cards.size(); i++) {
                items.add(UiOptionItem.from(formatCard(i + 1, config.cards.get(i))));
            }
        }

        if (items.isEmpty()) {
            items.add(UiOptionItem.from("No cards loaded"));
        }

        dialog.appendStringsCategory("Cards", items);
        dialog.appendSingleButton(UiOptionItem.from("Done", option -> dialog.closeDialog()));
        dialog.showDialog(TITLE);
    }

    private void showLoadError(String message) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        List<OptionItem> items = new ArrayList<>();
        items.add(UiOptionItem.from(message != null ? message : "Unknown error"));
        dialog.appendStringsCategory("Config load failed", items);
        dialog.showDialog(TITLE);
    }

    private String formatCard(int number, MiniDrillCard card) {
        String title = card != null ? card.getPromptText() : "";
        String answer = card != null ? card.getAnswerText() : "";
        String enabled = card != null && card.enabled ? "" : " [disabled]";
        return number + ". " + title + " -> " + answer + enabled;
    }

    private String remoteFrequency(MiniDrillConfig config) {
        if (config == null || config.frequency == null) {
            return "unknown";
        }

        return config.frequency.mode + " / " + config.frequency.intervalSeconds + "s";
    }

    private String effectiveFrequency(MiniDrillConfig config) {
        String mode = mSettings.getEffectiveFrequencyMode(config);
        int intervalSeconds = mSettings.getEffectiveOverlayIntervalSeconds(config);
        return mode + " / " + intervalSeconds + "s";
    }

    private String formatTime(long timeMs) {
        if (timeMs <= 0) {
            return "never";
        }

        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(timeMs));
    }

    private String safe(String value) {
        return value != null && !value.trim().isEmpty() ? value : "-";
    }
}
