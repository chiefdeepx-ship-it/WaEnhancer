package com.wmods.wppenhacer.xposed.features.listeners;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView; // Zaruri Import
import android.util.DisplayMetrics; // Screen Size naapne ke liye
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

                // --- START: FINAL OVERLAP KILLER FIX ---
                try {
                    android.content.Context ctx = view.getContext();
                    
                    // 1. SCREEN SIZE CALCULATION (Ganit)
                    // Hamein pata karna hai ki screen kitni choudi hai taaki text ko limit kar sakein
                    DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
                    int screenWidth = metrics.widthPixels;
                    // Safe Width = Screen - (Shift + Date Space + Padding)
                    // Approx 350px minus kar rahe hain taaki Text date se na takraye
                    int maxAllowedWidth = screenWidth - 350; 

                    // 2. DP HIDE (Purana logic)
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

                    // 3. CONTAINER SHIFT (Sabko Right le chalo +60)
                    int containerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (containerId != 0) {
                        View container = view.findViewById(containerId);
                        if (container != null) {
                            container.setTranslationX(60f); 
                            container.setPadding(0, container.getPaddingTop(), 0, container.getPaddingBottom());
                        }
                    }

                    // 4. NAME LIMIT FIX (The Overlap Solution) [ID: conversations_row_contact_name]
                    int nameId = ctx.getResources().getIdentifier("conversations_row_contact_name", "id", ctx.getPackageName());
                    if (nameId != 0) {
                        View nameView = view.findViewById(nameId);
                        // Check karo ki ye TextView hi hai na
                        if (nameView instanceof TextView) {
                            // YAHAN HAI MAGIC: setMaxWidth()
                            // Ye text ko bolta hai: "Is pixel se aage mat badhna"
                            ((TextView) nameView).setMaxWidth(maxAllowedWidth);
                        }
                    }

                    // 5. MESSAGE LIMIT FIX (The Overflow Solution) [ID: single_msg_tv]
                    int msgId = ctx.getResources().getIdentifier("single_msg_tv", "id", ctx.getPackageName());
                    if (msgId != 0) {
                        View msgView = view.findViewById(msgId);
                        if (msgView instanceof TextView) {
                            // Message ko bhi same limit do taaki wo screen se bahar na bhage
                            ((TextView) msgView).setMaxWidth(maxAllowedWidth);
                        }
                    }

                    // 6. DATE FIX (Wapas khicho) [ID: conversations_row_date]
                    // ID update kar di hai jo screenshot me thi
                    int dateId = ctx.getResources().getIdentifier("conversations_row_date", "id", ctx.getPackageName());
                    if (dateId != 0) {
                        View dateView = view.findViewById(dateId);
                        if (dateView != null) {
                            // Container +60 gaya hai, Date ko wapas -120 lao
                            dateView.setTranslationX(-120f);
                        }
                    }

                } catch (Throwable t) {}
                // --- END: FINAL OVERLAP KILLER FIX ---

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
