package com.best.deskclock.timer.quick;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.best.deskclock.R;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class QuickTimerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_ADD = 1;

    private final Context mContext;
    private final List<QuickTimer> mQuickTimers = new ArrayList<>();
    private final OnQuickTimerClickListener mListener;

    public interface OnQuickTimerClickListener {
        void onQuickTimerClick(QuickTimer quickTimer);
        void onAddQuickTimerClick();
        void onQuickTimerLongClick(QuickTimer quickTimer);
    }

    public QuickTimerAdapter(Context context, OnQuickTimerClickListener listener) {
        mContext = context;
        mListener = listener;
    }

    public void setQuickTimers(List<QuickTimer> quickTimers) {
        mQuickTimers.clear();
        if (quickTimers != null) {
            mQuickTimers.addAll(quickTimers);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mQuickTimers.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position < mQuickTimers.size() ? VIEW_TYPE_ITEM : VIEW_TYPE_ADD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        if (viewType == VIEW_TYPE_ITEM) {
            View view = inflater.inflate(R.layout.quick_timer_item, parent, false);
            return new QuickTimerViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.quick_timer_item, parent, false);
            return new AddViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof QuickTimerViewHolder) {
            QuickTimer quickTimer = mQuickTimers.get(position);
            ((QuickTimerViewHolder) holder).bind(quickTimer);
        } else if (holder instanceof AddViewHolder) {
            ((AddViewHolder) holder).bind();
        }
    }

    class QuickTimerViewHolder extends RecyclerView.ViewHolder {
        private final Chip chip;

        public QuickTimerViewHolder(@NonNull View itemView) {
            super(itemView);
            chip = itemView.findViewById(R.id.quick_timer_chip);
        }

        public void bind(QuickTimer quickTimer) {
            String durationStr = DateUtils.formatElapsedTime(quickTimer.duration / 1000);
            if (quickTimer.label != null && !quickTimer.label.isEmpty()) {
                chip.setText(quickTimer.label + " (" + durationStr + ")");
            } else {
                chip.setText(durationStr);
            }
            chip.setOnClickListener(v -> mListener.onQuickTimerClick(quickTimer));
            chip.setOnLongClickListener(v -> {
                mListener.onQuickTimerLongClick(quickTimer);
                return true;
            });
        }
    }

    class AddViewHolder extends RecyclerView.ViewHolder {
        private final Chip chip;

        public AddViewHolder(@NonNull View itemView) {
            super(itemView);
            chip = itemView.findViewById(R.id.quick_timer_chip);
        }

        public void bind() {
            chip.setText("+");
            chip.setOnClickListener(v -> mListener.onAddQuickTimerClick());
        }
    }
}
