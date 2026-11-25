package com.lagradost.cloudstream3.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import androidx.viewbinding.ViewBinding;
import com.xtsream.app.R;

/**
 * Compatibility shadow for CloudStream3's generated ToastBinding so plugins referencing
 * `com.lagradost.cloudstream3.databinding.ToastBinding` link cleanly.
 */
public final class ToastBinding implements ViewBinding {
    public final CardView root;
    public final TextView text;

    private ToastBinding(CardView root, TextView text) {
        this.root = root;
        this.text = text;
    }

    @Override
    public CardView getRoot() {
        return root;
    }

    public TextView getText() {
        return text;
    }

    public static ToastBinding inflate(LayoutInflater inflater) {
        return inflate(inflater, null, false);
    }

    public static ToastBinding inflate(LayoutInflater inflater, ViewGroup parent) {
        return inflate(inflater, parent, false);
    }

    public static ToastBinding inflate(LayoutInflater inflater, ViewGroup parent, boolean attachToParent) {
        View view = inflater.inflate(R.layout.cs_toast, parent, false);
        if (attachToParent && parent != null) parent.addView(view);
        return bind(view);
    }

    public static ToastBinding bind(View view) {
        CardView root = (CardView) view;
        TextView text = view.findViewById(R.id.toast_text);
        return new ToastBinding(root, text);
    }
}
