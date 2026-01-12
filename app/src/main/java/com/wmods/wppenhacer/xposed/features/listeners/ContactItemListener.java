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

                // --- START: BALANCED HOME SCREEN FIX ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. DP HIDE (Home + Contact List)
                    String[] killList = {
                        "contact_selector",         // Main Box
                        "contact_photo_row", 
                        "contactpicker_row_photo",  // New Chat
                        "photo_btn"
                    };

                    for (String id : killList) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View target = view.findViewById(resId);
                            if (target != null) {
                                target.setVisibility(View.GONE);
                                ViewGroup.LayoutParams lp = target.getLayoutParams();
                                if (lp != null) { 
                                    lp.width = 0; // Size 0 karte hi Text left bhaagta hai (ise niche rokenge)
                                    target.setLayoutParams(lp); 
                                }
                            }
                        }
                    }

                    // 2. NAME FIX (Text ko wapas Right dhakka do)
                    int textContainerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (textContainerId != 0) {
                        View textContainer = view.findViewById(textContainerId);
                        if (textContainer != null) {
                            // Padding 0 kar do taaki calculation easy ho
                            textContainer.setPadding(0, textContainer.getPaddingTop(), textContainer.getPaddingRight(), textContainer.getPaddingBottom());
                            
                            // ISKO DEKHO: +55f (Positive) matlab Right Side Shift
                            // Ye text ko diwar se door karega
                            textContainer.setTranslationX(55f); 
                        }
                    }

                    // 3. DATE FIX (Time ko wapas Left khicho)
                    int dateId = ctx.getResources().getIdentifier("date_time", "id", ctx.getPackageName());
                    if (dateId != 0) {
                        View dateView = view.findViewById(dateId);
                        if (dateView != null) {
                            // ISKO DEKHO: -45f (Negative) matlab Left Side Pull
                            // Ye date ko screen ke andar wapas layega
                            dateView.setTranslationX(-45f);
                        }
                    }

                } catch (Throwable t) {}
                // --- END: BALANCED HOME SCREEN FIX ---

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
