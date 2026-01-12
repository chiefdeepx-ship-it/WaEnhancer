package com.wmods.wppenhacer.xposed.features.listeners;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

                try {
                    android.content.Context ctx = view.getContext();
                    android.content.res.Resources res = ctx.getResources();
                    String pkg = ctx.getPackageName();

                    // --- 1. KILL LIST (DPs / PHOTOS) ---
                    // Strategy: GONE + Width 0 + Height 0 + Clear Animation
                    String[] killList = {
                        "contact_selector", 
                        "contact_photo_row", 
                        "contactpicker_row_photo", 
                        "photo_btn",
                        "avatar_contact",       // Extra ID possibility
                        "img_contact"           // Extra ID possibility
                    };

                    for (String id : killList) {
                        int resId = res.getIdentifier(id, "id", pkg);
                        if (resId != 0) {
                            View target = view.findViewById(resId);
                            if (target != null) {
                                target.clearAnimation(); // Stop WhatsApp animation
                                target.setVisibility(View.GONE);
                                
                                // FORCE SIZE TO 0 (Nuclear option)
                                ViewGroup.LayoutParams lp = target.getLayoutParams();
                                if (lp != null) { 
                                    lp.width = 0; 
                                    lp.height = 0; 
                                    target.setLayoutParams(lp); 
                                }
                            }
                        }
                    }

                    // --- 2. INVISIBLE LIST (TEXT, DATE, TICKS) ---
                    // Strategy: Alpha 0 (Transparent) + Invisible + Clear Animation
                    String[] invisibleList = {
                        "single_msg_tv",          // Message Text
                        "conversations_row_date", // Date Text
                        "date_time",              // Backup Date
                        "message_type_indicator", // Mic/Camera Icon
                        "status_indicator",       // Main Ticks ID
                        "msg_from_tv",            // Group Sender Name
                        "message_status_indicator", // Another Ticks ID (Common in mods)
                        "img_status",             // Extra Ticks ID
                        "ic_status"               // Extra Ticks ID
                    };

                    for (String id : invisibleList) {
                        int resId = res.getIdentifier(id, "id", pkg);
                        if (resId != 0) {
                            View target = view.findViewById(resId);
                            if (target != null) {
                                target.clearAnimation(); // Stop any fade-in glitch
                                target.setAlpha(0f);     // 100% Transparent immediately
                                
                                // Check if visible, then hide
                                if (target.getVisibility() != View.INVISIBLE) {
                                    target.setVisibility(View.INVISIBLE);
                                }
                            }
                        }
                    }

                    // --- 3. LAYOUT FIXES ---
                    
                    // A. Name Container Shift (+60)
                    int textContainerId = res.getIdentifier("contact_row_container", "id", pkg);
                    if (textContainerId != 0) {
                        View textContainer = view.findViewById(textContainerId);
                        if (textContainer != null) {
                            textContainer.setTranslationX(60f); 
                            textContainer.setPadding(0, textContainer.getPaddingTop(), 120, textContainer.getPaddingBottom());
                        }
                    }

                    // B. Date Shift (-120) - Restore Position
                    String[] dateIds = {"conversations_row_date", "date_time"};
                    for (String dId : dateIds) {
                        int dateResId = res.getIdentifier(dId, "id", pkg);
                        if (dateResId != 0) {
                            View dateView = view.findViewById(dateResId);
                            if (dateView != null) {
                                dateView.setTranslationX(-120f);
                            }
                        }
                    }

                } catch (Throwable t) {
                    // Ignore errors silently to prevent crash
                }

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
