package com.best.deskclock.widget;
import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
public class MaxHeightNestedScrollView extends NestedScrollView {
    private int mMaxHeight;
    public MaxHeightNestedScrollView(@NonNull Context context) { this(context, null); }
    public MaxHeightNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs) { this(context, attrs, 0); }
    public MaxHeightNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mMaxHeight = (int) (300 * context.getResources().getDisplayMetrics().density);
    }
    public void setMaxHeight(int maxHeight) { mMaxHeight = maxHeight; requestLayout(); }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mMaxHeight > 0) heightMeasureSpec = MeasureSpec.makeMeasureSpec(mMaxHeight, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
