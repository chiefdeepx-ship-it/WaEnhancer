package com.wmods.wppenhacer.xposed.features.listeners;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

                // --- START: POLISHED FIX (Home + New Chat Attempt) ---
                try {
                    android.content.Context ctx = view.getContext();

                    // 1. HOME SCREEN DP FIX (Specific IDs)
                    String[] homeIds = {"contact_selector", "contact_photo_row"};
                    for (String id : homeIds) {
                        int resId = ctx.getResources().getIdentifier(id, "id", ctx.getPackageName());
                        if (resId != 0) {
                            View box = view.findViewById(resId);
                            if (box != null) {
                                box.setVisibility(View.GONE);
                                ViewGroup.LayoutParams lp = box.getLayoutParams();
                                if (lp != null) { lp.width = 0; box.setLayoutParams(lp); }
                            }
                        }
                    }

                    // 2. NEW CHAT / CONTACT PICKER FIX (Aggressive Search)
                    // Agar direct ID nahi mil rahi, to hum 'ImageView' dhundenge jo size mein chota ho (DP jaisa)
                    int pickerId = ctx.getResources().getIdentifier("contactpicker_row_photo", "id", ctx.getPackageName());
                    if (pickerId != 0) {
                        View pickerView = view.findViewById(pickerId);
                        if (pickerView != null) pickerView.setVisibility(View.GONE);
                    }
                    
                    // Fallback: Agar upar wala fail ho jaye, to manually dhundo
                    if (view instanceof ViewGroup) {
                        ViewGroup vg = (ViewGroup) view;
                        for (int i = 0; i < vg.getChildCount(); i++) {
                            View child = vg.getChildAt(i);
                            // Agar ye Image View hai aur chota hai (DP size approx 100-150px)
                            if (child instanceof ImageView && child.getWidth() > 0 && child.getWidth() < 200) {
                                child.setVisibility(View.GONE);
                            }
                        }
                    }

                    // 3. TEXT PADDING FIX (Home Screen Looks)
                    int textContainerId = ctx.getResources().getIdentifier("contact_row_container", "id", ctx.getPackageName());
                    if (textContainerId != 0) {
                        View textContainer = view.findViewById(textContainerId);
                        if (textContainer != null) {
                            textContainer.setTranslationX(0f);
                            // Padding 25 se badhakar 55 kar di -> "Khula Khula" feel aayega
                            textContainer.setPadding(55, textContainer.getPaddingTop(), textContainer.getPaddingRight(), textContainer.getPaddingBottom());
                        }
                    }
                    
                    // Contact Picker Name Fix (New Chat Text)
                    int pickerNameId = ctx.getResources().getIdentifier("contactpicker_row_name", "id", ctx.getPackageName());
                    if (pickerNameId != 0) {
                         View pickerName = view.findViewById(pickerNameId);
                         if (pickerName != null) {
                             // Isko bhi thoda padding de do
                             pickerName.setPadding(20, 0, 0, 0); 
                         }
                    }

                    // 4. DATE ADJUSTMENT
                    int dateId = ctx.getResources().getIdentifier("date_time", "id", ctx.getPackageName());
                    if (dateId != 0) {
                        View dateView = view.findViewById(dateId);
                        if (dateView != null) dateView.setTranslationX(0f);
                    }

                } catch (Throwable t) {}
                // --- END: POLISHED FIX ---

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
