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

                // --- START: FINAL ID FIX (REPAIRED) ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. DP HIDE -> GONE (Jaisa original me tha - Space bhi khatam)
                    String[] killList = {
                        "contact_selector", 
                        "contact_photo_row", 
                        "contactpicker_row_photo", 
                        "photo_btn"
                    };
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

                    // 2. INVISIBLE LIST (Nayi IDs + Date/Msg added here)
                    // Inhe gayab karna hai par JAGAH rakhni hai
                    String[] invisibleList = {
                        "single_msg_tv",          // Message Text
                        "conversations_row_date", // Date Text
                        "date_time",              // Backup Date ID
                        "message_type_indicator", // Camera/Mic Icon (Screenshot se)
                        "status_indicator",       // Green/Grey Dot (Screenshot se)
                        "msg_from_tv"             // Group Sender Name (Screenshot se)
                    };

                    for (String id : invisibleList) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View target = view.findViewById(resId);
                            if (target != null) {
                                target.setVisibility(View.INVISIBLE); 
                            }
                        }
                    }

                    // 3. POSITIONING (Exact Original Logic Restored)
                    
                    // A. Container ko Shift karo
                    int textContainerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (textContainerId != 0) {
                        View textContainer = view.findViewById(textContainerId);
                        if (textContainer != null) {
                            textContainer.setTranslationX(60f); 
                            textContainer.setPadding(0, textContainer.getPaddingTop(), 120, textContainer.getPaddingBottom());
                        }
                    }

                    // B. Date ko wapas Shift karo (Original code logic wapas daal diya)
                    // Bhale hi ye Invisible hai, par code logic same rakh raha hu taaki crash/layout issue na ho
                    String[] dateIds = {"conversations_row_date", "date_time"};
                    for (String dId : dateIds) {
                        int dateResId = ctx.getResources().getIdentifier(dId, "id", ctx.getPackageName());
                        if (dateResId != 0) {
                            View dateView = view.findViewById(dateResId);
                            if (dateView != null) {
                                dateView.setTranslationX(-120f); // Ye line wapas add kar di
                            }
                        }
                    }

                } catch (Throwable t) {}
                // --- END: FINAL ID FIX ---

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
