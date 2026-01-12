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

                // --- START: FINAL ID FIX ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. DP HIDE
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

                    // 2. CONTAINER SHIFT (Left se gap banao, Right se kato mat)
                    int textContainerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (textContainerId != 0) {
                        View textContainer = view.findViewById(textContainerId);
                        if (textContainer != null) {
                            // Left se 60px khiskao (Naam ke liye jagah)
                            textContainer.setTranslationX(60f); 
                            
                            // Right side se 120px padding do taaki dabba screen ke bahar na nikle
                            textContainer.setPadding(0, textContainer.getPaddingTop(), 120, textContainer.getPaddingBottom());
                        }
                    }

                    // 3. DATE FIX (Correct ID: conversations_row_date)
                    // Hum purani aur nayi dono IDs check kar lenge safety ke liye
                    String[] dateIds = {"conversations_row_date", "date_time"};
                    
                    for (String dId : dateIds) {
                        int dateResId = ctx.getResources().getIdentifier(dId, "id", ctx.getPackageName());
                        if (dateResId != 0) {
                            View dateView = view.findViewById(dateResId);
                            if (dateView != null) {
                                // Container +60 gaya hai, Date ko wapas -120 lao taaki wo dikhe
                                dateView.setTranslationX(-120f);
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
