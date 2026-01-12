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

                // --- START: FORCE SHIFT FIX ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. DP BOX HIDE (Home Screen + New Chat)
                    String[] hideIds = {
                        "contact_selector",         // Home Screen DP Box
                        "contact_photo_row",        // Home Screen Wrapper
                        "contactpicker_row_photo",  // New Chat DP (Important!)
                        "contact_photo"             // Common ID
                    };
                    
                    for (String id : hideIds) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View target = view.findViewById(resId);
                            if (target != null) {
                                target.setVisibility(View.GONE);
                                // Width 0 karo taaki jagah na bache
                                ViewGroup.LayoutParams lp = target.getLayoutParams();
                                if (lp != null) { 
                                    lp.width = 0; 
                                    target.setLayoutParams(lp); 
                                }
                            }
                        }
                    }

                    // 2. TEXT FORCE SHIFT (Padding fail hua, ab Translation use karenge)
                    int textContainerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (textContainerId != 0) {
                        View textContainer = view.findViewById(textContainerId);
                        if (textContainer != null) {
                            // Reset Padding (Taaki purana kachra saaf ho)
                            textContainer.setPadding(0, textContainer.getPaddingTop(), textContainer.getPaddingRight(), textContainer.getPaddingBottom());
                            
                            // FORCE MOVE RIGHT: 45 pixels ka gap banayega left side se
                            // Positive value (+) matlab Right side shift
                            textContainer.setTranslationX(45f);
                        }
                    }
                    
                    // 3. New Chat Name Shift (Agar New Chat me text chipak raha hai)
                    int pickerNameId = ctx.getResources().getIdentifier("contactpicker_row_name", "id", ctx.getPackageName());
                    if (pickerNameId != 0) {
                         View pickerName = view.findViewById(pickerNameId);
                         if (pickerName != null) {
                             pickerName.setTranslationX(20f); // Thoda sa gap
                         }
                    }

                    // 4. DATE ADJUSTMENT (Right Side)
                    int dateId = ctx.getResources().getIdentifier("date_time", "id", ctx.getPackageName());
                    if (dateId != 0) {
                        View dateView = view.findViewById(dateId);
                        if (dateView != null) {
                            dateView.setTranslationX(0f); // Apni jagah par rahe
                        }
                    }

                } catch (Throwable t) {
                    // Ignore
                }
                // --- END: FORCE SHIFT FIX ---

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
