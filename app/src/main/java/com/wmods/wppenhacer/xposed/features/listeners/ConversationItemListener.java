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

                        // --- START: ID-BASED MEDIA MOD ---
                        try {
                            android.content.Context ctx = viewGroup.getContext();
                            
                            // 1. Direct ID Search (from your screenshots)
                            int imageResId = ctx.getResources().getIdentifier("image", "id", ctx.getPackageName());
                            int mediaContainerId = ctx.getResources().getIdentifier("media_container", "id", ctx.getPackageName()); // For Video
                            
                            if (imageResId != 0) {
                                View imageView = viewGroup.findViewById(imageResId);
                                
                                // Check if Image exists and is visible
                                if (imageView != null && imageView.getVisibility() == View.VISIBLE) {
                                    
                                    // 2. DETECT VIDEO vs IMAGE
                                    // Agar 'media_container' exist karta hai, to ye VIDEO hai
                                    boolean isVideo = false;
                                    if (mediaContainerId != 0 && viewGroup.findViewById(mediaContainerId) != null) {
                                        isVideo = true;
                                    }

                                    // 3. APPLY LOGIC
                                    if (isVideo) {
                                        // === VIDEO: Resize Only ===
                                        ViewGroup.LayoutParams params = imageView.getLayoutParams();
                                        // Agar size bada hai, to use chota kar do (Thumbnail size)
                                        if (params.height > 300) { 
                                            params.width = 450; 
                                            params.height = 250;
                                            imageView.setLayoutParams(params);
                                        }
                                    } else {
                                        // === IMAGE: Hide & Button ===
                                        imageView.setVisibility(View.GONE); // Hide original
                                        
                                        // Button Banao
                                        String myTag = "BTN_MEDIA_FIX";
                                        if (viewGroup.findViewWithTag(myTag) == null) {
                                            TextView btn = new TextView(ctx);
                                            btn.setText("📷 View Photo");
                                            btn.setTextColor(Color.WHITE);
                                            btn.setTypeface(null, Typeface.BOLD);
                                            btn.setPadding(30, 20, 30, 20);
                                            btn.setBackgroundColor(0xFF333333); // Dark Gray
                                            btn.setTag(myTag);
                                            
                                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2);
                                            params.gravity = Gravity.CENTER;
                                            
                                            // Click Fix: Trigger Hidden Image
                                            final View target = imageView;
                                            btn.setOnClickListener(v -> target.performClick());

                                            viewGroup.addView(btn, params);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) {}
                        // --- END: ID-BASED MEDIA MOD ---

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
