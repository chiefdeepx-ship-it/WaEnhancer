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

                // --- START: SIMPLE & SAFE FIX ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. DP BOX KO GAYAB KARO (Ghost Click Fix)
                    String[] boxIds = {"contact_selector", "contact_photo_row", "contactpicker_row_photo"};
                    for (String id : boxIds) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View box = view.findViewById(resId);
                            if (box != null) {
                                box.setVisibility(View.GONE);
                                ViewGroup.LayoutParams lp = box.getLayoutParams();
                                if (lp != null) {
                                    lp.width = 0; // Size 0 karte hi jagah khali ho jayegi
                                    box.setLayoutParams(lp);
                                }
                            }
                        }
                    }

                    // 2. TEXT KO SET KARO (Screen se bahar nahi jayega)
                    int textContainerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (textContainerId != 0) {
                        View textContainer = view.findViewById(textContainerId);
                        if (textContainer != null) {
                            // Position Normal karo (0f)
                            textContainer.setTranslationX(0f);
                            
                            // Left side se thoda gap do (Padding) taaki kinare se na chipke
                            textContainer.setPadding(25, textContainer.getPaddingTop(), textContainer.getPaddingRight(), textContainer.getPaddingBottom());
                        }
                    }

                    // 3. DATE ADJUSTMENT
                    int dateId = ctx.getResources().getIdentifier("date_time", "id", ctx.getPackageName());
                    if (dateId != 0) {
                        View dateView = view.findViewById(dateId);
                        if (dateView != null) {
                            dateView.setTranslationX(0f);
                        }
                    }

                } catch (Throwable t) {
                    // Ignore errors
                }
                // --- END: SIMPLE & SAFE FIX ---

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
