package com.samsung.colorstatusbar.preview;
import android.app.Activity;import android.os.Bundle;import android.content.Intent;import android.content.SharedPreferences;import android.provider.Settings;import android.net.Uri;import android.view.View;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.widget.*;
public class MainActivity extends Activity{
 private SharedPreferences p;private LinearLayout root;
 private void checkbox(String key,String label,boolean value){CheckBox c=new CheckBox(this);c.setText(label);c.setTextSize(17);c.setChecked(p.getBoolean(key,value));root.addView(c);c.setOnCheckedChangeListener((b,checked)->{SharedPreferences.Editor editor=p.edit().putBoolean(key,checked);
 if(checked && "notifications".equals(key)){editor.putBoolean("always",false).putBoolean("enabled",true);}
 if(checked && "always".equals(key)){editor.putBoolean("notifications",false).putBoolean("enabled",true);}
 editor.apply();if(checked && ("notifications".equals(key)||"always".equals(key))){recreate();}else refresh();});}
 private void slider(String key,String label,int min,int max,int def){TextView t=new TextView(this);t.setText(label+"："+p.getInt(key,def));root.addView(t);SeekBar s=new SeekBar(this);s.setMax(max-min);s.setProgress(p.getInt(key,def)-min);root.addView(s);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){t.setText(label+"："+(v+min));}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){p.edit().putInt(key,b.getProgress()+min).apply();refresh();}});}
 private void button(String label,View.OnClickListener f){Button b=new Button(this);b.setText(label);root.addView(b);b.setOnClickListener(f);}
 @Override public void onCreate(Bundle b){super.onCreate(b);p=getSharedPreferences("edge",0);p.edit().putBoolean("access_mode",true).putBoolean("v417_top_separate",false).putBoolean("v422_cover_joined",true).apply();ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,24,24,40);scroll.addView(root);setContentView(scroll);

 TextView integratedInfo = new TextView(this);
 integratedInfo.setText("V4.30 整合測試：七彩邊框沿用 V4.29 原有程式。狀態列 V3 在另一個設定畫面，請分別手動啟用兩項無障礙服務。初次測試先關閉舊 App 的同類無障礙服務，避免重疊；不必卸載舊版。");
 integratedInfo.setTextSize(16); root.addView(integratedInfo);
 Button statusSettings = new Button(this);statusSettings.setText("開啟彩色狀態列 V3 設定");root.addView(statusSettings);
 statusSettings.setOnClickListener(v -> startActivity(new Intent(this, com.samsung.colorstatusbar.StatusBarSettingsActivity.class)));
 TextView title=new TextView(this);title.setText("七彩邊框 V4.29｜整合測試 V4.30");title.setTextSize(22);root.addView(title);
 TextView info=new TextView(this);info.setText("V4.24 保留你實機確認的 V4.22 外螢幕四邊銜接方式，移除 V4.17 衝突實驗。鎖屏、底部邊框、觸控穿透與內螢幕維持原邏輯；請手動啟用無障礙服務。");root.addView(info);
 checkbox("enabled","開啟八色邊框",false);checkbox("always","螢幕亮起持續顯示",true);checkbox("notifications","任何 App 通知來時亮邊（預設關閉）",false);checkbox("charging","充電期間持續顯示七彩邊框（拔除後停止）",false);
 TextView colorTitle=new TextView(this);colorTitle.setText("邊框色彩：八色流動、單色或自訂 2～5 色");colorTitle.setTextSize(18);root.addView(colorTitle);
 final String[] colors={"🌈 八色流動","自訂多色（2～5 色）","紅色","橘色","黃色","綠色","青色","藍色","紫色","白色"};
 Spinner colorPicker=new Spinner(this);ArrayAdapter<String> ca=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,colors);colorPicker.setAdapter(ca);
 int currentMode=p.getInt("mode",-1);colorPicker.setSelection(currentMode==-1?0:currentMode==-2?1:Math.max(2,Math.min(9,currentMode+2)));
 root.addView(colorPicker);colorPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View v,int pos,long id){int selected=pos==0?-1:pos==1?-2:pos-2;if(p.getInt("mode",-1)!=selected){p.edit().putInt("mode",selected).apply();refresh();}}});
 TextView directionLabel=new TextView(this);directionLabel.setText("七彩邊框流動方向");directionLabel.setTextSize(18);root.addView(directionLabel);
 Spinner directionPicker=new Spinner(this);final String[] directions={"順時針","逆時針","兩邊一起往中間"};directionPicker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,directions));directionPicker.setSelection(Math.max(0,Math.min(2,p.getInt("flow_direction",0))));root.addView(directionPicker);
 directionPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View v,int pos,long id){if(p.getInt("flow_direction",0)!=pos){p.edit().putInt("flow_direction",pos).apply();refresh();}}});
 TextView countLabel=new TextView(this);countLabel.setText("自訂多色：選擇顏色數量");countLabel.setTextSize(18);root.addView(countLabel);
 Spinner countSpinner=new Spinner(this);String[] counts={"2 個顏色","3 個顏色","4 個顏色","5 個顏色"};countSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,counts));countSpinner.setSelection(Math.max(0,Math.min(3,p.getInt("multi_count",3)-2)));root.addView(countSpinner);
 LinearLayout swatches=new LinearLayout(this);swatches.setOrientation(LinearLayout.HORIZONTAL);root.addView(swatches);
 countSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> a){}public void onItemSelected(android.widget.AdapterView<?> a,View v,int position,long id){p.edit().putInt("multi_count",position+2).apply();populateSwatches(swatches,position+2);refresh();}});
 populateSwatches(swatches,p.getInt("multi_count",3));
 TextView presetsLabel=new TextView(this);presetsLabel.setText("預設多色組合（點一下可套用，再個別調整）");root.addView(presetsLabel);
 int[][] presets={{0xff00e5e5,0xffe100ce,0xfffd3209,0xff00d86c,0xff4a00ec},{0xff32d8ff,0xff2970ff,0xff8b33fa,0xffff80e8,0xff67ffde},{0xffff402f,0xffffa023,0xffffef3c,0xff63f34e,0xff00cfe7},{0xffffbfcf,0xffff63ab,0xff994ee4,0xff4a91e9,0xff67e5ff}};
 for(int n=0;n<presets.length;n++){final int idx=n;Button preset=new Button(this);preset.setText("多色預設 "+(n+1));root.addView(preset);preset.setOnClickListener(v->{SharedPreferences.Editor editor=p.edit().putInt("mode",-2);for(int j=0;j<5;j++)editor.putInt("multi_color_"+j,presets[idx][j]);editor.apply();colorPicker.setSelection(1);populateSwatches(swatches,p.getInt("multi_count",3));refresh();});}
 button("保存並套用多色色盤",v->{p.edit().putInt("mode",-2).apply();colorPicker.setSelection(1);refresh();Toast.makeText(this,"已儲存並套用多色色盤",Toast.LENGTH_SHORT).show();});
 TextView accessInfo=new TextView(this);accessInfo.setText("主要邊框模式：無障礙覆蓋視窗（需手動授權；不讀取畫面內容）");accessInfo.setTextSize(16);root.addView(accessInfo);
 button("前往無障礙服務設定（請手動同意）",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
 button("重新載入主要邊框",v->{sendBroadcast(new Intent("edge.preview.ACCESS_REFRESH").setPackage(getPackageName()));refresh();});
 slider("seconds","通知亮起秒數",5,60,10);slider("thick","邊框粗細 dp",2,14,4);slider("opacity","邊框透明度 %",10,100,90);slider("speed","流動速度",2,80,15);
 button("授權懸浮視窗",v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
 button("設定通知存取權限",v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
 button("檢查通知監聽授權",v->{
 boolean allowed=false;
 try{android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
 allowed=nm.isNotificationListenerAccessGranted(new android.content.ComponentName(this,EdgeNotificationListener.class));}catch(Exception ignored){}
 new android.app.AlertDialog.Builder(this).setTitle("V4.24 通知監聽權限")
 .setMessage(allowed?"已授權通知存取。收到其他 App 的新通知時，應顯示七彩邊框。":"尚未授權通知存取！請點『設定通知存取權限』，開啟八色邊框測試版，否則無法接收 LINE、微信或其他 App 的通知事件。")
 .setPositiveButton("確定",null).show();
 });
 button("啟動／更新邊框",v->{p.edit().putBoolean("enabled",true).apply();refresh();Toast.makeText(this,"已啟用邊框；若未出現請確認無障礙服務已授權",Toast.LENGTH_LONG).show();});
 button("查看 V4.27 外螢幕旋轉診斷",v->{new android.app.AlertDialog.Builder(this).setTitle("V4.23 四邊座標診斷").setMessage(p.getString("v419_top_diagnostic","尚無數據：請開啟八色邊框，返回桌面，再回來查看。")+"\n請在外螢幕截圖此視窗。先在實驗關閉時截圖，再開啟實驗、返回桌面並重新截圖。需要懸浮視窗授權，若頂邊消失或鎖屏異常，立刻關閉實驗。 ").setPositiveButton("確定",null).show();});
 button("查看 V4.9 底部座標",v->{new android.app.AlertDialog.Builder(this).setTitle("底部邊框座標診斷").setMessage(p.getString("v49_bounds","尚無座標；請先啟動邊框。")+"\n備註：顯示區高度不一定包含三星系統導覽列，這不是實體底端保證。 ").setPositiveButton("確定",null).show();});
 button("查看鎖屏診斷",v->{new android.app.AlertDialog.Builder(this).setTitle("最近鎖屏／亮屏狀態變化").setMessage(p.getString("diagnostic_history",p.getString("diagnostic","尚無紀錄；請先開啟邊框、鎖屏後解鎖，再按此按鈕。"))+"\n注意：這不能判斷系統是否實際顯示了邊框。").setPositiveButton("確定",null).show();});
 button("測試充電亮起",v->{p.edit().putBoolean("enabled",true).putBoolean("charging",true).apply();Intent i=new Intent("edge.preview.CHARGE_TEST");i.setPackage(getPackageName());sendBroadcast(i);refresh();});
 button("測試通知亮起",v->{p.edit().putBoolean("enabled",true).putBoolean("always",false).putBoolean("notifications",true).apply();Intent i=new Intent("edge.preview.NOTIFY");i.setPackage(getPackageName());sendBroadcast(i);refresh();});
 button("停止邊框",v->{p.edit().putBoolean("enabled",false).apply();refresh();});}
 private void populateSwatches(LinearLayout bar,int count){bar.removeAllViews();for(int i=0;i<count;i++){final int slot=i;int color=p.getInt("multi_color_"+i,DEFAULT_MULTI[i]);Button b=new Button(this);b.setText("色"+(i+1));b.setTextColor((Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000>145?Color.BLACK:Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));bar.addView(b,new LinearLayout.LayoutParams(0,72,1));b.setOnClickListener(v->showRgbPicker(slot,bar));}}
 private static final int[] DEFAULT_MULTI={0xff00e5e5,0xffe100ce,0xfffd3209,0xff00d86c,0xff4a00ec};
 private void showRgbPicker(int slot,LinearLayout swatches){int current=p.getInt("multi_color_"+slot,DEFAULT_MULTI[slot]);LinearLayout dialog=new LinearLayout(this);dialog.setPadding(28,18,28,12);dialog.setOrientation(LinearLayout.VERTICAL);TextView preview=new TextView(this);preview.setText("顏色 "+(slot+1)+" 預覽");preview.setGravity(android.view.Gravity.CENTER);preview.setTextSize(20);dialog.addView(preview,new LinearLayout.LayoutParams(-1,95));int[] channels={Color.red(current),Color.green(current),Color.blue(current)};String[] names={"紅 R","綠 G","藍 B"};for(int j=0;j<3;j++){final int ch=j;TextView text=new TextView(this);text.setText(names[j]+"："+channels[j]);dialog.addView(text);SeekBar slider=new SeekBar(this);slider.setMax(255);slider.setProgress(channels[j]);dialog.addView(slider);slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){channels[ch]=value;text.setText(names[ch]+"："+value);int c=Color.rgb(channels[0],channels[1],channels[2]);preview.setBackgroundColor(c);preview.setTextColor((channels[0]*299+channels[1]*587+channels[2]*114)/1000>145?Color.BLACK:Color.WHITE);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});}preview.setBackgroundColor(current);new android.app.AlertDialog.Builder(this).setTitle("自訂第 "+(slot+1)+" 色").setView(dialog).setNegativeButton("取消",null).setPositiveButton("保存顏色",(d,w)->{p.edit().putInt("multi_color_"+slot,Color.rgb(channels[0],channels[1],channels[2])).putInt("mode",-2).apply();populateSwatches(swatches,p.getInt("multi_count",3));refresh();}).show();}
 private void refresh(){sendBroadcast(new Intent("edge.preview.ACCESS_REFRESH").setPackage(getPackageName()));try{startService(new Intent(this,EdgeService.class));}catch(Exception e){Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();}}
}
