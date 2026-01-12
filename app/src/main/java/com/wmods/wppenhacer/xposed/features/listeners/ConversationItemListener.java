package com.wmods.wppenhacer.xposed.features.listeners;

import android.view.View;
import android.view.ViewGroup;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

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
                        var position = (int) param.args[0];
                        var viewGroup = (ViewGroup) param.args[1];
                        if (viewGroup == null) return;
                        Object fMessageObj = mAdapter.getItem(position);
                        if (fMessageObj == null) return;
                        var fMessage = new FMessageWpp(fMessageObj);
// --- START: Minimalist Chat Mod ---
                        try {
                            // 1. Check karein ki ye Image ya Video wala row hai kya
                            String clsName = viewGroup.getClass().getName();
                            boolean isImage = clsName.contains("ConversationRowImage");
                            boolean isVideo = clsName.contains("ConversationRowVideo");

                            if (isImage || isVideo) {
                                android.content.Context ctx = viewGroup.getContext();
                                
                                // 2. "image" ya "thumb" ID wala view dhund ke chupayein
                                // WhatsApp me usually main image ka ID "image" ya "thumb" hota hai
                                int imgId = ctx.getResources().getIdentifier("image", "id", ctx.getPackageName());
                                if (imgId == 0) {
                                    imgId = ctx.getResources().getIdentifier("thumb", "id", ctx.getPackageName());
                                }
                                
                                if (imgId != 0) {
                                    View imgView = viewGroup.findViewById(imgId);
                                    if (imgView != null) {
                                        imgView.setVisibility(View.GONE);
                                    }
                                }

                                // 3. Apna Text Add karein (Agar pehle se nahi hai)
                                String myTag = "MINIMAL_TEXT";
                                if (viewGroup.findViewWithTag(myTag) == null) {
                                    android.widget.TextView tv = new android.widget.TextView(ctx);
                                    tv.setText(isImage ? "=> 📷 Image" : "=> 🎥 Video");
                                    tv.setTextSize(14); // Text size
                                    tv.setTextColor(android.graphics.Color.DKGRAY); // Color
                                    tv.setBackgroundColor(0x20000000); // Halka sa background
                                    tv.setPadding(15, 10, 15, 10);
                                    tv.setTag(myTag); // Tag lagaya taaki duplicate na bane
                                    
                                    // Layout Params taaki ye beech me dikhe
                                    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                                    );
                                    params.gravity = android.view.Gravity.CENTER;
                                    viewGroup.addView(tv, params);
                                }
                            }
                        } catch (Throwable t) {
                            // Agar koi error aaye to crash mat hona, bas ignore karna
                        }
                        // --- END: Minimalist Chat Mod ---
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
        /**
         * Called when a message item is rendered in the conversation
         *
         * @param fMessage  The message
         * @param viewGroup The view associated with the item
         */
        public abstract void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup);
    }
}
