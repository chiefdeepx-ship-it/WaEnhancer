package com.wmods.wppenhacer.xposed.features.listeners;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView; // Text view specific properties ke liye
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

                // --- START: FINAL COMPONENT FIX ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. DP HIDE (Sab gayab karo)
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

                    // 2. MAIN CONTAINER SHIFT (Sabko Right le chalo)
                    int containerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (containerId != 0) {
                        View container = view.findViewById(containerId);
                        if (container != null) {
                            // Container ko Right shift (+60px)
                            container.setTranslationX(60f); 
                            // Container ki Right Padding 0 rakho, hum andar wale components ko fix karenge
                            container.setPadding(0, container.getPaddingTop(), 0, container.getPaddingBottom());
                        }
                    }

                    // 3. NAME FIX (Overlap rokna hai) [ID: conversations_row_contact_name]
                    int nameId = ctx.getResources().getIdentifier("conversations_row_contact_name", "id", ctx.getPackageName());
                    if (nameId != 0) {
                        View nameView = view.findViewById(nameId);
                        if (nameView != null) {
                            // "Laxman Rekha": Right side se 180px ki padding de di.
                            // Isse Naam Date tak pahunchne se pehle hi ruk jayega (truncate ho jayega).
                            nameView.setPadding(0, 0, 180, 0); 
                        }
                    }

                    // 4. MESSAGE FIX (Lamba text rokna hai) [ID: single_msg_tv]
                    int msgId = ctx.getResources().getIdentifier("single_msg_tv", "id", ctx.getPackageName());
                    if (msgId != 0) {
                        View msgView = view.findViewById(msgId);
                        if (msgView != null) {
                            // Isko bhi Right side se padding do taaki screen ke bahar na jaye
                            // 100px padding kaafi honi chahiye
                            msgView.setPadding(0, 0, 100, 0);
                        }
                    }

                    // 5. DATE FIX (Wapas khicho) [ID: conversations_row_date]
                    String[] dateIds = {"conversations_row_date", "date_time"};
                    for (String dId : dateIds) {
                        int dateResId = ctx.getResources().getIdentifier(dId, "id", ctx.getPackageName());
                        if (dateResId != 0) {
                            View dateView = view.findViewById(dateResId);
                            if (dateView != null) {
                                // Date ko wapas Left khicho (-120px) taaki wo screen me dikhe
                                dateView.setTranslationX(-120f);
                            }
                        }
                    }

                } catch (Throwable t) {}
                // --- END: FINAL COMPONENT FIX ---

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
