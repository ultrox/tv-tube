package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerManager;

public interface PlaybackView extends PlayerManager {
    void showProgressBar(boolean show);
}
