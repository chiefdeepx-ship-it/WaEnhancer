package com.wmods.wppenhacer.xposed.features;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.widget.RemoteViews;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class WidgetBlocker {

    public static void init(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod(
                AppWidgetManager.class,
                "updateAppWidget",
                int[].class,
                RemoteViews.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        RemoteViews views = (RemoteViews) param.args[1];
                        if (views == null) return;

                        String pkg = views.getPackage();
                        if (pkg == null || !pkg.equals("com.whatsapp")) return;

                        // STRATEGY: Sabhi clicks ko jad se khatam karna
                        try {
                            // Method 1: RemoteViews ke internal clicks list ko clear karna (Hard Reset)
                            // Ye purana tarika hai jo kai versions pe kaam karta hai
                            Field mActionsField = views.getClass().getDeclaredField("mActions");
                            mActionsField.setAccessible(true);
                            ArrayList<?> mActions = (ArrayList<?>) mActionsField.get(views);
                            
                            if (mActions != null) {
                                // Hum saari actions ko scan karke sirf Click wali actions hata denge
                                mActions.removeIf(action -> action.getClass().getSimpleName().contains("SetOnClickPendingIntent"));
                                XposedBridge.log("WA Enhancer: All widget clicks purged via Reflection!");
                            }

                        } catch (Exception e) {
                            // Method 2: Agar Reflection fail ho jaye, to default IDs try karein
                            XposedBridge.log("WA Enhancer: Reflection failed, trying ID override...");
                            
                            Context context = (Context) XposedHelpers.callMethod(
                                XposedHelpers.findClass("android.app.ActivityThread", loader), 
                                "currentApplication"
                            );

                            if (context != null) {
                                // In IDs ko force-disable karna
                                String[] commonIds = {"header", "conversation_list", "widget_row", "back_pane", "list_view"};
                                for (String idName : commonIds) {
                                    int id = context.getResources().getIdentifier(idName, "id", "com.whatsapp");
                                    if (id != 0) views.setOnClickPendingIntent(id, null);
                                }
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

