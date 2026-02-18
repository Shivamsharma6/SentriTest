// ObservableScrollView.java
package com.sentri.access_control.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

public class ObservableScrollView extends ScrollView {
    public interface OnScrollListener { void onScrollChanged(int l, int t); }
    private OnScrollListener listener;

    public ObservableScrollView(Context c) { super(c); }
    public ObservableScrollView(Context c, AttributeSet a) { super(c, a); }
    public ObservableScrollView(Context c, AttributeSet a, int s) { super(c, a, s); }

    public void setOnScrollListener(OnScrollListener l) { this.listener = l; }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (listener != null) listener.onScrollChanged(l, t);
    }
}
