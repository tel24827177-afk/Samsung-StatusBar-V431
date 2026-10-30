package com.samsung.colorstatusbar.preview;
import android.app.Service;import android.content.Intent;import android.content.IntentFilter;import android.content.BroadcastReceiver;import android.content.Context;import android.content.SharedPreferences;import android.graphics.PixelFormat;import android.os.IBinder;import android.os.PowerManager;import android.os.Handler;import android.os.Looper;import android.os.SystemClock;import android.provider.Settings;import android.view.WindowManager;import android.view.Gravity;
public final class EdgeService extends Service {
 private boolean chargerConnected(){Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));return b!=null && b.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED,0)!=0;}
 private WindowManager wm;private final EdgeStripView[] v=new EdgeStripView[4];private Handler h=new Handler(Looper.getMainLooper());private long until=0;private long chargingUntil=0;private final Runnable expiry=new Runnable(){@Override public void run(){refresh();}};private boolean registered=false;
 private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if("edge.preview.NOTIFY".equals(i.getAction())){until=SystemClock.uptimeMillis()+1000L*getSharedPreferences("edge",0).getInt("seconds",10);h.removeCallbacks(expiry);h.postDelayed(expiry,Math.max(100L,until-SystemClock.uptimeMillis()+80L));}if("edge.preview.CHARGE_TEST".equals(i.getAction())){
 chargingUntil=SystemClock.uptimeMillis()+1000L*getSharedPreferences("edge",0).getInt("seconds",10);
 h.removeCallbacks(expiry);h.postDelayed(expiry,Math.max(100L,Math.max(until,chargingUntil)-SystemClock.uptimeMillis()+80L));
 }if(Intent.ACTION_POWER_DISCONNECTED.equals(i.getAction()))chargingUntil=0L;refresh();}};
 @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);IntentFilter f=new IntentFilter();f.addAction("edge.preview.NOTIFY");f.addAction(Intent.ACTION_POWER_CONNECTED);f.addAction(Intent.ACTION_POWER_DISCONNECTED);f.addAction("edge.preview.CHARGE_TEST");f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);registered=true;}
 @Override public int onStartCommand(Intent i,int f,int id){refresh();return START_STICKY;}
 private void refresh(){
 android.app.KeyguardManager kg=(android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);
 boolean locked=kg!=null&&kg.isKeyguardLocked();
 boolean awake=((PowerManager)getSystemService(POWER_SERVICE)).isInteractive();
 SharedPreferences dp=getSharedPreferences("edge",0);
 String snapshot="螢幕亮起="+awake+"；系統鎖定="+locked+"；懸浮權限="+Settings.canDrawOverlays(this)+"；時間="+new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.TAIWAN).format(new java.util.Date());
 String history=dp.getString("diagnostic_history","");
 if(!snapshot.substring(0,snapshot.indexOf("；時間=")).equals(dp.getString("last_state",""))){
   dp.edit().putString("last_state",snapshot.substring(0,snapshot.indexOf("；時間=")))
     .putString("diagnostic_history",(snapshot+"\n"+history).substring(0,Math.min(1500,(snapshot+"\n"+history).length()))).apply();
 }
 dp.edit().putString("diagnostic",snapshot).apply();
 android.util.Log.i("EdgeV45", "screenInteractive="+awake+" keyguardLocked="+locked+" overlayPermission="+Settings.canDrawOverlays(this));
if(getSharedPreferences("edge",0).getBoolean("access_mode",false)){hide();return;}
SharedPreferences p=getSharedPreferences("edge",0);PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
 boolean active=power!=null&&power.isInteractive()&&p.getBoolean("enabled",false)&&(p.getBoolean("always",false)||(p.getBoolean("notifications",false)&&SystemClock.uptimeMillis()<until)||(p.getBoolean("charging",false)&&(chargerConnected()||SystemClock.uptimeMillis()<chargingUntil)));
 if(!active||!Settings.canDrawOverlays(this)){hide();return;}
 // V4.7: measure the real display and the usable overlay window bounds.
 // Do not fake a system-nav-bar overlay by moving a view outside its permitted area.
 try {
   android.graphics.Point physical=new android.graphics.Point();
   wm.getDefaultDisplay().getRealSize(physical);
   android.graphics.Rect avail=wm.getCurrentWindowMetrics().getBounds();
   String bounds="real="+physical.x+"x"+physical.y+"; window="+avail.width()+"x"+avail.height()+"; navGap="+Math.max(0,physical.y-avail.height());
   getSharedPreferences("edge",0).edit().putString("v47_bounds",bounds).apply();
   android.util.Log.i("EdgeV47",bounds);
 } catch(Exception ignored) {}
 // Keep existing windows to avoid blink on setting updates.

 int thick=Math.max(2,Math.min(14,p.getInt("thick",4)));int px=(int)(thick*getResources().getDisplayMetrics().density+.5f);
 float opacity=Math.min(1.0f,Math.max(.10f,p.getInt("opacity",90)/100f));float speed=p.getInt("speed",15)/100f;
 // V4.9: use ONE shared coordinate frame for the four narrow windows.
 android.graphics.Rect region=wm.getCurrentWindowMetrics().getBounds();
 int width=Math.max(px*2,region.width()),height=Math.max(px*2,region.height());
 // V4.10: only the narrow top strip on cover displays gets a negative inset offset.
 // Keep all other windows identical to V4.9, preserving bottom/Gmail behavior.
 android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
 float widthDp=width/dm.density;
 boolean cover=widthDp<600f;
 int statusInset=0;
 try {android.view.WindowInsets in=wm.getCurrentWindowMetrics().getWindowInsets();
 statusInset=in.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top;
 }catch(Exception ignored){}
 int topShift=cover?Math.min(statusInset,Math.round(38f*dm.density)):0;
 int[] xs={0,0,0,width-px},ys={-topShift,0,height-px,0};
 int[] ws={width,px,width,px},hs={px,height,px,height};
 for(int k=0;k<4;k++){
 WindowManager.LayoutParams lp=new WindowManager.LayoutParams(ws[k],hs[k],WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
 WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
 PixelFormat.TRANSLUCENT);
 if(k==0 && cover)lp.flags|=WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
 lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=xs[k];lp.y=ys[k];lp.alpha=opacity;
 if(v[k]!=null){v[k].setSpeed(speed);v[k].setSelected(p.getInt("mode",-1));try{wm.updateViewLayout(v[k],lp);}catch(Exception e){try{wm.removeView(v[k]);}catch(Exception ignored){}v[k]=null;}if(v[k]!=null)continue;}
 EdgeStripView view=new EdgeStripView(this,k);view.setSpeed(speed);view.setSelected(p.getInt("mode",-1));try{wm.addView(view,lp);v[k]=view;}catch(Exception e){android.util.Log.w("EdgeV49","Strip "+k+" failed",e);} }
 p.edit().putString("v49_bounds","V4.10 display="+width+"x"+height+"; widthDp="+Math.round(widthDp)+"; cover="+cover+"; statusInset="+statusInset+"; topShift="+topShift+"; bottomY="+(height-px)+"; thickness="+px).apply();
 }
 private void hide(){for(int k=0;k<4;k++)if(v[k]!=null){try{wm.removeView(v[k]);}catch(Exception ignored){}v[k]=null;}}
 @Override public void onDestroy(){h.removeCallbacks(expiry);hide();if(registered)unregisterReceiver(receiver);super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
