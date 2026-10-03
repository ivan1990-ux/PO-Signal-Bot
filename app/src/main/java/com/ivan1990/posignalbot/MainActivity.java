package com.ivan1990.posignalbot;

import android.app.*;import android.os.*;import android.graphics.*;import android.view.*;import android.widget.*;import java.text.*;import java.util.*;

public class MainActivity extends Activity{
 LinearLayout root,history; TextView signal,confidence,status,selectedInfo; Spinner asset,timeframe; EditText key; Button connect,analyze,stop;
 DataClient api=new DataClient(); List<DataClient.Symbol> symbols=new ArrayList<>(); List<DataClient.Candle> candles=new ArrayList<>();
 DataClient.Candle forming; String sym=""; int tf=60,minScore=80; long lastBucket=-1; boolean analyzing=false;

 int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
 TextView tv(String s,int z){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(z);t.setPadding(dp(8),dp(5),dp(8),dp(5));return t;}

 public void onCreate(Bundle b){super.onCreate(b);build();}

 void build(){
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(8),dp(10),dp(8));root.setBackgroundColor(Color.rgb(6,12,24));
  TextView h=tv("POCKET SIGNAL PRO",23);h.setGravity(17);h.setTypeface(null,Typeface.BOLD);root.addView(h,new LinearLayout.LayoutParams(-1,dp(42)));
  TextView sub=tv("OTC · Price Action · análisis manual · sin EMA/RSI",13);sub.setGravity(17);root.addView(sub);

  key=new EditText(this);key.setHint("OTCharts API key");key.setSingleLine(true);key.setTextColor(Color.WHITE);key.setHintTextColor(Color.GRAY);
  root.addView(key,new LinearLayout.LayoutParams(-1,dp(48)));

  connect=new Button(this);connect.setText("1. CONECTAR DATOS REALES");connect.setOnClickListener(v->connect());
  root.addView(connect,new LinearLayout.LayoutParams(-1,dp(48)));

  TextView aLabel=tv("2. ELEGÍ EL ACTIVO OTC QUE VAS A OPERAR",13);aLabel.setTypeface(null,Typeface.BOLD);root.addView(aLabel);
  asset=new Spinner(this);root.addView(asset,new LinearLayout.LayoutParams(-1,dp(45)));

  TextView tLabel=tv("3. ELEGÍ LA DURACIÓN / TEMPORALIDAD DE LA SEÑAL",13);tLabel.setTypeface(null,Typeface.BOLD);root.addView(tLabel);
  timeframe=new Spinner(this);
  String[] ts={"15 segundos","1 minuto","3 minutos","5 minutos"};
  timeframe.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,ts));
  timeframe.setSelection(1);
  timeframe.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(AdapterView<?>p){}
   public void onItemSelected(AdapterView<?>p,View v,int x,long id){tf=new int[]{15,60,180,300}[x];updateSelection();}
  });
  root.addView(timeframe,new LinearLayout.LayoutParams(-1,dp(45)));

  selectedInfo=tv("Activo: —   |   Duración: 1 minuto   |   OTC",12);selectedInfo.setGravity(17);root.addView(selectedInfo);

  analyze=new Button(this);analyze.setText("4. ANALIZAR ACTIVO SELECCIONADO");analyze.setEnabled(false);analyze.setOnClickListener(v->analyzeSelected());
  root.addView(analyze,new LinearLayout.LayoutParams(-1,dp(52)));

  stop=new Button(this);stop.setText("DETENER ANÁLISIS");stop.setEnabled(false);stop.setOnClickListener(v->{analyzing=false;api.stopStream();status.setText("Estado: análisis detenido");stop.setEnabled(false);});
  root.addView(stop,new LinearLayout.LayoutParams(-1,dp(45)));

  status=tv("Estado: conectá los datos y elegí un activo",12);status.setGravity(17);root.addView(status);
  signal=tv("SEÑAL: —",25);signal.setGravity(17);signal.setTypeface(null,Typeface.BOLD);root.addView(signal,new LinearLayout.LayoutParams(-1,dp(58)));
  confidence=tv("Confianza del modelo: — / 100 · mínimo 80",16);confidence.setGravity(17);root.addView(confidence);

  TextView n=tv("El 80–100 es un PUNTAJE DE CONFIANZA DEL MODELO, no una probabilidad garantizada. La duración indicada es el horizonte seleccionado. Solo se publica señal desde 80 y la operación es manual.",11);n.setTextColor(Color.LTGRAY);root.addView(n);
  TextView ht=tv("HISTORIAL DE SEÑALES",14);ht.setTypeface(null,Typeface.BOLD);root.addView(ht);
  history=new LinearLayout(this);history.setOrientation(LinearLayout.VERTICAL);ScrollView sv=new ScrollView(this);sv.addView(history);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(root);
 }

 void updateSelection(){
  String shown=asset.getSelectedItem()==null?"—":asset.getSelectedItem().toString();
  selectedInfo.setText("Activo: "+shown+"   |   Duración: "+tfLabel(tf)+"   |   OTC");
 }

 String tfLabel(int x){return x<60?x+" segundos":x/60+" minuto"+(x==60?"":"s");}

 void connect(){
  String k=key.getText().toString().trim();if(k.length()<10){status.setText("Estado: clave inválida");return;}
  connect.setEnabled(false);status.setText("Estado: leyendo catálogo OTC…");
  api.symbols(k,new DataClient.SymbolsCallback(){
   public void ok(List<DataClient.Symbol>s){runOnUiThread(()->{symbols=s;asset.setAdapter(new ArrayAdapter<DataClient.Symbol>(MainActivity.this,android.R.layout.simple_spinner_dropdown_item,symbols));status.setText("Conectado · "+s.size()+" instrumentos OTC disponibles");connect.setEnabled(true);analyze.setEnabled(!symbols.isEmpty());updateSelection();});}
   public void error(String m){runOnUiThread(()->{status.setText("Estado: "+m);connect.setEnabled(true);});}
  });
 }

 void analyzeSelected(){
  if(symbols.isEmpty()||asset.getSelectedItemPosition()<0)return;
  sym=symbols.get(asset.getSelectedItemPosition()).id;
  analyzing=true;stop.setEnabled(true);analyze.setEnabled(false);api.stopStream();candles.clear();forming=null;lastBucket=-1;
  String k=key.getText().toString().trim();
  status.setText("Estado: cargando velas reales de "+sym+"…");
  signal.setText("ANALIZANDO "+sym);signal.setTextColor(Color.WHITE);
  confidence.setText("Confianza del modelo: calculando…");
  api.candles(k,sym,tf,120,new DataClient.CandlesCallback(){
   public void ok(List<DataClient.Candle>c){runOnUiThread(()->{
    candles.addAll(c);status.setText("Estado: datos cargados · monitoreando "+sym+" · horizonte "+tfLabel(tf));
    evaluate();
    api.stream(k,sym,new DataClient.TickCallback(){
     public void tick(String s,long t,double p){MainActivity.this.tick(t,p);}
     public void error(String m){runOnUiThread(()->status.setText("Estado: "+m));}
    });
   });}
   public void error(String m){runOnUiThread(()->{status.setText("Estado: "+m);analyze.setEnabled(true);stop.setEnabled(false);});}
  });
 }

 void tick(long t,double p){
  if(!analyzing)return; long b=(t/tf)*tf;
  synchronized(this){
   if(forming==null||forming.time!=b){
    if(forming!=null){candles.add(forming);while(candles.size()>150)candles.remove(0);if(forming.time!=lastBucket){lastBucket=forming.time;evaluate();}}
    forming=new DataClient.Candle(b,p,p,p,p);
   }else{forming.high=Math.max(forming.high,p);forming.low=Math.min(forming.low,p);forming.close=p;}
  }
 }

 void evaluate(){
  List<DataClient.Candle>c; synchronized(this){c=new ArrayList<>(candles);}
  if(c.size()<30)return;
  Score s=score(c);String horizon=tfLabel(tf);
  runOnUiThread(()->{
   confidence.setText("Confianza del modelo: "+s.v+" / 100 · mínimo "+minScore);
   if(s.v>=minScore){
    String d=s.up?"🟢 ALZA":"🔴 BAJA";
    signal.setText("SEÑAL VÁLIDA: "+d+" · "+horizon);
    signal.setTextColor(s.up?Color.rgb(50,230,100):Color.rgb(255,80,70));
    history.addView(tv(new SimpleDateFormat("HH:mm:ss").format(new Date())+" · "+sym+" · "+d+" · "+horizon+" · "+s.v+"/100",12),0);
   }else{
    signal.setText("NO OPERAR · "+sym+" · "+horizon);signal.setTextColor(Color.WHITE);
   }
  });
 }

 static class Score{int v;boolean up;Score(int x,boolean u){v=x;up=u;}}

 Score score(List<DataClient.Candle>c){
  int n=c.size(),u=0,d=0;
  for(int i=n-6;i<n;i++){if(c.get(i).close>c.get(i).open)u++;else if(c.get(i).close<c.get(i).open)d++;}
  DataClient.Candle x=c.get(n-1);double r=Math.max(x.high-x.low,1e-12),body=Math.abs(x.close-x.open)/r;
  double uw=(x.high-Math.max(x.open,x.close))/r,lw=(Math.min(x.open,x.close)-x.low)/r;
  double ph=-1e300,pl=1e300;for(int i=n-11;i<n-1;i++){ph=Math.max(ph,c.get(i).high);pl=Math.min(pl,c.get(i).low);}
  boolean bu=x.close>ph,bd=x.close<pl;double m3=x.close-c.get(n-4).close,m8=x.close-c.get(n-9).close;
  int a=0,b=0;if(u>=4)a+=18;if(d>=4)b+=18;
  if(body>.55){if(x.close>x.open)a+=18;else if(x.close<x.open)b+=18;}
  if(bu)a+=20;if(bd)b+=20;if(lw>.45&&x.close>x.open)a+=12;if(uw>.45&&x.close<x.open)b+=12;
  if(m3>0)a+=10;else if(m3<0)b+=10;if(m8>0)a+=10;else if(m8<0)b+=10;
  int best=Math.max(a,b),v=Math.min(100,50+best/2);if(best<60||Math.abs(a-b)<12)v=Math.min(v,79);
  return new Score(v,a>b);
 }

 protected void onDestroy(){api.stopStream();super.onDestroy();}
}