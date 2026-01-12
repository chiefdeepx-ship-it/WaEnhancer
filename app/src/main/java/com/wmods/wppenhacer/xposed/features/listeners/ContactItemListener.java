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

                // --- START: FINAL ID-BASED HOME SCREEN FIX ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. THE KILLER SWITCH: 'contact_selector' ko uda do (Screenshot se mila ID)
                    // Ye DP ka main box hai. Ise hatane se Click aur Space dono khatam ho jayenge.
                    String[] boxIds = {"contact_selector", "contact_photo_row", "contactpicker_row_photo"};
                    for (String id : boxIds) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View box = view.findViewById(resId);
                            if (box != null) {
                                box.setVisibility(View.GONE);
                                // Size 0 kar do taaki koi shak na rahe
                                ViewGroup.LayoutParams params = box.getLayoutParams();
                                if (params != null) { params.width = 0; box.setLayoutParams(params); }
                            }
                        }
                    }

                    // 2. TEXT ADJUSTMENT ('contact_row_container')
                    int textContainerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (textContainerId != 0) {
                        View textContainer = view.findViewById(textContainerId);
                        if (textContainer != null) {
                            // Left Padding hatao taaki text kone me aaye
                            textContainer.setPadding(0, textContainer.getPaddingTop(), textContainer.getPaddingRight(), textContainer.getPaddingBottom());
                            
                            // Thoda zabardasti Left khiskao (-50px)
                            textContainer.setTranslationX(-50f);
                        }
                    }

                    // 3. DATE FIX (Date ko wapas Right side bhejo)
                    int dateId = ctx.getResources().getIdentifier("date_time", "id", ctx.getPackageName());
                    if (dateId != 0) {
                        View dateView = view.findViewById(dateId);
                        if (dateView != null) {
                            // Date ko Right side push karo (+80px) taaki gap na dikhe
                            dateView.setTranslationX(80f);
                        }
                    }

                } catch (Throwable t) {}
                // --- END: FINAL ID-BASED HOME SCREEN FIX ---

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
