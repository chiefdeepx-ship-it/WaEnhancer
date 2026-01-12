package com.wmods.wppenhacer.xposed.features.listeners;

import android.view.View;
import android.view.ViewGroup;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView; // टेक्स्ट बटन बनाने के लिए
import android.graphics.Color; // रंगों के लिए
import android.view.Gravity; // एलाइनमेंट के लिए
import android.graphics.Typeface; // बोल्ड टेक्स्ट के लिए
import android.widget.FrameLayout; // लेआउट के लिए

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

                        // --- यहाँ से नया कोड शुरू (MEDIA VIEW-ONCE STYLE UI) ---
                        viewGroup.post(() -> {
                            try {
                                android.content.Context ctx = viewGroup.getContext();
                                
                                // ID: image (जैसा आपने स्क्रीनशॉट में दिखाया)
                                int imageResId = ctx.getResources().getIdentifier("image", "id", ctx.getPackageName());
                                
                                if (imageResId != 0) {
                                    // असली Image View ढूँढो
                                    View originalImageView = viewGroup.findViewById(imageResId);
                                    
                                    if (originalImageView != null) {
                                        // 1. असली Image को HIDE कर दो (ताकि बड़ी फोटो न दिखे)
                                        if (originalImageView.getVisibility() != View.GONE) {
                                            originalImageView.setVisibility(View.GONE);
                                        }

                                        // 2. चेक करो कि क्या हमने अपना बटन पहले ही लगा दिया है?
                                        String myTag = "FAKE_VIEW_ONCE_BTN";
                                        View existingBtn = viewGroup.findViewWithTag(myTag);

                                        if (existingBtn == null) {
                                            // 3. नया बटन बनाओ (जो View Once जैसा दिखे)
                                            TextView btn = new TextView(ctx);
                                            
                                            // टेक्स्ट और स्टाइलिंग
                                            btn.setText("📷 Photo"); 
                                            btn.setTextColor(Color.WHITE); // सफ़ेद टेक्स्ट
                                            btn.setTypeface(null, Typeface.BOLD);
                                            btn.setTextSize(16);
                                            
                                            // बैकग्राउंड (डार्क ग्रे जैसा View Once में होता है)
                                            btn.setBackgroundColor(0xFF333333); 
                                            btn.setPadding(40, 25, 40, 25); // बटन को थोड़ा बड़ा दिखाने के लिए पैडिंग
                                            btn.setGravity(Gravity.CENTER_VERTICAL);
                                            
                                            // टैग सेट करो ताकि डुप्लीकेट बटन न बनें
                                            btn.setTag(myTag);
                                            
                                            // लेआउट पैरामीटर्स
                                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT, 
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            );
                                            params.gravity = Gravity.CENTER; // बीच में दिखेगा
                                            
                                            // 4. क्लिक एक्शन (सबसे ज़रूरी)
                                            // जब इस बटन पे क्लिक हो, तो वो छुपी हुई इमेज पे क्लिक ट्रिगर करे
                                            final View target = originalImageView;
                                            btn.setOnClickListener(v -> {
                                                if (target != null) {
                                                    target.performClick(); // असली फोटो ओपन करेगा
                                                }
                                            });

                                            // 5. बटन को व्यू में जोड़ें
                                            if (originalImageView.getParent() instanceof ViewGroup) {
                                                ((ViewGroup) originalImageView.getParent()).addView(btn, params);
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                // कोई एरर आए तो इग्नोर करें (ताकि ऐप क्रैश न हो)
                            }
                        });
                        // --- नया कोड समाप्त ---

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
