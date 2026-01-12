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
                // --- START: ULTRA Minimalist (Home + Contact List) ---
                try {
                    android.content.Context ctx = view.getContext();
                    
                    // Yahan humne IDs ki list badha di hai taaki New Chat list bhi cover ho jaye
                    String[] dpIds = {
                        "contact_photo",           // Standard
                        "photo_btn",               // Standard
                        "avatar",                  // Standard
                        "profile_picture",         // Standard
                        "contactpicker_row_photo", // New Chat List (Contact Picker) ke liye specific
                        "wcontact_photo"           // Business accounts ke liye
                    };
                    
                    for (String idName : dpIds) {
                        int resId = ctx.getResources().getIdentifier(idName, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View dpView = view.findViewById(resId);
                            if (dpView != null) {
                                // 1. Image View ko GONE karein
                                dpView.setVisibility(View.GONE);
                                
                                // 2. Image ke "Papa" (Container Box) ko dhundein aur chupayein (Space hatane ke liye)
                                View parentBox = (View) dpView.getParent();
                                
                                // Check: Parent exist karna chahiye aur wo Main Row nahi hona chahiye
                                if (parentBox != null && parentBox != view) {
                                    parentBox.setVisibility(View.GONE); 
                                    parentBox.setOnClickListener(null); 
                                    parentBox.setClickable(false);
                                }
                            }
                        }
                    }
                    
                    // 3. Text Padding Fix (Space adjust karne ke liye)
                    // Ye koshish karega ki text left side shift ho jaye
                    String[] textContainerIds = {"conversations_row_contact_name", "contact_row_container", "contactpicker_row_name"};
                    for (String txtId : textContainerIds) {
                        int txtResId = ctx.getResources().getIdentifier(txtId, "id", ctx.getPackageName());
                        if (txtResId != 0) {
                             View textContainer = view.findViewById(txtResId);
                             if (textContainer != null) {
                                 textContainer.setPadding(0, textContainer.getPaddingTop(), textContainer.getPaddingRight(), textContainer.getPaddingBottom());
                                 break; // Ek mil gaya to loop roko
                             }
                        }
                    }

                } catch (Throwable t) {
                }
                // --- END: ULTRA Minimalist ---
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
