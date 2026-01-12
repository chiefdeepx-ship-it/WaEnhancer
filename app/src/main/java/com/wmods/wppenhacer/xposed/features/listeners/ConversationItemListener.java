package com.wmods.wppenhacer.xposed.features.listeners;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator; // यह ज़रूरी है
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils; // यह भी ज़रूरी है

import java.lang.reflect.Field;
import java.util.HashSet;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ConversationItemListener extends Feature {

    public static HashSet<OnConversationItemListener> conversationListeners = new HashSet<>();

    public ConversationItemListener(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        // 1. वो क्लास लोड करें जो मैसेज व्यू को हैंडल करती है (ViewHolder)
        var absViewHolderClass = Unobfuscator.loadAbsViewHolder(classLoader);
        
        // 2. वो मेथड लोड करें जो डेटा को व्यू में भरता है (bind method)
        var bindMethod = Unobfuscator.loadBindMethod(classLoader);
        
        // 3. वो फील्ड ढूंढें जिसमें असली मैसेज ऑब्जेक्ट होता है
        var fMessageField = Unobfuscator.loadFMessageField(classLoader);

        // 4. सीधे bind मेथड को हुक करें (यह बहुत तेज़ है)
        XposedBridge.hookMethod(bindMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // जिस व्यू होल्डर पर यह चल रहा है
                var viewHolder = param.thisObject;

                // उस व्यू होल्डर से मुख्य व्यू (ViewGroup) निकालें
                Field viewField = ReflectionUtils.findFieldUsingFilter(absViewHolderClass, field -> field.getType() == View.class);
                var view = (View) viewField.get(viewHolder);

                if (!(view instanceof ViewGroup)) return;
                ViewGroup viewGroup = (ViewGroup) view;

                // असली मैसेज ऑब्जेक्ट निकालें
                var fMessageObj = fMessageField.get(viewHolder);
                if (fMessageObj == null) return;
                var fMessage = new FMessageWpp(fMessageObj);

                // --- नया तेज़ फिक्स शुरू ---
                try {
                    android.content.Context ctx = viewGroup.getContext();
                    
                    // ID: image (थंबनेल वाली आईडी)
                    int imageResId = ctx.getResources().getIdentifier("image", "id", ctx.getPackageName());
                    
                    if (imageResId != 0) {
                        View originalImageView = viewGroup.findViewById(imageResId);
                        
                        if (originalImageView != null) {
                            // 1. तुरंत HIDE करें (फ्लिकरिंग बंद)
                            originalImageView.setVisibility(View.GONE);

                            // 2. हमारा नकली बटन टैग
                            String myTag = "FAKE_VIEW_ONCE_BTN_V2";
                            View existingBtn = viewGroup.findViewWithTag(myTag);

                            if (existingBtn == null) {
                                // 3. नया बटन बनाएं
                                TextView btn = new TextView(ctx);
                                btn.setText("📷 Photo");
                                btn.setTextColor(Color.WHITE);
                                btn.setTypeface(null, Typeface.BOLD);
                                btn.setTextSize(16);
                                btn.setBackgroundColor(0xFF333333); // डार्क ग्रे
                                btn.setPadding(40, 25, 40, 25);
                                btn.setGravity(Gravity.CENTER);
                                btn.setTag(myTag);
                                
                                // लेआउट: इसे सेंटर में रखें ताकि टाइम के साथ मिक्स न हो
                                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                );
                                params.gravity = Gravity.CENTER;
                                
                                // 4. क्लिक फिक्स (सबसे ज़रूरी)
                                // हम सीधे व्यू पर क्लिक नहीं करेंगे, बल्कि उसके पेरेंट (कंटेनर) पर क्लिक करेंगे।
                                // अक्सर क्लिक लिस्नर इमेज पर नहीं, बल्कि उसके कंटेनर पर होता है।
                                final View clickTarget = (View) originalImageView.getParent();
                                btn.setOnClickListener(v -> {
                                    if (clickTarget != null) {
                                        clickTarget.performClick();
                                    }
                                });

                                // 5. बटन को जोड़ें
                                if (originalImageView.getParent() instanceof ViewGroup) {
                                    ((ViewGroup) originalImageView.getParent()).addView(btn, params);
                                }
                            } else {
                                // अगर बटन पहले से है, तो बस उसे विज़िबल रखें
                                existingBtn.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                } catch (Throwable t) {
                    // एरर इग्नोर करें
                }
                // --- नया तेज़ फिक्स समाप्त ---

                for (OnConversationItemListener listener : conversationListeners) {
                    listener.onItemBind(fMessage, viewGroup);
                }
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
