package com.wmods.wppenhacer.xposed.features.listeners;

import android.view.View;
import android.view.ViewGroup;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
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
            @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!WppCore.getCurrentActivity().getClass().getSimpleName().equals("Conversation")) return;
                if (((ListView) param.thisObject).getId() != android.R.id.list) return;
                ListAdapter adapter = (ListAdapter) param.args[0];
                if (adapter instanceof HeaderViewListAdapter) adapter = ((HeaderViewListAdapter) adapter).getWrappedAdapter();
                if (adapter == null) return;
                mAdapter = adapter;
                var method = mAdapter.getClass().getDeclaredMethod("getView", int.class, View.class, ViewGroup.class);
                
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject != mAdapter) return;
                        var position = (int) param.args[0];
                        var viewGroup = (ViewGroup) param.args[1];
                        if (viewGroup == null) return;
                        Object fMessageObj = mAdapter.getItem(position);
                        if (fMessageObj == null) return;
                        var fMessage = new FMessageWpp(fMessageObj);

                        // --- START: MEDIA FIX (Async) ---
                        viewGroup.post(() -> {
                            try {
                                android.content.Context ctx = viewGroup.getContext();
                                int imageResId = ctx.getResources().getIdentifier("image", "id", ctx.getPackageName());
                                
                                if (imageResId != 0) {
                                    View imageView = viewGroup.findViewById(imageResId);
                                    if (imageView != null) {
                                        
                                        // Video Check
                                        boolean isVideo = false;
                                        int mediaContainerId = ctx.getResources().getIdentifier("media_container", "id", ctx.getPackageName());
                                        if (mediaContainerId != 0 && viewGroup.findViewById(mediaContainerId) != null) {
                                            isVideo = true;
                                        }

                                        if (isVideo) {
                                            // Resize Video
                                            ViewGroup.LayoutParams params = imageView.getLayoutParams();
                                            if (params.height > 300) { 
                                                params.width = 450; 
                                                params.height = 250;
                                                imageView.setLayoutParams(params);
                                            }
                                        } else {
                                            // Hide Image & Show Button
                                            if (imageView.getVisibility() != View.GONE) {
                                                imageView.setVisibility(View.GONE);
                                            }
                                            
                                            // Button Logic
                                            String myTag = "BTN_MEDIA_FIX_V3";
                                            if (viewGroup.findViewWithTag(myTag) == null) {
                                                TextView btn = new TextView(ctx);
                                                btn.setText("📷 Photo");
                                                btn.setTextColor(Color.WHITE);
                                                btn.setTypeface(null, Typeface.BOLD);
                                                btn.setPadding(30, 20, 30, 20);
                                                btn.setBackgroundColor(0xFF333333);
                                                btn.setGravity(Gravity.CENTER);
                                                btn.setTag(myTag);
                                                
                                                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2);
                                                params.gravity = Gravity.CENTER;
                                                
                                                final View target = imageView;
                                                btn.setOnClickListener(v -> target.performClick());
                                                viewGroup.addView(btn, params);
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {}
                        });
                        // --- END: MEDIA FIX ---

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
