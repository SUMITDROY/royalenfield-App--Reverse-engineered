package local.re.screenoff;

import android.content.Context;
import android.os.*;
import org.json.*;
import java.io.*;

/** Root worker transport. Caller identity stays in memory/private pipes, never status logs. */
final class HfpController {
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private volatile java.lang.Process process;private volatile BufferedWriter input;private JSONObject selected;
    private boolean stopping,starting;private String lastUuid="";private int lastCallState=-1;
    private final android.os.PowerManager.WakeLock callWake;
    HfpController(Context c){context=c;callWake=c.getSystemService(android.os.PowerManager.class).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"TripperBridge:HfpCall");callWake.setReferenceCounted(false);}
    private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    synchronized void start(){
        if(process!=null||starting)return;starting=true;stopping=false;BridgeState.hfpStatus="Requesting KernelSU root";
        new Thread(()->{
            try {
                String command="CLASSPATH="+quote(context.getApplicationInfo().sourceDir)+" /system/bin/app_process /system/bin local.re.screenoff.HfpRootWorker --enable-profile";
                String su=null;for(String path:new String[]{"/system/bin/su","/system/xbin/su","/debug_ramdisk/su"})if(new File(path).canExecute()){su=path;break;}
                if(su==null){
                    main.post(()->{starting=false;BridgeState.hfpStatus="Enable Superuser for Tripper iPhone Bridge in KernelSU, then retry";BridgeState.log("HFP_SU_NOT_VISIBLE_ENABLE_KERNELSU_FOR_BRIDGE_APP",null);});return;
                }
                process=new ProcessBuilder(su,"-c",command).start();input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                Thread errors=new Thread(()->{try(BufferedReader r=new BufferedReader(new InputStreamReader(process.getErrorStream()))){String line;while((line=r.readLine())!=null)BridgeState.log("HFP_ROOT_STDERR "+AppleProtocol.clean(line,300),null);}catch(Exception ignored){}},"HfpErrors");errors.setDaemon(true);errors.start();
                try(BufferedReader r=new BufferedReader(new InputStreamReader(process.getInputStream()))){String line;
                    while((line=r.readLine())!=null){if(line.length()>16384){BridgeState.log("HFP_ROOT_RESPONSE_OVERSIZE",null);continue;}
                        try{final JSONObject state=new JSONObject(line);main.post(()->receive(state));}catch(JSONException ignored){BridgeState.log("HFP_ROOT_NON_JSON_OUTPUT",null);}}
                }
                int code=process.waitFor();main.post(()->{if(!stopping){BridgeState.hfpStatus="HFP worker exited ("+code+") — see logs";BridgeState.log("HFP_ROOT_EXIT_"+code,null);}clear();process=null;input=null;starting=false;});
            }catch(Exception e){main.post(()->{BridgeState.log("HFP_ROOT_START_FAILED",e);BridgeState.hfpStatus="Root denied or unavailable — see logs";clear();process=null;starting=false;});}
        },"TripperHfpRoot").start();
    }
    private void clear(){selected=null;BridgeState.hfpReady=false;if(callWake.isHeld())callWake.release();if(BridgeState.callToken.startsWith("hfp:"))BridgeState.clearCall();}
    private void receive(JSONObject b){
        if(stopping)return;
        String event=b.optString("event");
        if(!event.equals("HFP_STATE")){
            BridgeState.log(event+(b.has("error")?" "+AppleProtocol.clean(b.optString("error"),400):""),null);BridgeState.hfpStatus=event;
            if(event.equals("HFP_CLIENT_PROXY_READY")){String peer=context.getSharedPreferences("bridge",0).getString("hfpPeer","");if(!peer.isEmpty())connect(peer);}
            if(event.equals("HFP_CLIENT_PROXY_DISCONNECTED"))clear();
            return;
        }
        BridgeState.hfpLastUpdate=SystemClock.elapsedRealtime();BridgeState.hfpReady=b.optInt("connectedDevices")>0;
        BridgeState.hfpStatus=BridgeState.hfpReady?"iPhone call channel connected":"Call channel ready — select/connect iPhone";
        JSONArray calls=b.optJSONArray("calls");JSONObject chosen=null;
        BridgeState.hfpCallCount=calls==null?0:calls.length();
        if(calls!=null)for(int i=0;i<calls.length();i++){
            JSONObject c=calls.optJSONObject(i);if(c==null||c.optInt("state",7)==7)continue;
            if(chosen==null||priority(c.optInt("state"))<priority(chosen.optInt("state")))chosen=c;
        }
        selected=chosen;
        if(chosen==null){if(callWake.isHeld())callWake.release();if(!lastUuid.isEmpty())BridgeState.log("HFP_CALLS_CLEARED",null);if(BridgeState.callToken.startsWith("hfp:")||BridgeState.hfpReady)BridgeState.clearCall();lastUuid="";lastCallState=-1;return;}
        try{callWake.acquire(90000);}catch(Exception e){BridgeState.log("HFP_CALL_WAKE_LOCK_FAILED",e);}
        String uuid=chosen.optString("uuid");int state=chosen.optInt("state");
        boolean specific=!BridgeState.call.isEmpty()&&!BridgeState.call.equals("Incoming iPhone call")&&!BridgeState.call.equals("iPhone call");
        String label=lastUuid.equals(uuid)&&specific?BridgeState.call:chosen.optString("number","iPhone call");
        if(!lastUuid.equals(uuid)&&!BridgeState.callToken.startsWith("hfp:")&&BridgeState.callPhase.equals("ringing")&&specific)label=BridgeState.call;
        if(!lastUuid.equals(uuid)||lastCallState!=state)BridgeState.log("HFP_CALL_OBSERVED_STATE_"+state,null);
        lastCallState=state;
        lastUuid=uuid;BridgeState.call=label.isEmpty()?"iPhone call":AppleProtocol.clean(label,96);
        BridgeState.callToken="hfp:"+uuid;BridgeState.callPhase=(state==4||state==5)?"ringing":state==0?"active":(state==1||state==6)?"held":"outgoing";
        BridgeState.positiveLabel=(state==4||state==5)?"Answer":"";BridgeState.negativeLabel=(state==4||state==5)?"Decline":"Disconnect";
        BridgeState.connectedSince=chosen.optLong("connectedSince");BridgeState.callUntil=(state==4||state==5)?SystemClock.elapsedRealtime()+5000:0;
    }
    private static int priority(int s){return s==4||s==5?0:s==0?1:s==1||s==6?2:3;}
    private synchronized void send(JSONObject b){
        try{if(input==null){BridgeState.log("HFP_WORKER_NOT_READY",null);return;}input.write(b.toString());input.newLine();input.flush();}
        catch(IOException e){BridgeState.log("HFP_WORKER_WRITE_FAILED",e);}
    }
    void connect(String address){try{send(new JSONObject().put("op","connect").put("device",address));}catch(JSONException ignored){}}
    void refresh(){try{send(new JSONObject().put("op","snapshot"));}catch(JSONException ignored){}}
    boolean action(String token,int action){
        JSONObject call=selected;if(call==null||token==null||!token.equals("hfp:"+call.optString("uuid"))||(action!=0&&action!=1))return false;
        int state=call.optInt("state",7);if(state==7||action==0&&state!=4&&state!=5)return false;
        try{send(new JSONObject().put("op",action==0?"answer":"hangup").put("uuid",call.optString("uuid")).put("device",call.optString("device")));return true;}
        catch(JSONException e){BridgeState.log("HFP_ACTION_BUILD_FAILED",e);return false;}
    }
    void stop(){stopping=true;try{send(new JSONObject().put("op","stop"));if(input!=null)input.close();}catch(Exception ignored){}
        final java.lang.Process old=process;if(old!=null)main.postDelayed(()->old.destroy(),2500);clear();}
}
