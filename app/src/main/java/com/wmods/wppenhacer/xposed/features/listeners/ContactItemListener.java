package com.wmods.wppenhacer.xposed.features.listeners;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

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

                // --- START: AGGRESSIVE Minimalist Mod ---
                try {
                    android.content.Context ctx = view.getContext();
                    
                    // 1. GHOST CLICK FIX: Saare Image/Button IDs ko dhund ke GONE karo
                    String[] hideIds = {
                        "contact_photo",           // DP
                        "photo_btn",               // Clickable Overlay (Ye click leta hai)
                        "avatar",                  
                        "profile_picture",         
                        "contactpicker_row_photo", 
                        "wcontact_photo",
                        "selection_check"          // Selection circle
                    };
                    
                    for (String idName : hideIds) {
                        int resId = ctx.getResources().getIdentifier(idName, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View targetView = view.findViewById(resId);
                            if (targetView != null) {
                                targetView.setVisibility(View.GONE);
                                targetView.setOnClickListener(null);
                                targetView.setClickable(false);
                            }
                        }
                    }
                    
                    // 2. SPACE FIX: Text ko zabardasti Left Khiskao (TranslationX)
                    // Hum Text Container ko dhund rahe hain
                    String[] contentIds = {
                        "conversations_row_content",  // Main Home Screen Text Container
                        "contact_row_container",      // Contact List Container
                        "contactpicker_row_name"      // Name specific
                    };

                    for (String contentId : contentIds) {
                         int resId = ctx.getResources().getIdentifier(contentId, "id", ctx.getPackageName());
                         if (resId != 0) {
                             View contentView = view.findViewById(resId);
                             if (contentView != null) {
                                 // Padding Zero karo
                                 contentView.setPadding(0, contentView.getPaddingTop(), contentView.getPaddingRight(), contentView.getPaddingBottom());
                                 
                                 // AGGRESSIVE SHIFT: -120 pixels left move karo
                                 // Ye value aap kam/jyada kar sakte hain agar text jyada chipak jaye
                                 contentView.setTranslationX(-130f); 
                                 
                                 // Date/Time ko wapas adjust karo (optional, taaki wo na kat jaye)
                                 int dateId = ctx.getResources().getIdentifier("conversations_row_date_divider", "id", ctx.getPackageName());
                                 if (dateId != 0) {
                                     View dateView = view.findViewById(dateId);
                                     if (dateView != null) {
                                         // Date ko thoda right shift karo taaki wo text ke upar na chade
                                         dateView.setTranslationX(20f);
                                     }
                                 }
                                 break; // Ek mil gaya to kaam khatam
                             }
                         }
                    }

                } catch (Throwable t) {
                    // Ignore errors
                }
                // --- END: AGGRESSIVE Minimalist Mod ---

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
        public abstract void onBind(WaContactWpp waContact, View view);
    }
}
