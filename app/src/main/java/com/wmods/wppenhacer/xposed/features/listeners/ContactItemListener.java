package com.wmods.wppenhacer.xposed.features;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class WidgetBlocker {

    public static void init(ClassLoader loader) {
        XposedBridge.log("WA Enhancer: Deep Hooking Widget Click System...");

        try {
            // Method 1: RemoteViews ke click setter ko hi block kar dena (Global Block)
            XposedHelpers.findAndHookMethod(
                RemoteViews.class,
                "setOnClickPendingIntent",
                int.class,
                PendingIntent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        RemoteViews rv = (RemoteViews) param.thisObject;
                        if (rv.getPackage() != null && rv.getPackage().equals("com.whatsapp")) {
                            // WhatsApp ke liye click ko null kar do
                            param.args[1] = null; 
                        }
                    }
                }
            );

            // Method 2: List items (Messages) ke click ko block karna
            XposedHelpers.findAndHookMethod(
                RemoteViews.class,
                "setPendingIntentTemplate",
                int.class,
                PendingIntent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        RemoteViews rv = (RemoteViews) param.thisObject;
                        if (rv.getPackage() != null && rv.getPackage().equals("com.whatsapp")) {
                            param.args[1] = null;
                        }
                    }
                }
            );

            // Method 3: WhatsApp ke widget update method ko hi silence kar dena
            XposedHelpers.findAndHookMethod(
                AppWidgetManager.class,
                "updateAppWidget",
                int[].class,
                RemoteViews.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        RemoteViews views = (RemoteViews) param.args[1];
                        if (views != null && "com.whatsapp".equals(views.getPackage())) {
                            // Yahan hum manual safety check kar rahe hain
                            XposedBridge.log("WA Enhancer: WhatsApp Widget Update Intercepted");
                        }
                    }
                }
            );

        } catch (Throwable t) {
            XposedBridge.log("WA Enhancer WidgetBlocker Error: " + t.getMessage());
        }
    }
}
