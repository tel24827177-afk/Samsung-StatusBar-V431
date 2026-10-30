package com.samsung.colorstatusbar.preview;
import android.service.notification.NotificationListenerService;import android.service.notification.StatusBarNotification;import android.content.Intent;
public final class EdgeNotificationListener extends NotificationListenerService {
 @Override public void onNotificationPosted(StatusBarNotification s){if(s==null||getPackageName().equals(s.getPackageName()))return; Intent i=new Intent("edge.preview.NOTIFY");i.setPackage(getPackageName());sendBroadcast(i);}
}
