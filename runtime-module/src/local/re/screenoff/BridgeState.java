package local.re.screenoff;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** In-memory private content, persisted technical logs only. */
final class BridgeState {
    static volatile Context context;
    static volatile String status="Stopped", title="",artist="",album="",player="",call="",alert="";
    static volatile boolean mediaReady, callsReady, playing, running;
    static volatile long changed, artVersion, callUntil, alertUntil, lastHook;
    static volatile String callToken="",positiveLabel="",negativeLabel="",callPhase="";
    static volatile String hfpStatus="Not enabled";static volatile boolean hfpReady;static volatile long connectedSince,hfpLastUpdate;
    static volatile int hfpCallCount;
    static void init(Context c){context=c.getApplicationContext();}
    static synchronized void log(String event,Throwable error) {
        String s=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date())+" "+event;
        if(error!=null)s+=" "+error.getClass().getSimpleName()+": "+error.getMessage();
        if(error!=null){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));s+="\n"+trace;}
        Log.i("REIPhoneBridge",s);
        if(context==null)return;
        File f=new File(context.getFilesDir(),"iphone-bridge.log");
        try {
            if(f.length()>1024*1024){File prev=new File(context.getFilesDir(),"iphone-bridge.previous.log");if(prev.exists())prev.delete();f.renameTo(prev);}
            try(FileWriter w=new FileWriter(f,true)){w.write(s+"\n");}
        }catch(IOException e){Log.e("REIPhoneBridge","LOG_WRITE_FAILED",e);}
    }
    static synchronized void status(String s){status=s;log(s,null);}
    static synchronized Bundle snapshot(){
        long now=SystemClock.elapsedRealtime();
        if(hfpReady&&now-hfpLastUpdate>5000){hfpReady=false;if(callToken.startsWith("hfp:"))clearCall();log("HFP_HEARTBEAT_TIMEOUT",null);}
        if(callUntil>0&&now>callUntil){clearCall();changed++;log("CALL_ALERT_EXPIRED",null);}
        if(alertUntil>0&&now>alertUntil){alert="";alertUntil=0;changed++;}
        Bundle b=new Bundle();b.putString("status",status);b.putBoolean("running",running);
        b.putBoolean("mediaReady",mediaReady);b.putBoolean("callsReady",callsReady);
        b.putBoolean("playing",playing);b.putString("title",title);b.putString("artist",artist);
        b.putString("album",album);b.putString("player",player);b.putString("call",call);b.putString("alert",alert);
        b.putLong("changed",changed);b.putLong("artVersion",artVersion);
        b.putString("callToken",callToken);b.putString("positiveLabel",positiveLabel);b.putString("negativeLabel",negativeLabel);
        b.putString("callPhase",callPhase);
        b.putString("hfpStatus",hfpStatus);b.putBoolean("hfpReady",hfpReady);b.putLong("connectedSince",connectedSince);
        b.putBoolean("hookAlive",now-lastHook<10000&&lastHook>0);
        b.putBoolean("overlay",context!=null&&context.getSharedPreferences("bridge",0).getBoolean("overlay",true));
        return b;
    }
    static synchronized void disconnected(){
        mediaReady=false;callsReady=false;playing=false;title="";artist="";album="";player="";alert="";if(!hfpReady)clearCall();
        callUntil=0;alertUntil=0;artVersion++;changed++;
    }
    static synchronized void clearCall(){call="";callToken="";positiveLabel="";negativeLabel="";callPhase="";callUntil=0;connectedSince=0;}
}
