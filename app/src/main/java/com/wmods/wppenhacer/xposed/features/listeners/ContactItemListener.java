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

                // --- START: FINAL HOME SCREEN FIX ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. DP HIDE & KILL (Set Width to 0 to stop clicks)
                    String[] dpIds = {"contact_photo", "photo_btn", "avatar", "profile_picture", "contactpicker_row_photo"};
                    for (String id : dpIds) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View v = view.findViewById(resId);
                            if (v != null) {
                                v.setVisibility(View.GONE);
                                // Size zero karna padega taaki click na ho
                                ViewGroup.LayoutParams params = v.getLayoutParams();
                                if (params != null) {
                                    params.width = 0;
                                    v.setLayoutParams(params);
                                }
                            }
                        }
                    }

                    // 2. MOVE TEXT LEFT (Khali jagah bharein)
                    String[] textIds = {"conversations_row_contact_name", "contact_row_container", "contactpicker_row_name"};
                    for (String id : textIds) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View v = view.findViewById(resId);
                            if (v != null) {
                                v.setTranslationX(-130f); // Left shift
                            }
                        }
                    }

                    // 3. MOVE DATE RIGHT (Right side ki jagah bharein)
                    String[] dateIds = {"conversations_row_date_divider", "date_time"};
                    for (String id : dateIds) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View v = view.findViewById(resId);
                            if (v != null) {
                                // Date ko right side dhakka maarein
                                v.setTranslationX(100f); 
                            }
                        }
                    }

                } catch (Throwable t) {}
                // --- END: FINAL HOME SCREEN FIX ---

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
