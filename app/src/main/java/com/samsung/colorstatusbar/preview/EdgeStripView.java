package com.samsung.colorstatusbar.preview;
import android.content.Context;import android.graphics.Canvas;import android.graphics.Color;import android.graphics.Paint;import android.view.View;import android.os.SystemClock;import android.content.SharedPreferences;
public final class EdgeStripView extends View {
 private static final int[] COLORS={0xffe00025,0xffe66b00,0xffffcc00,0xff00b52b,0xff00b9c9,0xff1246ed,0xff8200bd,0xffffffff};
 private static final int[] DEFAULT_MULTI={0xff00e5e5,0xffe100ce,0xfffd3209,0xff00d86c,0xff4a00ec};
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int side;private static final long SHARED_START=SystemClock.uptimeMillis();private float speed=.13f;private int selected=-1;
 public EdgeStripView(Context c,int side){super(c);this.side=side;setWillNotDraw(false);}public void setSpeed(float v){speed=v;invalidate();}public void setSelected(int v){selected=v;invalidate();}
 @Override protected void onDraw(Canvas c){int l=(side>=4?getWidth():(side%2==0?getWidth():getHeight()));int w=(side>=4?getHeight():(side%2==0?getHeight():getWidth()));if(l<=0||w<=0)return;
 int[] palette=COLORS;if(selected==-2){SharedPreferences p=getContext().getSharedPreferences("edge",0);int count=Math.max(2,Math.min(5,p.getInt("multi_count",3)));palette=new int[count];for(int j=0;j<count;j++)palette[j]=p.getInt("multi_color_"+j,DEFAULT_MULTI[j]);}
 int parts=Math.max(80,l/3);float phase=((SystemClock.uptimeMillis()-SHARED_START)*speed/1000f)%1f;
 int direction=getContext().getSharedPreferences("edge",0).getInt("flow_direction",0);
 for(int i=0;i<parts;i++){if(selected>=0&&selected<8){paint.setColor(COLORS[selected]);}else{
 float pos=((float)i/parts);
 // Sides are top=0, left=1, bottom=2, right=3. Preserve window positioning.
 // Clockwise is top L->R, right T->B, bottom R->L, left B->T.
 boolean forward=(side==0||side==3||side>=4);
 float clockwisePosition=forward?pos:1f-pos;
 float travel;
 if(direction==1){travel=(1f-clockwisePosition)-phase;}
 else if(direction==2){travel=(2f*Math.min(pos,1f-pos))-phase;}
 else{travel=clockwisePosition-phase;}
 travel=travel-(float)Math.floor(travel);
 float a=travel*palette.length;int j=((int)a)%palette.length;float t=a-j;int x=palette[j],y=palette[(j+1)%palette.length];paint.setColor(Color.rgb((int)(Color.red(x)*(1-t)+Color.red(y)*t),(int)(Color.green(x)*(1-t)+Color.green(y)*t),(int)(Color.blue(x)*(1-t)+Color.blue(y)*t)));}
 float p=(float)i*l/parts,q=(float)(i+1)*l/parts+1;if(side%2==0||side>=4)c.drawRect(p,0,q,w,paint);else c.drawRect(0,p,w,q,paint);}
 postInvalidateDelayed(40);
 }
}
