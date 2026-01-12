package com.wa.enhancer.hooks; // Package name apne project ke hisab se adjust karein

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.widget.RemoteViews;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class WidgetBlocker {

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        // Sirf WhatsApp process me hi hook karein
        if (!lpparam.packageName.equals("com.whatsapp")) {
            return;
        }

        XposedBridge.log("WA Enhancer: Hooking Widget Manager for " + lpparam.packageName);

        try {
            XposedHelpers.findAndHookMethod(
                AppWidgetManager.class,
                "updateAppWidget",
                int[].class, // appWidgetIds
                RemoteViews.class, // views
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        RemoteViews views = (RemoteViews) param.args[1];
                        
                        if (views == null) return;

                        // Context nikalna ID dhundne ke liye
                        // Hum current thread se context uthayenge
                        Context context = (Context) XposedHelpers.callMethod(
                            XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader), 
                            "currentApplication"
                        );

                        if (context != null) {
                            // 1. HEADER (Green Bar) ko Unclickable banana
                            int headerId = context.getResources().getIdentifier("header", "id", "com.whatsapp");
                            if (headerId != 0) {
                                views.setOnClickPendingIntent(headerId, null);
                                // XposedBridge.log("WA Enhancer: Widget Header Blocked");
                            }

                            // 2. LIST (Messages) ko Unclickable banana
                            int listId = context.getResources().getIdentifier("conversation_list", "id", "com.whatsapp");
                            if (listId != 0) {
                                // "setPendingIntentTemplate" list items ke click manage karta hai
                                views.setPendingIntentTemplate(listId, null);
                                // XposedBridge.log("WA Enhancer: Widget List Blocked");
                            }
                            
                            // 3. (Optional) Compose Button (Agar widget me hai to)
                            int composeId = context.getResources().getIdentifier("compose", "id", "com.whatsapp");
                            if (composeId != 0) {
                                views.setOnClickPendingIntent(composeId, null);
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("WA Enhancer Error: " + t.getMessage());
        }
    }
}
