package com.wmods.wppenhacer.xposed.features.listeners;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
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

                        // --- TEXT VISIBILITY FIX START ---
                        try {
                            Context ctx = viewGroup.getContext();
                            int imageResId = ctx.getResources().getIdentifier("image", "id", ctx.getPackageName());
                            String myTag = "FAKE_BTN_TEXT_FIX";

                            if (imageResId != 0) {
                                View originalImageView = viewGroup.findViewById(imageResId);
                                View existingBtn = viewGroup.findViewWithTag(myTag);

                                // Check: Image Visible hai?
                                if (originalImageView != null && originalImageView.getVisibility() == View.VISIBLE) {
                                    
                                    // 1. Image Gayab
                                    originalImageView.setVisibility(View.GONE);

                                    // 2. Button Check
                                    if (existingBtn == null) {
                                        TextView btn = new TextView(ctx);
                                        btn.setTag(myTag);

                                        // --- UI STYLING (THE TEXT FIX) ---
                                        // Unicode Camera Icon + Text
                                        btn.setText(" \uD83D\uDCF7 Photo "); 
                                        
                                        // Color Hardcode (White Text, Dark Grey BG)
                                        btn.setTextColor(Color.WHITE); 
                                        btn.setBackgroundColor(Color.parseColor("#333333"));
                                        
                                        // Font Style
                                        btn.setTypeface(Typeface.DEFAULT_BOLD);
                                        btn.setTextSize(14); // Size thoda safe rakha hai
                                        
                                        // Alignment inside the box
                                        btn.setGravity(Gravity.CENTER);
                                        
                                        // Padding (Taaki text chipke nahi)
                                        btn.setPadding(30, 20, 30, 20);

                                        // --- LAYOUT PARAMS FIX (Parent ke hisaab se size) ---
                                        ViewGroup parent = (ViewGroup) originalImageView.getParent();
                                        ViewGroup.LayoutParams params;

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
                                        } else if (parent instanceof RelativeLayout) {
                                            RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT, 
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            );
                                            rlp.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                                            params = rlp;
                                        } else {
                                            // Fallback
                                            params = new ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT, 
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            );
                                        }
                                        
                                        btn.setLayoutParams(params);

                                        // --- CLICK FIX (Reflection) ---
                                        btn.setOnClickListener(v -> {
                                            try {
                                                Object listenerInfo = XposedHelpers.getObjectField(originalImageView, "mListenerInfo");
                                                if (listenerInfo != null) {
                                                    View.OnClickListener originalListener = (View.OnClickListener) XposedHelpers.getObjectField(listenerInfo, "mOnClickListener");
                                                    if (originalListener != null) {
                                                        originalListener.onClick(originalImageView);
                                                    } else {
                                                        ((View)originalImageView.getParent()).performClick();
                                                    }
                                                }
                                            } catch (Throwable t) {
                                                if (originalImageView.getParent() instanceof View) {
                                                    ((View)originalImageView.getParent()).performClick();
                                                }
                                            }
                                        });

                                        // Add to Parent
                                        parent.addView(btn);

                                    } else {
                                        // Button hai to Visible karo
                                        existingBtn.setVisibility(View.VISIBLE);
                                        existingBtn.bringToFront();
                                    }
                                } else {
                                    // Text Message hai to Button hatao
                                    if (existingBtn != null) existingBtn.setVisibility(View.GONE);
                                }
                            }
                        } catch (Throwable t) { }
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
