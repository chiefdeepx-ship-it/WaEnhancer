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

                // --- START: ALL-IN-ONE REMOVER (Based on Screenshots) ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. LIST OF IDs TO HIDE (Home + Contact List + Profile + Chat Bar)
                    String[] killList = {
                        "contact_selector",            // Home Screen DP Box
                        "contact_photo_row",           // Home Screen Wrapper
                        "contactpicker_row_photo",     // Contact List DP (Screenshot wala ID)
                        "wds_profile_picture",         // Profile Screen Large DP (Screenshot wala ID)
                        "collapsing_profile_photo_view", // Profile Header Box
                        "conversation_contact_photo",  // Chat Screen Top Bar DP
                        "conversation_profile_picture",// Chat Screen Alternate ID
                        "photo_btn",                   // Generic
                        "avatar"                       // Generic
                    };

                    for (String id : killList) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View target = view.findViewById(resId);
                            if (target != null) {
                                target.setVisibility(View.GONE);
                                
                                // Size bhi 0 kar do taaki jagah na bache
                                ViewGroup.LayoutParams lp = target.getLayoutParams();
                                if (lp != null) {
                                    lp.width = 0;
                                    target.setLayoutParams(lp);
                                }
                            }
                        }
                    }

                    // 2. TEXT ADJUSTMENT (Naam sahi jagah dikhe)
                    int textContainerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (textContainerId != 0) {
                        View textContainer = view.findViewById(textContainerId);
                        if (textContainer != null) {
                            textContainer.setTranslationX(0f);
                            // Padding 40 pixels rakhi hai taaki screen ke kinare se na chipke
                            textContainer.setPadding(40, textContainer.getPaddingTop(), textContainer.getPaddingRight(), textContainer.getPaddingBottom());
                        }
                    }

                    // 3. DATE/TIME FIX (Jo Right me ghus raha hai)
                    int dateId = ctx.getResources().getIdentifier("date_time", "id", ctx.getPackageName());
                    if (dateId != 0) {
                        View dateView = view.findViewById(dateId);
                        if (dateView != null) {
                            // Negative Value = Left Side Pull
                            // Ise -20 kar diya taaki ye right edge se door aa jaye
                            dateView.setTranslationX(-20f);
                        }
                    }

                } catch (Throwable t) {
                    // Ignore
                }
                // --- END: ALL-IN-ONE REMOVER ---

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
