package com.wmods.wppenhacer.xposed.features;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.widget.RemoteViews;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class WidgetBlocker {

    public static void init(ClassLoader loader) {
        
        XposedBridge.log("WA Enhancer: Hooking Widget Manager...");

        try {
            // AppWidgetManager ka class load karte hain
            // Note: AppWidgetManager Android framework ka hissa hai, isliye seedha class use kar sakte hain
            // Lekin agar error aaye to XposedHelpers.findClass use karein
            
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

                        // Context nikalna resource ID dhundne ke liye
                        // Hum current thread se application context uthayenge
                        Context context = (Context) XposedHelpers.callMethod(
                            XposedHelpers.findClass("android.app.ActivityThread", loader), 
                            "currentApplication"
                        );

                        if (context != null) {
                            // 1. HEADER (Green Bar) ko Unclickable banana
                            int headerId = context.getResources().getIdentifier("header", "id", "com.whatsapp");
                            if (headerId != 0) {
                                views.setOnClickPendingIntent(headerId, null);
                            }

                            // 2. LIST (Messages) ko Unclickable banana
                            int listId = context.getResources().getIdentifier("conversation_list", "id", "com.whatsapp");
                            if (listId != 0) {
                                // "setPendingIntentTemplate" list items ke click manage karta hai
                                views.setPendingIntentTemplate(listId, null);
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
            XposedBridge.log("WA Enhancer WidgetBlocker Error: " + t.getMessage());
        }
    }
}
