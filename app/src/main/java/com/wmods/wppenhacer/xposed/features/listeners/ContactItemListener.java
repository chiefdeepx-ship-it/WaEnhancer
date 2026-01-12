package com.wmods.wppenhacer.xposed.features.listeners;

import android.view.View;

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
        logDebug(Unobfuscator.getMethodDescriptor(onChangeStatus));
        var field1 = Unobfuscator.loadViewHolderField1(classLoader);
        logDebug(Unobfuscator.getFieldDescriptor(field1));
        var absViewHolderClass = Unobfuscator.loadAbsViewHolder(classLoader);

        XposedBridge.hookMethod(onChangeStatus, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var viewHolder = field1.get(param.thisObject);
                var object = param.args[0];
                var waContact = new WaContactWpp(object);
                var viewField = ReflectionUtils.findFieldUsingFilter(absViewHolderClass, field -> field.getType() == View.class);
                var view = (View) viewField.get(viewHolder);
                // --- START: Hide Home Screen DP Mod ---
                try {
                    android.content.Context ctx = view.getContext();
                    // WhatsApp me DP ke liye commonly ye IDs use hote hain
                    String[] dpIds = {"contact_photo", "photo_btn", "avatar", "profile_picture"};
                    
                    for (String idName : dpIds) {
                        int resId = ctx.getResources().getIdentifier(idName, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View dpView = view.findViewById(resId);
                            if (dpView != null) {
                                dpView.setVisibility(View.GONE); // DP Gayab
                            }
                        }
                    }
                } catch (Throwable t) {
                    // Ignore errors
                }
                // --- END: Hide Home Screen DP Mod ---
                var userJid = waContact.getUserJid();
                if (userJid.isNull()) return;

                for (OnContactItemListener listener : contactListeners) {
                    listener.onBind(waContact, view);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Contact Item Listener";
    }

    public abstract static class OnContactItemListener {
        /**
         * Called when a contact item is bound in the RecyclerView
         *
         * @param waContact The user contact
         * @param view    The view associated with the item
         */
        public abstract void onBind(WaContactWpp waContact, View view);
    }
}
