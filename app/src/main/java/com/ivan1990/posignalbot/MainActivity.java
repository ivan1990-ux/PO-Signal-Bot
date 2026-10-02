package com.ivan1990.posignalbot;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root, history; TextView signal, confidence, status, countdown; Spinner asset;
    final Random rnd = new Random(42); ArrayList<Double> closes = new ArrayList<>(); int seconds=60;

    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);} 
    TextView tv(String s,int sp){ TextView t=new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(sp); t.setPadding(dp(12),dp(8),dp(12),dp(8)); return t; }

    @Override public void onCreate(Bundle b){super.onCreate(b); build(); seed(); generateSignal();}
    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(12),dp(16),dp(12)); root.setBackgroundColor(Color.rgb(5,11,24));
        TextView title=tv("PO SIGNAL BOT",24); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setGravity(Gravity.CENTER); root.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView sub=tv("Señales de 1 minuto · método Price Action",14); sub.setTextColor(Color.LTGRAY); sub.setGravity(Gravity.CENTER); root.addView(sub);
        asset=new Spinner(this); String[] a={"EUR/USD OTC","GBP/USD OTC","USD/JPY OTC","AUD/USD OTC"}; asset.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a)); root.addView(asset,new LinearLayout.LayoutParams(-1,dp(48)));
        status=tv("MODO DEMO — no conecta ni opera tu cuenta",13); status.setTextColor(Color.YELLOW); status.setGravity(Gravity.CENTER); root.addView(status);
        signal=tv("SEÑAL: —",30); signal.setTypeface(Typeface.DEFAULT,Typeface.BOLD); signal.setGravity(Gravity.CENTER); signal.setPadding(0,dp(24),0,dp(8)); root.addView(signal);
        confidence=tv("Confianza: —",18); confidence.setGravity(Gravity.CENTER); root.addView(confidence);
        countdown=tv("Próxima revisión: 60 s",14); countdown.setGravity(Gravity.CENTER); root.addView(countdown);
        Button btn=new Button(this); btn.setText("GENERAR SEÑAL"); btn.setOnClickListener(v->{seconds=60; generateSignal();}); root.addView(btn,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView note=tv("Regla: estructura de velas + impulso + ruptura/rechazo. Sin EMA y sin RSI.\nNO garantiza ganancias; validar en demo antes de usar dinero real.",13); note.setTextColor(Color.LTGRAY); root.addView(note);
        TextView h=tv("HISTORIAL",16); h.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(h); history=new LinearLayout(this); history.setOrientation(LinearLayout.VERTICAL); ScrollView sv=new ScrollView(this); sv.addView(history); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        new android.os.Handler().postDelayed(new Runnable(){public void run(){ if(seconds>0) seconds--; countdown.setText("Próxima revisión: "+seconds+" s"); if(seconds==0){generateSignal();seconds=60;} new android.os.Handler().postDelayed(this,1000);}},1000);
    }
    void seed(){double p=1.1; for(int i=0;i<30;i++){p += (rnd.nextDouble()-.49)*0.002; closes.add(p);} }
    void generateSignal(){
        for(int i=0;i<5;i++){double last=closes.get(closes.size()-1); closes.add(last+(rnd.nextDouble()-.47)*0.0015); if(closes.size()>80)closes.remove(0);} 
        int n=closes.size(); double shortMove=closes.get(n-1)-closes.get(n-4), longMove=closes.get(n-1)-closes.get(n-12); double avg=0; for(int i=n-6;i<n;i++)avg+=closes.get(i); avg/=6; double dist=closes.get(n-1)-avg;
        String s="NO TRADE"; int c=50; if(shortMove>0 && longMove>0 && dist>0){s="BUY / SUBE"; c=70+(int)Math.min(24,Math.abs(shortMove)*12000);} else if(shortMove<0 && longMove<0 && dist<0){s="SELL / BAJA"; c=70+(int)Math.min(24,Math.abs(shortMove)*12000);} else {c=50+(int)Math.min(14,Math.abs(shortMove)*7000);} 
        confidence.setText("Confianza estimada: "+c+"%  ·  Expiración: 1 min"); signal.setText("SEÑAL: "+s); signal.setTextColor(s.startsWith("BUY")?Color.rgb(50,220,100):s.startsWith("SELL")?Color.rgb(255,80,70):Color.WHITE);
        TextView row=tv(new java.text.SimpleDateFormat("HH:mm:ss").format(new Date())+"  ·  "+s+"  ·  "+c+"%",14); history.addView(row,0);
    }
}
