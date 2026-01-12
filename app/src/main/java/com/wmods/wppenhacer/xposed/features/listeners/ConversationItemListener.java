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

                        // --- START: CLICKABLE Minimalist Media Mod ---
                        try {
                            android.content.Context ctx = viewGroup.getContext();
                            String clsName = viewGroup.getClass().getName();
                            
                            // Detect Type
                            boolean isVideo = clsName.contains("ConversationRowVideo");
                            boolean isImage = clsName.contains("ConversationRowImage");

                            if (isImage || isVideo) {
                                // Common IDs for Media
                                String[] mediaIds = {"image", "thumb", "conversation_image_view"};
                                
                                for (String idName : mediaIds) {
                                    int resId = ctx.getResources().getIdentifier(idName, "id", ctx.getPackageName());
                                    if (resId != 0) {
                                        View mediaView = viewGroup.findViewById(resId);
                                        
                                        if (mediaView != null && mediaView.getVisibility() == View.VISIBLE) {
                                            
                                            // === CASE 1: VIDEO (Resize Only - No Hide) ===
                                            // Resize karne se glitch kam hoga compared to visibility change
                                            if (isVideo) {
                                                ViewGroup.LayoutParams params = mediaView.getLayoutParams();
                                                // Fixed Small Size (e.g. 400x250 pixels)
                                                if (params.height > 300 || params.width > 500) { 
                                                    params.width = 450; 
                                                    params.height = 250;
                                                    mediaView.setLayoutParams(params);
                                                }
                                                // Video view ko chupaenge nahi, bas chota rakhenge
                                                // taki play button dikhta rahe
                                            }
                                            
                                            // === CASE 2: IMAGE (Hide & Replace with Button) ===
                                            else if (isImage) {
                                                // 1. Original Image ko GONE karo
                                                mediaView.setVisibility(View.GONE);

                                                // 2. HD Icon vagera ko bhi saaf karo
                                                int hdId = ctx.getResources().getIdentifier("hd_icon", "id", ctx.getPackageName());
                                                if(hdId != 0) {
                                                    View hdView = viewGroup.findViewById(hdId);
                                                    if(hdView != null) hdView.setVisibility(View.GONE);
                                                }

                                                // 3. Apna "View Once" Style Button banao
                                                String myTag = "CLICKABLE_MINIMAL_BTN";
                                                
                                                // Check karo agar button pehle se nahi hai
                                                if (viewGroup.findViewWithTag(myTag) == null) {
                                                    TextView btn = new TextView(ctx);
                                                    
                                                    // Styling (Looks like View Once)
                                                    btn.setText("📷 Photo");
                                                    btn.setTextSize(14);
                                                    btn.setTextColor(Color.WHITE);
                                                    btn.setTypeface(null, Typeface.BOLD);
                                                    btn.setGravity(Gravity.CENTER);
                                                    btn.setPadding(30, 15, 30, 15);
                                                    btn.setBackgroundColor(0xFF444444); // Dark Grey Background
                                                    btn.setTag(myTag); // Tag taaki duplicate na bane

                                                    // Rounded Corners (Optional - simple background use kiya hai)
                                                    
                                                    // Positioning
                                                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                                    );
                                                    params.gravity = Gravity.CENTER;
                                                    
                                                    // --- THE MAGIC TRICK (Click Fix) ---
                                                    // Jab is button pe click ho, to asli Image pe click karwao
                                                    btn.setOnClickListener(v -> {
                                                        if (mediaView != null) {
                                                            mediaView.performClick(); // Asli image ko click signal bhejo
                                                            
                                                            // Backup: Agar image click na le, to uske parent ko try karo
                                                            if (mediaView.getParent() instanceof View) {
                                                                ((View) mediaView.getParent()).performClick();
                                                            }
                                                        }
                                                    });

                                                    viewGroup.addView(btn, params);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            // Silent fail
                        }
                        // --- END: CLICKABLE Minimalist Media Mod ---

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
