package com.samsung.colorstatusbar.preview;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;

/** Opt-in visual-only border experiment. No content reading, clicks or gestures. */
public class EdgeAccessibilityService extends AccessibilityService {
 private WindowManager wm;
 private EdgeStripView[] strips=new EdgeStripView[4];
 private Handler handler=new Handler(Looper.getMainLooper());
 private long notificationUntil=0L;
 private long chargingUntil=0L;
 private boolean chargerConnected(){
  Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  return battery!=null && battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED,0)!=0;
 }
 private final Runnable notificationTimeout=new Runnable(){@Override public void run(){drawOrHide();}};
 private void scheduleExpiry(){
  handler.removeCallbacks(notificationTimeout);
  long remain=Math.max(notificationUntil,chargingUntil)-SystemClock.uptimeMillis();
  if(remain>0){handler.postDelayed(notificationTimeout,remain+80L);}else{drawOrHide();}
 }
 private boolean receiverRegistered=false;
 private final BroadcastReceiver receiver=new BroadcastReceiver(){
  @Override public void onReceive(Context c,Intent i){
   if("edge.preview.NOTIFY".equals(i.getAction())){
     notificationUntil=SystemClock.uptimeMillis()+1000L*getSharedPreferences("edge",0).getInt("seconds",10);
     scheduleExpiry();
   }
   if("edge.preview.CHARGE_TEST".equals(i.getAction())){
     chargingUntil=SystemClock.uptimeMillis()+1000L*getSharedPreferences("edge",0).getInt("seconds",10);
     scheduleExpiry();
   }
   if(Intent.ACTION_POWER_DISCONNECTED.equals(i.getAction()))chargingUntil=0L;
   drawOrHide();
  }
 };
 @Override protected void onServiceConnected(){
  super.onServiceConnected();
  wm=(WindowManager)getSystemService(WINDOW_SERVICE);
  IntentFilter f=new IntentFilter();
  f.addAction("edge.preview.ACCESS_REFRESH");f.addAction("edge.preview.NOTIFY");
  f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_POWER_CONNECTED);f.addAction(Intent.ACTION_POWER_DISCONNECTED);f.addAction("edge.preview.CHARGE_TEST");
  if(!receiverRegistered){registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);receiverRegistered=true;}
  drawOrHide();
 }
 @Override public void onAccessibilityEvent(AccessibilityEvent event){/* Intentionally do not inspect events or content. */}
 @Override public void onInterrupt(){}
 @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);drawOrHide();}
 @Override public void onDestroy(){handler.removeCallbacks(notificationTimeout);hide();if(receiverRegistered){unregisterReceiver(receiver);receiverRegistered=false;}super.onDestroy();}
 private void hide(){if(wm==null)return;for(int k=0;k<4;k++){if(strips[k]!=null){try{wm.removeView(strips[k]);}catch(Exception ignored){}strips[k]=null;}}}
 private void drawOrHide(){
  if(wm==null)return;
  SharedPreferences p=getSharedPreferences("edge",0);
  PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
  boolean showing=power!=null&&power.isInteractive()&&p.getBoolean("access_mode",false)&&p.getBoolean("enabled",false)
   &&(p.getBoolean("always",false)||(p.getBoolean("notifications",false)&&SystemClock.uptimeMillis()<notificationUntil)||(p.getBoolean("charging",false)&&(chargerConnected()||SystemClock.uptimeMillis()<chargingUntil)));
  if(!showing){hide();return;}
  if(!p.getBoolean("always",false) && ((p.getBoolean("notifications",false)&&notificationUntil>SystemClock.uptimeMillis())||(p.getBoolean("charging",false)&&chargingUntil>SystemClock.uptimeMillis())))scheduleExpiry();
  int thick=Math.max(2,Math.min(14,p.getInt("thick",4)));
  int px=Math.max(1,(int)(thick*getResources().getDisplayMetrics().density+.5f));
  float opacity=Math.min(1f,Math.max(.1f,p.getInt("opacity",90)/100f));
  float speed=p.getInt("speed",15)/100f;
  // V4.9: shared window-space rectangle. Keep four narrow, non-touchable windows.
  android.graphics.Rect region=wm.getCurrentWindowMetrics().getBounds();
  int width=Math.max(px*2,region.width()),height=Math.max(px*2,region.height());
  int overlap=Math.max(1,Math.min(px/2,3));
  float widthDp=width/getResources().getDisplayMetrics().density;
  boolean cover=widthDp<600f;
  int statusInset=0;
  try {statusInset=wm.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top;}catch(Exception ignored){}
  // V4.27: detect actual rotation and the visible display-cutout boundary.
  // Never assume the front camera is on a particular landscape side.
  int rotation=wm.getDefaultDisplay().getRotation();
  boolean landscape=(rotation==android.view.Surface.ROTATION_90 || rotation==android.view.Surface.ROTATION_270);
  String cutoutInfo="none";
  try {
    android.view.DisplayCutout dc=wm.getCurrentWindowMetrics().getWindowInsets().getDisplayCutout();
    if(dc!=null) cutoutInfo="safe="+dc.getSafeInsetLeft()+","+dc.getSafeInsetTop()+","+dc.getSafeInsetRight()+","+dc.getSafeInsetBottom()+";rects="+dc.getBoundingRects().toString();
  }catch(Exception ex){cutoutInfo="unavailable:"+ex.getClass().getSimpleName();}
  // V4.18 opt-in: test whether negative Y moves the cover TOP strip into status-bar region.
  // Only strip 0 moves; remaining three windows are left unchanged.
  boolean unifiedCover=cover; // V4.23: verified V4.22 four-edge geometry is always active on cover.
  boolean shiftProbe=unifiedCover;
  boolean seamProbe=false;
  // V4.21 opt-in reversible side corner overlap test for the cover screen.
  boolean cornerOverlap=unifiedCover;
  int cornerPx=cornerOverlap?Math.max(2,Math.min(px/3,6)):0;
  int topShift=0;
  int[] xs={0,0,0,width-px},ys={-topShift,-cornerPx,height-px,-cornerPx};
  int[] ws={width,px,width,px},hs={px,height+cornerPx,px,height+cornerPx};
  for(int k=0;k<4;k++){
   WindowManager.LayoutParams lp=new WindowManager.LayoutParams(ws[k],hs[k],
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT);
   // V4.23: all four strips stay TYPE_ACCESSIBILITY_OVERLAY.
   // V4.17 application-overlay experiment was removed (caused visual discontinuity).
   // V4.20 opt-in seam test: side windows share the top window inset policy. Bottom unchanged.
   // Security/system compositing may still restrict lockscreen and status areas.
   // Clear automatic fit-insets and permit layout through display cutout/system bars.
   // This cannot override OEM secure-window compositing restrictions.
   if(landscape || (k==0 && shiftProbe) || ((k==1 || k==3) && (seamProbe || cornerOverlap))){
    lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    lp.setFitInsetsTypes(0);
    lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
   }
   // Baseline remains exactly as in V4.15 when probe is off.
   lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=xs[k];lp.y=ys[k];lp.alpha=opacity;
   try{
    if(strips[k]==null){strips[k]=new EdgeStripView(this,k);wm.addView(strips[k],lp);}
    else wm.updateViewLayout(strips[k],lp);
    strips[k].setSpeed(speed);strips[k].setSelected(p.getInt("mode",-1));
   }catch(Exception e){android.util.Log.w("EdgeV49","Accessibility strip "+k+" failed",e);if(strips[k]!=null){try{wm.removeView(strips[k]);}catch(Exception ignored){}strips[k]=null;}}
  }
  p.edit().putString("v49_bounds","access display="+width+"x"+height+"; bottomY="+(height-px)+"; thickness="+px).apply();
  // V4.15 diagnostics only: do not alter window placement or touch flags.
  String metric="V4.27 rotation="+rotation+"; landscape="+landscape+"; cutout="+cutoutInfo+"; overlay="+width+"x"+height+"; cover="+cover+"; density="+getResources().getDisplayMetrics().density+"; statusInset="+statusInset+"; requestedTopY="+ys[0]+"; stripPx="+px+"; v423Joined="+unifiedCover+"; topInsetsProbe="+shiftProbe+"; sideSeamProbe="+seamProbe+"; cornerOverlap="+cornerOverlap+"; cornerPx="+cornerPx+"; topShiftPx="+topShift+"; topSeparateRequested=false"+"; overlayPermission="+android.provider.Settings.canDrawOverlays(this)+"; topWindowType="+WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
  try {
   android.graphics.Point real=new android.graphics.Point();wm.getDefaultDisplay().getRealSize(real);
   android.view.WindowInsets wi=wm.getCurrentWindowMetrics().getWindowInsets();
   android.graphics.Insets bars=wi.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars());
   metric+="; real="+real.x+"x"+real.y+"; bars(T,B)="+bars.top+","+bars.bottom;
  }catch(Exception e){metric+="; insetsError="+e.getClass().getSimpleName();}
  final String snapshot=metric;
  p.edit().putString("v419_top_diagnostic",snapshot+"; waiting for actual view coordinates").apply();
  if(strips[0]!=null){strips[0].postDelayed(()->{
   try{
    int[] topLoc=new int[2];strips[0].getLocationOnScreen(topLoc);
    String extra="; topActual(x,y)="+topLoc[0]+","+topLoc[1]+"; topSize="+strips[0].getWidth()+"x"+strips[0].getHeight();
    if(strips[1]!=null){int[] leftLoc=new int[2];strips[1].getLocationOnScreen(leftLoc);extra+="; leftActual(x,y)="+leftLoc[0]+","+leftLoc[1];}
    if(strips[3]!=null){int[] rightLoc=new int[2];strips[3].getLocationOnScreen(rightLoc);extra+="; rightActual(x,y)="+rightLoc[0]+","+rightLoc[1];}
    getSharedPreferences("edge",0).edit().putString("v419_top_diagnostic",snapshot+extra).apply();
   }catch(Exception e){getSharedPreferences("edge",0).edit().putString("v419_top_diagnostic",snapshot+"; viewError="+e.getClass().getSimpleName()).apply();}
  },350);}

  try{
   android.graphics.Point real=new android.graphics.Point();wm.getDefaultDisplay().getRealSize(real);
   android.graphics.Rect bounds=wm.getCurrentWindowMetrics().getBounds();
   p.edit().putString("v48_access_bounds","real="+real.x+"x"+real.y+";overlay="+bounds.width()+"x"+bounds.height()+";mode=accessibility").apply();
  }catch(Exception ignored){}
 }
}
