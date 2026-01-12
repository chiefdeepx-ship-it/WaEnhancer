package com.wmods.wppenhacer.xposed.features.listeners;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;

import java.util.HashSet;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ConversationItemListener extends Feature {

    public static HashSet<OnConversationItemListener> conversationListeners = new HashSet<>();
    private static ListAdapter mAdapter;

    public ConversationItemListener(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public static ListAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void doHook() throws Throwable {
        XposedHelpers.findAndHookMethod(ListView.class, "setAdapter", ListAdapter.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!WppCore.getCurrentActivity().getClass().getSimpleName().equals("Conversation"))
                    return;
                if (((ListView) param.thisObject).getId() != android.R.id.list) return;

                ListAdapter adapter = (ListAdapter) param.args[0];
                if (adapter instanceof HeaderViewListAdapter) {
                    adapter = ((HeaderViewListAdapter) adapter).getWrappedAdapter();
                }
                if (adapter == null) return;
                mAdapter = adapter;

                // getView मेथड को हुक कर रहे हैं
                var method = mAdapter.getClass().getDeclaredMethod("getView", int.class, View.class, ViewGroup.class);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject != mAdapter) return;

                        // FIX 1: args[1] की जगह getResult() यूज़ करें। यह असली View है जो स्क्रीन पर दिखेगा।
                        Object resultView = param.getResult();
                        if (!(resultView instanceof ViewGroup)) return;
                        final ViewGroup viewGroup = (ViewGroup) resultView;

                        var position = (int) param.args[0];
                        Object fMessageObj = mAdapter.getItem(position);
                        if (fMessageObj == null) return;
                        var fMessage = new FMessageWpp(fMessageObj);

                        // --- FIX START: DIRECT EXECUTION (No post() to stop flickering) ---
                        try {
                            android.content.Context ctx = viewGroup.getContext();
                            int imageResId = ctx.getResources().getIdentifier("image", "id", ctx.getPackageName());
                            String myTag = "FAKE_VIEW_ONCE_BTN_FINAL";

                            if (imageResId != 0) {
                                View originalImageView = viewGroup.findViewById(imageResId);
                                View existingBtn = viewGroup.findViewWithTag(myTag);

                                // Logic: अगर असली इमेज विज़िबल है, तो उसे छुपाओ और बटन दिखाओ।
                                // अगर असली इमेज नहीं है (यानी यह टेक्स्ट मैसेज है), तो बटन भी छुपा दो।
                                if (originalImageView != null && originalImageView.getVisibility() == View.VISIBLE) {
                                    
                                    // 1. इमेज छुपाओ
                                    originalImageView.setVisibility(View.GONE);

                                    // 2. बटन दिखाओ या बनाओ
                                    if (existingBtn == null) {
                                        TextView btn = new TextView(ctx);
                                        btn.setText("📷 Photo");
                                        btn.setTextColor(Color.WHITE);
                                        btn.setTypeface(null, Typeface.BOLD);
                                        btn.setTextSize(16);
                                        btn.setBackgroundColor(0xFF333333); // Dark Gray
                                        btn.setPadding(40, 25, 40, 25);
                                        btn.setGravity(Gravity.CENTER);
                                        btn.setTag(myTag);

                                        // Layout Params (Center in parent)
                                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                        );
                                        params.gravity = Gravity.CENTER;

                                        // 3. CLICK FIX: Parent पर क्लिक करवाएं
                                        // क्योंकि अक्सर क्लिक लिस्नर इमेज पर नहीं, उसके कंटेनर पर होता है
                                        final View clickTarget = (View) originalImageView.getParent();
                                        btn.setOnClickListener(v -> {
                                            if (clickTarget != null) {
                                                clickTarget.performClick();
                                            }
                                        });

                                        // बटन को व्यू में जोड़ें
                                        if (originalImageView.getParent() instanceof ViewGroup) {
                                            ((ViewGroup) originalImageView.getParent()).addView(btn, params);
                                        }
                                    } else {
                                        // बटन पहले से है, तो उसे विज़िबल करें
                                        existingBtn.setVisibility(View.VISIBLE);
                                        // यह सुनिश्चित करें कि यह सबसे ऊपर (front) रहे
                                        existingBtn.bringToFront();
                                    }
                                } else {
                                    // MIXING FIX: अगर यह इमेज मैसेज नहीं है (जैसे टेक्स्ट), तो हमारा बटन नहीं दिखना चाहिए
                                    if (existingBtn != null) {
                                        existingBtn.setVisibility(View.GONE);
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            // Ignore errors safely
                        }
                        // --- FIX END ---

                        for (OnConversationItemListener listener : conversationListeners) {
                            // listener को अभी भी post में रखें ताकि क्रैश न हो अगर user code heavy हो
                            viewGroup.post(() -> listener.onItemBind(fMessage, viewGroup));
                        }
                    }
                });
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Conversation Item Listener";
    }

    public abstract static class OnConversationItemListener {
        public abstract void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup);
    }
}
