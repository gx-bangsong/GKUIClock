/*
 * Copyright (C) 2023 The LineageOS Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.best.deskclock.timer;

import static com.best.deskclock.settings.PreferencesDefaultValues.DEFAULT_SORT_TIMER_MANUALLY;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.best.deskclock.R;
import com.best.deskclock.data.DataModel;
import com.best.deskclock.data.SettingsDAO;
import com.best.deskclock.data.Timer;
import com.best.deskclock.data.TimerListener;
import com.best.deskclock.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * This adapter produces a {@link TimerViewHolder} for each timer.
 */
public class TimerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements TimerListener {

    private static final int SINGLE_TIMER = R.layout.timer_single_item;
    private static final int MULTIPLE_TIMERS = R.layout.timer_item;

    /** Only holders currently attached to the RecyclerView need periodic time updates. */
    private final Set<TimerViewHolder> mAttachedHolders =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Timer> mTimers = new ArrayList<>();
    private final List<Timer> mReadOnlyTimers = Collections.unmodifiableList(mTimers);
    private final TimerClickHandler mTimerClickHandler;
    private final Context mContext;
    private final SharedPreferences mPrefs;

    private boolean mIsManualSorting;

    public TimerAdapter(Context context, SharedPreferences sharedPreferences,
                        TimerClickHandler timerClickHandler) {
        mContext = context.getApplicationContext();
        mPrefs = sharedPreferences;
        mTimerClickHandler = timerClickHandler;
        rebuildTimerSnapshot();
    }

    @Override
    public int getItemCount() {
        return mTimers.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (mTimers.size() == 1) {
            return (ThemeUtils.isTablet() || ThemeUtils.isPortrait())
                    ? SINGLE_TIMER : MULTIPLE_TIMERS;
        }
        return MULTIPLE_TIMERS;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View view = inflater.inflate(viewType == SINGLE_TIMER
                ? R.layout.timer_single_item : R.layout.timer_item, parent, false);
        return new TimerViewHolder(view, mTimerClickHandler);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder itemViewHolder, int position) {
        final TimerViewHolder holder = (TimerViewHolder) itemViewHolder;
        holder.onBind(mTimers.get(position).getId());
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        mAttachedHolders.add((TimerViewHolder) holder);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        mAttachedHolders.remove((TimerViewHolder) holder);
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        mAttachedHolders.remove((TimerViewHolder) holder);
        super.onViewRecycled(holder);
    }

    @Override
    public void timerAdded(Timer timer) {
        final int oldCount = mTimers.size();
        rebuildTimerSnapshot();
        saveTimerList();

        if (oldCount == 1) {
            // The first timer switches from the single-timer to multi-timer layout.
            notifyDataSetChanged();
        } else {
            final int position = findTimerPosition(timer.getId());
            if (position >= 0) {
                notifyItemInserted(position);
            } else {
                notifyDataSetChanged();
            }
        }
    }

    @Override
    public void timerRemoved(Timer timer) {
        final int oldCount = mTimers.size();
        final int oldPosition = findTimerPosition(timer.getId());
        rebuildTimerSnapshot();
        saveTimerList();

        if (oldCount == 2) {
            // The remaining timer switches to the single-timer layout.
            notifyDataSetChanged();
        } else if (oldPosition >= 0) {
            notifyItemRemoved(oldPosition);
        } else {
            notifyDataSetChanged();
        }
    }

    @Override
    public void timerUpdated(Timer before, Timer after) {
        final int oldPosition = findTimerPosition(before.getId());
        rebuildTimerSnapshot();
        final int newPosition = findTimerPosition(after.getId());

        if (oldPosition >= 0 && newPosition >= 0 && oldPosition != newPosition) {
            notifyItemMoved(oldPosition, newPosition);
            notifyItemChanged(newPosition);
        } else if (newPosition >= 0) {
            notifyItemChanged(newPosition);
        } else {
            notifyDataSetChanged();
        }
    }

    /**
     * @return {@code true} if at least one visible timer requires continuous updates
     */
    boolean updateTime() {
        boolean continuousUpdates = false;
        for (TimerViewHolder holder : mAttachedHolders) {
            continuousUpdates |= holder.updateTime();
        }
        return continuousUpdates;
    }

    Timer getTimer(int index) {
        return mTimers.get(index);
    }

    /** Returns a stable read-only snapshot until the next model callback. */
    public List<Timer> getTimers() {
        return mReadOnlyTimers;
    }

    /** Refreshes sorting preferences and the cached model snapshot. */
    public void refreshTimers() {
        rebuildTimerSnapshot();
        notifyDataSetChanged();
    }

    private void rebuildTimerSnapshot() {
        mIsManualSorting = DEFAULT_SORT_TIMER_MANUALLY.equals(
                SettingsDAO.getTimerSortingPreference(mPrefs));
        mTimers.clear();
        mTimers.addAll(DataModel.getDataModel().getTimers());
        if (!mIsManualSorting) {
            mTimers.sort(Timer.createTimerStateComparator(mContext));
        }
    }

    private int findTimerPosition(int timerId) {
        for (int i = 0; i < mTimers.size(); i++) {
            if (mTimers.get(i).getId() == timerId) {
                return i;
            }
        }
        return -1;
    }

    private boolean isManualSorting() {
        return mIsManualSorting;
    }

    private void moveTimer(int fromPosition, int toPosition) {
        if (!mIsManualSorting || fromPosition == toPosition
                || fromPosition < 0 || toPosition < 0
                || fromPosition >= mTimers.size() || toPosition >= mTimers.size()) {
            return;
        }

        // In manual mode the snapshot mirrors DataModel order; keep both in sync.
        Collections.swap(mTimers, fromPosition, toPosition);
        Collections.swap(DataModel.getDataModel().getTimers(), fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        saveTimerList();
    }

    public void saveTimerList() {
        final List<Timer> timers = DataModel.getDataModel().getTimers();
        final SharedPreferences.Editor editor = mPrefs.edit();

        if (timers.isEmpty()) {
            editor.remove("timerOrder");
        } else {
            final StringBuilder order = new StringBuilder();
            for (Timer timer : timers) {
                if (order.length() > 0) {
                    order.append(',');
                }
                order.append(timer.getId());
            }
            editor.putString("timerOrder", order.toString());
        }

        editor.apply();
    }

    public void loadTimerList() {
        final String savedOrder = mPrefs.getString("timerOrder", null);
        final List<Timer> modelTimers = DataModel.getDataModel().getTimers();
        if (savedOrder != null && !modelTimers.isEmpty()) {
            final List<Timer> unorderedTimers = new ArrayList<>(modelTimers);
            final List<Timer> orderedTimers = new ArrayList<>(modelTimers.size());

            for (String id : savedOrder.split(",")) {
                try {
                    final int timerId = Integer.parseInt(id);
                    for (int i = 0; i < unorderedTimers.size(); i++) {
                        final Timer timer = unorderedTimers.get(i);
                        if (timer.getId() == timerId) {
                            orderedTimers.add(timer);
                            unorderedTimers.remove(i);
                            break;
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed legacy entries without discarding valid timers.
                }
            }

            // Preserve newly created timers that were not present when the order was saved.
            orderedTimers.addAll(unorderedTimers);
            modelTimers.clear();
            modelTimers.addAll(orderedTimers);
        }

        rebuildTimerSnapshot();
        notifyDataSetChanged();
    }

    /**
     * Custom ItemTouchHelper.Callback for managing drag & drop of Timer items in a RecyclerView.
     */
    public static class TimerItemTouchHelper extends ItemTouchHelper.Callback {

        private final TimerAdapter mAdapter;
        private final RecyclerView mRecyclerView;
        private final RecyclerView.OnItemTouchListener mItemTouchListener;

        private boolean mIsTouchOnDragBlockingView;

        public TimerItemTouchHelper(TimerAdapter adapter, RecyclerView recyclerView) {
            mAdapter = adapter;
            mRecyclerView = recyclerView;
            mItemTouchListener = new RecyclerView.OnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv,
                                                     @NonNull MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        final View child = rv.findChildViewUnder(event.getX(), event.getY());
                        if (child != null) {
                            final View addTimeButton =
                                    child.findViewById(R.id.timer_add_time_button);
                            if (addTimeButton != null
                                    && addTimeButton.getVisibility() == View.VISIBLE) {
                                final int[] location = new int[2];
                                addTimeButton.getLocationOnScreen(location);
                                final float x = event.getRawX();
                                final float y = event.getRawY();
                                mIsTouchOnDragBlockingView = x >= location[0]
                                        && x <= location[0] + addTimeButton.getWidth()
                                        && y >= location[1]
                                        && y <= location[1] + addTimeButton.getHeight();
                            } else {
                                mIsTouchOnDragBlockingView = false;
                            }
                        } else {
                            mIsTouchOnDragBlockingView = false;
                        }
                    }
                    return false;
                }

                @Override
                public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
                }

                @Override
                public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                }
            };
            recyclerView.addOnItemTouchListener(mItemTouchListener);
        }

        /** Removes the touch listener installed outside ItemTouchHelper itself. */
        public void detach() {
            mRecyclerView.removeOnItemTouchListener(mItemTouchListener);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            final int fromPosition = viewHolder.getBindingAdapterPosition();
            final int toPosition = target.getBindingAdapterPosition();
            if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                return false;
            }

            mAdapter.moveTimer(fromPosition, toPosition);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
            if (mIsTouchOnDragBlockingView || !mAdapter.isManualSorting()) {
                return 0;
            }

            final int dragFlags;
            if (ThemeUtils.isTablet()) {
                dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN
                        | ItemTouchHelper.START | ItemTouchHelper.END;
            } else if (ThemeUtils.isLandscape()) {
                dragFlags = ItemTouchHelper.START | ItemTouchHelper.END;
            } else {
                dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            }
            return makeMovementFlags(dragFlags, 0);
        }

        @Override
        public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState,
                    isCurrentlyActive);
            viewHolder.itemView.setTranslationZ(isCurrentlyActive ? 20f : 0f);
        }
    }
}
