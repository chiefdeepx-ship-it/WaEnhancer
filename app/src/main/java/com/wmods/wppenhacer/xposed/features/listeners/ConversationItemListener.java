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

                var method = mAdapter.getClass().getDeclaredMethod("getView", int.class, View.class, ViewGroup.class);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject != mAdapter) return;

                        // Result View (Asli View jo screen pe hai)
                        Object resultView = param.getResult();
                        if (!(resultView instanceof ViewGroup)) return;
                        final ViewGroup viewGroup = (ViewGroup) resultView;

                        var position = (int) param.args[0];
                        Object fMessageObj = mAdapter.getItem(position);
                        if (fMessageObj == null) return;
                        var fMessage = new FMessageWpp(fMessageObj);

                        // --- FINAL FIX: UI + CLICK SEARCH ---
                        try {
                            android.content.Context ctx = viewGroup.getContext();
                            int imageResId = ctx.getResources().getIdentifier("image", "id", ctx.getPackageName());
                            String myTag = "FAKE_VIEW_ONCE_BTN_V3";

                            if (imageResId != 0) {
                                View originalImageView = viewGroup.findViewById(imageResId);
                                View existingBtn = viewGroup.findViewWithTag(myTag);

                                // Check: Kya ye Image Message hai? (Text message me ye null ya GONE hoga)
                                if (originalImageView != null && originalImageView.getVisibility() == View.VISIBLE) {
                                    
                                    // 1. Asli Image ko HIDE karo
                                    originalImageView.setVisibility(View.GONE);

                                    // 2. Button Banao (Agar nahi hai)
                                    if (existingBtn == null) {
                                        TextView btn = new TextView(ctx);
                                        // UI Styling (Khali box fix)
                                        btn.setText(" \uD83D\uDCF7  Photo "); // Camera Icon + Text
                                        btn.setTextColor(Color.WHITE);
                                        btn.setTypeface(null, Typeface.BOLD);
                                        btn.setTextSize(15);
                                        btn.setBackgroundColor(0xFF252525); // Dark Gray Background
                                        btn.setPadding(30, 20, 30, 20); // Padding taaki text dikhe
                                        btn.setGravity(Gravity.CENTER);
                                        btn.setTag(myTag);

                                        // Layout Params (Taaki box sikud na jaye)
                                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                        );
                                        params.gravity = Gravity.CENTER;
                                        btn.setLayoutParams(params);

                                        // 3. CLICK FIX (MAGIC LOOP)
                                        // Hum upar ja kar dhundenge ki "Click" sunne wala kaun hai
                                        btn.setOnClickListener(v -> {
                                            View parent = (View) originalImageView.getParent();
                                            // Loop: Jab tak koi Click Listener wala view na mile, upar jao
                                            while (parent != null) {
                                                if (parent.hasOnClickListeners()) {
                                                    parent.performClick(); // Mil gaya! Click karo
                                                    break;
                                                }
                                                if (parent.getParent() instanceof View) {
                                                    parent = (View) parent.getParent();
                                                } else {
                                                    break;
                                                }
                                            }
                                        });

                                        // Button ko Parent me add karo
                                        if (originalImageView.getParent() instanceof ViewGroup) {
                                            ((ViewGroup) originalImageView.getParent()).addView(btn);
                                        }
                                    } else {
                                        // Agar button hai, to use Visible rakho
                                        existingBtn.setVisibility(View.VISIBLE);
                                    }
                                } else {
                                    // Agar ye Text message hai, aur galti se button ban gaya tha, to use hatao
                                    if (existingBtn != null) {
                                        existingBtn.setVisibility(View.GONE);
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            // Ignore
                        }
                        // --- END FIX ---

                        for (OnConversationItemListener listener : conversationListeners) {
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
