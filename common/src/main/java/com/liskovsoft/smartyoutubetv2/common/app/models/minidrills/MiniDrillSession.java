package com.liskovsoft.smartyoutubetv2.common.app.models.minidrills;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MiniDrillSession {
    public enum State {
        UNSEEN,
        SHOWN,
        IGNORED,
        PENDING_REVIEW,
        REVEALED,
        RATED_EASY,
        RATED_HARD,
        SNOOZED,
        DISABLED_FOR_SESSION
    }

    private final Map<String, State> mStates = new HashMap<>();
    private final Set<String> mPendingReviewIds = new LinkedHashSet<>();
    private final Random mRandom = new Random();
    private String mLastCardId;

    public void reset() {
        mStates.clear();
        mPendingReviewIds.clear();
        mLastCardId = null;
    }

    public MiniDrillCard pickCard(List<MiniDrillCard> cards) {
        List<MiniDrillCard> unseen = new ArrayList<>();
        List<MiniDrillCard> available = new ArrayList<>();

        for (MiniDrillCard card : cards) {
            if (!isAvailable(card)) {
                continue;
            }

            available.add(card);

            State state = mStates.get(card.id);
            if (state == null || state == State.UNSEEN) {
                unseen.add(card);
            }
        }

        List<MiniDrillCard> pool = !unseen.isEmpty() ? unseen : available;

        if (pool.isEmpty()) {
            return null;
        }

        MiniDrillCard card = pool.get(mRandom.nextInt(pool.size()));

        if (pool.size() > 1) {
            int attempts = 0;

            while (card.id.equals(mLastCardId) && attempts < 4) {
                card = pool.get(mRandom.nextInt(pool.size()));
                attempts++;
            }
        }

        mLastCardId = card.id;
        return card;
    }

    public void markShown(MiniDrillCard card) {
        setState(card, State.SHOWN);
    }

    public void markIgnored(MiniDrillCard card) {
        setState(card, State.IGNORED);
        addPending(card);
    }

    public void markPending(MiniDrillCard card) {
        setState(card, State.PENDING_REVIEW);
        addPending(card);
    }

    public void markRevealed(MiniDrillCard card) {
        setState(card, State.REVEALED);
        removePending(card);
    }

    public void markEasy(MiniDrillCard card) {
        setState(card, State.RATED_EASY);
        removePending(card);
    }

    public void markHard(MiniDrillCard card) {
        setState(card, State.RATED_HARD);
        removePending(card);
    }

    public void markSnoozed(MiniDrillCard card) {
        setState(card, State.SNOOZED);
        addPending(card);
    }

    public void markDisabledForSession(MiniDrillCard card) {
        setState(card, State.DISABLED_FOR_SESSION);
        removePending(card);
    }

    public List<MiniDrillCard> getPendingCards(List<MiniDrillCard> cards) {
        List<MiniDrillCard> result = new ArrayList<>();

        for (String cardId : mPendingReviewIds) {
            for (MiniDrillCard card : cards) {
                if (card.id.equals(cardId)) {
                    result.add(card);
                    break;
                }
            }
        }

        return result;
    }

    private boolean isAvailable(MiniDrillCard card) {
        return card != null && card.enabled && mStates.get(card.id) != State.DISABLED_FOR_SESSION;
    }

    private void setState(MiniDrillCard card, State state) {
        if (card != null) {
            mStates.put(card.id, state);
        }
    }

    private void addPending(MiniDrillCard card) {
        if (card != null) {
            mPendingReviewIds.add(card.id);
        }
    }

    private void removePending(MiniDrillCard card) {
        if (card != null) {
            mPendingReviewIds.remove(card.id);
        }
    }
}
