package com.wmods.wppenhacer.xposed.features.listeners;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import java.util.HashSet;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ContactItemListener extends Feature {

    public static HashSet<OnContactItemListener> contactListeners = new HashSet<>();

    public ContactItemListener(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var onChangeStatus = Unobfuscator.loadOnChangeStatus(classLoader);
        var field1 = Unobfuscator.loadViewHolderField1(classLoader);
        var absViewHolderClass = Unobfuscator.loadAbsViewHolder(classLoader);

        XposedBridge.hookMethod(onChangeStatus, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var viewHolder = field1.get(param.thisObject);
                var object = param.args[0];
                var waContact = new WaContactWpp(object);
                var viewField = ReflectionUtils.findFieldUsingFilter(absViewHolderClass, field -> field.getType() == View.class);
                var view = (View) viewField.get(viewHolder);

                // --- START: FINAL BALANCED LAYOUT FIX ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. DP HIDE (Same as before)
                    String[] killList = {"contact_selector", "contact_photo_row", "contactpicker_row_photo", "photo_btn"};
                    for (String id : killList) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View target = view.findViewById(resId);
                            if (target != null) {
                                target.setVisibility(View.GONE);
                                ViewGroup.LayoutParams lp = target.getLayoutParams();
                                if (lp != null) { lp.width = 0; target.setLayoutParams(lp); }
                            }
                        }
                    }

                    // 2. NAME FIX (Keep Left Side Clean)
                    int textContainerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (textContainerId != 0) {
                        View textContainer = view.findViewById(textContainerId);
                        if (textContainer != null) {
                            // Step A: Container ko Right shift karo (Naam sahi jagah aayega)
                            // Aapne 55 bola tha, main 60 kar raha hu safety ke liye
                            textContainer.setTranslationX(60f); 
                            
                            // Step B: ***NEW*** RIGHT PADDING ADD KARO
                            // Kyunki humne dabba right khiska diya, to right side ka text kat raha hai.
                            // Hum Right side me 80px ki padding de rahe hain taaki text pehle hi ruk jaye.
                            textContainer.setPadding(0, textContainer.getPaddingTop(), 100, textContainer.getPaddingBottom());
                        }
                    }

                    // 3. DATE/TIMESTAMP FIX (Corner se bahar nikalo)
                    int dateId = ctx.getResources().getIdentifier("date_time", "id", ctx.getPackageName());
                    if (dateId != 0) {
                        View dateView = view.findViewById(dateId);
                        if (dateView != null) {
                            // Logic: Container +60 gaya hai.
                            // Hamein Date ko wapas lana hai (-60) + thoda aur gap dena hai (-40).
                            // Total = -100 ya -120 safely.
                            dateView.setTranslationX(-120f);
                        }
                    }

                } catch (Throwable t) {}
                // --- END: FINAL BALANCED LAYOUT FIX ---

                var userJid = waContact.getUserJid();
                if (userJid.isNull()) return;

                for (OnContactItemListener listener : contactListeners) {
                    listener.onBind(waContact, view);
                }
            }
        });
    }

    @NonNull @Override public String getPluginName() { return "Contact Item Listener"; }
    public abstract static class OnContactItemListener { public abstract void onBind(WaContactWpp waContact, View view); }
}
