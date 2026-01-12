package com.wmods.wppenhacer.xposed.features.listeners;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button; // TextView nahi, ab Button use karenge
import android.widget.FrameLayout;
import android.widget.HeaderViewListAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;

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

    public static ListAdapter getAdapter() { return mAdapter; }

    @Override
    public void doHook() throws Throwable {
        XposedHelpers.findAndHookMethod(ListView.class, "setAdapter", ListAdapter.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!WppCore.getCurrentActivity().getClass().getSimpleName().equals("Conversation")) return;
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

                        Object resultView = param.getResult();
                        if (!(resultView instanceof ViewGroup)) return;
                        final ViewGroup viewGroup = (ViewGroup) resultView;

                        var position = (int) param.args[0];
                        Object fMessageObj = mAdapter.getItem(position);
                        if (fMessageObj == null) return;
                        var fMessage = new FMessageWpp(fMessageObj);

                        // --- FINAL BUTTON FIX START ---
                        try {
                            Context ctx = viewGroup.getContext();
                            int imageResId = ctx.getResources().getIdentifier("image", "id", ctx.getPackageName());
                            String myTag = "FAKE_BTN_REAL_BUTTON";

                            if (imageResId != 0) {
                                View originalImageView = viewGroup.findViewById(imageResId);
                                View existingBtn = viewGroup.findViewWithTag(myTag);

                                // Logic: Sirf tab chalao jab Image VISIBLE ho (Yaani Photo Message hai)
                                if (originalImageView != null && originalImageView.getVisibility() == View.VISIBLE) {
                                    
                                    // 1. Image ko HIDE karo (GONE)
                                    originalImageView.setVisibility(View.GONE);

                                    // 2. Button Check
                                    if (existingBtn == null) {
                                        // **CHANGE:** Use 'Button' class instead of TextView
                                        Button btn = new Button(ctx);
                                        btn.setTag(myTag);

                                        // --- STYLING ---
                                        btn.setText("📷 PHOTO"); // Simple text
                                        btn.setTextColor(Color.WHITE); // White Text
                                        btn.setBackgroundColor(Color.DKGRAY); // Dark Grey Background
                                        btn.setTextSize(14);
                                        btn.setPadding(20, 10, 20, 10);
                                        btn.setGravity(Gravity.CENTER);
                                        
                                        // Remove default capitalization and shadow if any
                                        btn.setAllCaps(true);
                                        btn.setMinHeight(0); // Remove default button bulk
                                        btn.setMinWidth(0);

                                        // --- LAYOUT PARAMS (Force Size) ---
                                        ViewGroup parent = (ViewGroup) originalImageView.getParent();
                                        ViewGroup.LayoutParams params;
                                        
                                        // Parent detect karke sahi params lagao
                                        if (parent instanceof FrameLayout) {
                                            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT, 
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            );
                                            flp.gravity = Gravity.CENTER;
                                            params = flp;
                                        } else if (parent instanceof LinearLayout) {
                                            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT, 
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            );
                                            llp.gravity = Gravity.CENTER;
                                            params = llp;
                                        } else {
                                            // Generic Fallback
                                            params = new ViewGroup.LayoutParams(300, 120); // Fixed size fallback
                                        }
                                        
                                        btn.setLayoutParams(params);

                                        // --- CLICK FIX (Reflection) ---
                                        // Image ka 'soul' nikal kar click call karenge
                                        btn.setOnClickListener(v -> {
                                            try {
                                                // Method 1: Reflection on Image Listener
                                                Object listenerInfo = XposedHelpers.getObjectField(originalImageView, "mListenerInfo");
                                                if (listenerInfo != null) {
                                                    View.OnClickListener originalListener = (View.OnClickListener) XposedHelpers.getObjectField(listenerInfo, "mOnClickListener");
                                                    if (originalListener != null) {
                                                        originalListener.onClick(originalImageView);
                                                        return;
                                                    }
                                                }
                                                // Method 2: Fallback to Parent
                                                if (originalImageView.getParent() instanceof View) {
                                                    ((View)originalImageView.getParent()).performClick();
                                                }
                                            } catch (Throwable t) {
                                                // Method 3: Last Resort Fallback
                                                try {
                                                    ((View)originalImageView.getParent()).performClick();
                                                } catch(Throwable t2) {}
                                            }
                                        });

                                        // Button Add Karo
                                        parent.addView(btn);
                                        
                                        // Request Layout Update
                                        parent.requestLayout();

                                    } else {
                                        // Button Already Exists: Show it
                                        if(existingBtn.getVisibility() != View.VISIBLE) {
                                            existingBtn.setVisibility(View.VISIBLE);
                                            existingBtn.bringToFront();
                                        }
                                    }
                                } else {
                                    // Agar Image nahi hai (Text msg), to Button HATAO
                                    if (existingBtn != null) {
                                        existingBtn.setVisibility(View.GONE);
                                    }
                                }
                            }
                        } catch (Throwable t) { 
                            // Error handling
                        }
                        // --- FIX END ---

                        for (OnConversationItemListener listener : conversationListeners) {
                            viewGroup.post(() -> listener.onItemBind(fMessage, viewGroup));
                        }
                    }
                });
            }
        });
    }

    @NonNull @Override public String getPluginName() { return "Conversation Item Listener"; }
    public abstract static class OnConversationItemListener { public abstract void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup); }
}
