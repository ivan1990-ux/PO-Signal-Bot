package com.ivan1990.posignalbot;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class DataClient {
    private static final String BASE="https://otcharts.com";
    private final ExecutorService io=Executors.newCachedThreadPool();
    private volatile boolean streaming=false;
    public static class Symbol{public final String id,name; Symbol(String i,String n){id=i;name=n;} public String toString(){return name;}}
    public static class Candle{public long time;public double open,high,low,close; Candle(long t,double o,double h,double l,double c){time=t;open=o;high=h;low=l;close=c;}}
    public interface SymbolsCallback{void ok(List<Symbol>s);void error(String m);}
    public interface CandlesCallback{void ok(List<Candle>c);void error(String m);}
    public interface TickCallback{void tick(String s,long t,double p);void error(String m);}
    private HttpURLConnection open(String path,String key)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(30000);
        c.setRequestProperty("Authorization","Bearer "+key.trim());
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","PO-Signal-Bot/3.0 Android");return c;
    }
    public void symbols(String key,SymbolsCallback cb){io.execute(()->{try{
        HttpURLConnection c=open("/v1/symbols?venue=otc",key);int code=c.getResponseCode();
        BufferedReader r=new BufferedReader(new InputStreamReader(code>=200&&code<300?c.getInputStream():c.getErrorStream()));
        StringBuilder s=new StringBuilder();String l;while((l=r.readLine())!=null)s.append(l);r.close();
        if(code<200||code>=300)throw new Exception("HTTP "+code+": "+s);
        JSONArray a=new JSONObject(s.toString()).getJSONArray("symbols");List<Symbol> out=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);out.add(new Symbol(x.getString("symbol"),x.optString("name",x.getString("symbol"))));}
        cb.ok(out);
    }catch(Exception e){cb.error(e.getMessage()==null?"Error de datos":e.getMessage());}});}
    public void candles(String key,String symbol,int tf,int limit,CandlesCallback cb){io.execute(()->{try{
        int rt=tf==10?5:tf;String path="/v1/candles?venue=otc&symbol="+URLEncoder.encode(symbol,"UTF-8")+"&tf="+rt+"&limit="+limit;
        HttpURLConnection c=open(path,key);int code=c.getResponseCode();
        BufferedReader r=new BufferedReader(new InputStreamReader(code>=200&&code<300?c.getInputStream():c.getErrorStream()));
        StringBuilder s=new StringBuilder();String l;while((l=r.readLine())!=null)s.append(l);r.close();
        if(code<200||code>=300)throw new Exception("HTTP "+code+": "+s);
        JSONArray a=new JSONObject(s.toString()).getJSONArray("candles");List<Candle> raw=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);raw.add(new Candle(x.getLong("time"),x.getDouble("open"),x.getDouble("high"),x.getDouble("low"),x.getDouble("close")));}
        cb.ok(tf==10?aggregate(raw,10):raw);
    }catch(Exception e){cb.error(e.getMessage()==null?"Error de velas":e.getMessage());}});}
    private List<Candle> aggregate(List<Candle> raw,int sec){List<Candle> out=new ArrayList<>();Candle cur=null;
        for(Candle b:raw){long bucket=(b.time/sec)*sec;if(cur==null||cur.time!=bucket){if(cur!=null)out.add(cur);cur=new Candle(bucket,b.open,b.high,b.low,b.close);}
        else{cur.high=Math.max(cur.high,b.high);cur.low=Math.min(cur.low,b.low);cur.close=b.close;}}if(cur!=null)out.add(cur);return out;}
    public void stream(String key,String symbol,TickCallback cb){stopStream();streaming=true;io.execute(()->{while(streaming){HttpURLConnection c=null;try{
        c=open("/v1/stream?venue=otc&symbol="+URLEncoder.encode(symbol,"UTF-8"),key);c.setReadTimeout(0);int code=c.getResponseCode();
        if(code<200||code>=300)throw new Exception("Stream HTTP "+code);
        BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()));String line;
        while(streaming&&(line=r.readLine())!=null)if(line.startsWith("data:"))try{JSONObject x=new JSONObject(line.substring(5).trim());
            if(x.has("price")&&x.has("time"))cb.tick(x.optString("symbol",symbol),x.getLong("time"),x.getDouble("price"));
        }catch(Exception ignored){}r.close();
    }catch(Exception e){if(streaming){cb.error("Conexión en vivo: "+(e.getMessage()==null?"reintentando":e.getMessage()));try{Thread.sleep(2500);}catch(InterruptedException ignored){}}}
    finally{if(c!=null)c.disconnect();}}});}
    public void stopStream(){streaming=false;}
}
