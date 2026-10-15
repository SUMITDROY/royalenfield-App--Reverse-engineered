package local.re.screenoff;

import android.bluetooth.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Root-only HFP client worker. Private stdio IPC; no exported call-control service. */
public final class HfpRootWorker {
    private static final int CLIENT=16;
    private static Handler main;private static BluetoothAdapter adapter;private static BluetoothProfile proxy;
    private static Context context;private static String peer="";private static boolean probe, enabledProfile;
    private static String lastState="";private static final Set<String> audioPolicySet=new HashSet<>();
    private static String originalProperty="";
    private static boolean polling;
    private static PowerManager.WakeLock callWake;private static long wakeRenewed;
    private static final Map<String,Integer> previousStates=new HashMap<>();private static final Map<String,Long> connectedSince=new HashMap<>();
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args)throws Exception {
        Method method=target.getClass().getMethod(name,types);method.setAccessible(true);return method.invoke(target,args);
    }
    private static synchronized void emit(JSONObject message){System.out.println(message.toString());System.out.flush();}
    private static void event(String event){try{emit(new JSONObject().put("event",event));}catch(Exception ignored){}}
    private static void error(String event,Throwable e){
        Throwable cause=e instanceof InvocationTargetException&&e.getCause()!=null?e.getCause():e;
        try{emit(new JSONObject().put("event",event).put("error",cause.getClass().getSimpleName()+": "+cause.getMessage()));}catch(Exception ignored){}
    }
    private static String property()throws Exception {
        return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"bluetooth.profile.hfp.hf.enabled");
    }
    private static void setProperty(String value)throws Exception{Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,"bluetooth.profile.hfp.hf.enabled",value);}
    private static void fail(int code){
        if(enabledProfile){
            try{setProperty(originalProperty);event("HFP_PROFILE_FAILURE_RESTORED_PREVIOUS_SETTING");adapter.disable();main.postDelayed(()->{adapter.enable();System.exit(code);},2500);return;}
            catch(Exception e){error("HFP_PROFILE_RESTORE_FAILED",e);}
        }System.exit(code);
    }
    private static boolean rootCommand(String... args)throws Exception {java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();while(p.getInputStream().read()!=-1){}return p.waitFor()==0;}
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0){event("HFP_ROOT_REQUIRED");return;}
        probe=Arrays.asList(args).contains("--probe");
        if(Looper.getMainLooper()==null)Looper.prepareMainLooper();main=new Handler(Looper.getMainLooper());
        try {
            Class<?> vm=Class.forName("dalvik.system.VMRuntime");Object runtime=vm.getDeclaredMethod("getRuntime").invoke(null);
            vm.getDeclaredMethod("setHiddenApiExemptions",String[].class).invoke(runtime,(Object)new String[]{"L"});
            // app_process has not run the normal app Bluetooth framework initializer.
            Class<?> initializer=Class.forName("android.bluetooth.BluetoothFrameworkInitializer");
            if(initializer.getMethod("getBluetoothServiceManager").invoke(null)==null){
                Class<?> manager=Class.forName("android.os.BluetoothServiceManager");
                initializer.getMethod("setBluetoothServiceManager",manager).invoke(null,manager.getConstructor().newInstance());
            }
            Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getDeclaredMethod("systemMain").invoke(null);
            context=(Context)at.getDeclaredMethod("getSystemContext").invoke(thread);
            adapter=context.getSystemService(BluetoothManager.class).getAdapter();
            if(adapter==null){event("HFP_ADAPTER_UNAVAILABLE");return;}
            if(!probe){
                callWake=context.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"TripperBridge:HfpCall");callWake.setReferenceCounted(false);
                try{
                    IntentFilter wakeFilter=new IntentFilter("android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED");
                    wakeFilter.addAction("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED");
                    context.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){main.post(()->snapshot());}},wakeFilter,"android.permission.BLUETOOTH_CONNECT",main,Context.RECEIVER_EXPORTED);
                }catch(Exception e){error("HFP_EVENT_RECEIVER_UNAVAILABLE_USING_POLLING",e);}
            }
            String configured=property();originalProperty=configured;event("HFP_PROFILE_PROPERTY_"+(configured.isEmpty()?"UNSET":configured.toUpperCase(Locale.ROOT)));
            if(Arrays.asList(args).contains("--disable-profile")){
                setProperty("");
                adapter.disable();main.postDelayed(()->{adapter.enable();event("HFP_PROFILE_DISABLED_BLUETOOTH_RESTARTED");System.exit(0);},2500);
            }else if(Arrays.asList(args).contains("--enable-profile")&&!"true".equals(configured)){
                if(Build.VERSION.SDK_INT!=35){event("HFP_PROFILE_NEEDS_DEVICE_SPECIFIC_SETUP");return;}
                setProperty("true");
                enabledProfile=true;event("HFP_PROFILE_ENABLED_RESTARTING_BLUETOOTH");adapter.disable();
                main.postDelayed(()->adapter.enable(),2500);main.postDelayed(()->waitForAdapter(0),4000);
            }else waitForAdapter(0);
        }catch(Throwable e){error("HFP_WORKER_INIT_FAILED",e);return;}
        if(!probe){
            Thread input=new Thread(()->{
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(System.in))){String line;
                    while((line=reader.readLine())!=null){if(line.length()>2048){event("HFP_COMMAND_TOO_LARGE");continue;}
                        final JSONObject command=new JSONObject(line);main.post(()->command(command));}
                }catch(Exception e){error("HFP_INPUT_FAILED",e);}finally{main.post(()->shutdown());}
            },"HfpCommands");input.setDaemon(true);input.start();
        }
        Looper.loop();
    }
    private static void waitForAdapter(int attempt){
        try {
            if(!adapter.isEnabled()){if(attempt<20)main.postDelayed(()->waitForAdapter(attempt+1),1000);else{event("HFP_BLUETOOTH_ENABLE_TIMEOUT");fail(2);}return;}
            boolean accepted=adapter.getProfileProxy(context,new BluetoothProfile.ServiceListener(){
                @Override public void onServiceConnected(int profile,BluetoothProfile service){
                    proxy=service;event("HFP_CLIENT_PROXY_READY");
                    if(probe){try{event("HFP_CONNECTED_DEVICE_READ_COUNT_"+proxy.getConnectedDevices().size());}catch(Exception e){error("HFP_READ_ACCESS_FAILED",e);}System.exit(0);}else if(!polling){polling=true;main.post(poll);}
                }
                @Override public void onServiceDisconnected(int profile){proxy=null;audioPolicySet.clear();previousStates.clear();connectedSince.clear();event("HFP_CLIENT_PROXY_DISCONNECTED");}
            },CLIENT);
            if(!accepted){event("HFP_CLIENT_PROXY_REJECTED");fail(3);}
            main.postDelayed(()->{if(proxy==null){event("HFP_CLIENT_UNAVAILABLE_PROFILE_DISABLED_OR_UNSUPPORTED");fail(4);}},12000);
        }catch(Throwable e){error("HFP_CLIENT_BIND_FAILED",e);fail(5);}
    }
    private static final Runnable poll=new Runnable(){public void run(){snapshot();main.postDelayed(this,1000);}};
    @SuppressWarnings("unchecked") private static void snapshot(){
        if(proxy==null)return;
        try {
            List<BluetoothDevice> devices=proxy.getConnectedDevices();JSONArray calls=new JSONArray();
            Set<String> seenCalls=new HashSet<>(),seenDevices=new HashSet<>();
            int matching=0;
            for(BluetoothDevice d:devices){
                if(peer.isEmpty()||!peer.equals(d.getAddress()))continue;
                matching++;
                seenDevices.add(d.getAddress());
                if(audioPolicySet.add(d.getAddress())){
                    invoke(proxy,"setAudioRouteAllowed",new Class[]{BluetoothDevice.class,boolean.class},d,false);
                    event("HFP_AUDIO_ROUTING_DISABLED_KEEP_IPHONE_HEADSET");
                }
                List<?> current=(List<?>)invoke(proxy,"getCurrentCalls",new Class[]{BluetoothDevice.class},d);
                if(current==null)continue;
                for(Object call:current){
                    String uuid=String.valueOf(invoke(call,"getUUID",new Class[]{}));int state=(Integer)invoke(call,"getState",new Class[]{});
                    if(state==7)continue;
                    seenCalls.add(uuid);
                    Integer previous=previousStates.put(uuid,state);
                    if(state==0&&previous!=null&&previous!=0)connectedSince.putIfAbsent(uuid,SystemClock.elapsedRealtime());
                    String number=String.valueOf(invoke(call,"getNumber",new Class[]{}));
                    calls.put(new JSONObject().put("uuid",uuid).put("state",state).put("number",number.equals("null")?"":number)
                        .put("device",d.getAddress()).put("connectedSince",connectedSince.containsKey(uuid)?connectedSince.get(uuid):0));
                }
            }
            audioPolicySet.retainAll(seenDevices);previousStates.keySet().retainAll(seenCalls);connectedSince.keySet().retainAll(seenCalls);
            JSONObject state=new JSONObject().put("event","HFP_STATE").put("connectedDevices",matching).put("calls",calls);
            try{if(callWake!=null){
                if(calls.length()>0&&(!callWake.isHeld()||SystemClock.elapsedRealtime()-wakeRenewed>45000)){callWake.acquire(90000);wakeRenewed=SystemClock.elapsedRealtime();}
                else if(calls.length()==0&&callWake.isHeld())callWake.release();
            }}catch(Exception e){error("HFP_ROOT_WAKE_LOCK_FAILED_USING_BRIDGE_WAKE_LOCK",e);callWake=null;}
            String payload=state.toString();if(!payload.equals(lastState)||matching>0){emit(state);lastState=payload;}
        }catch(Throwable e){error("HFP_READ_CALLS_FAILED",e);}
    }
    @SuppressWarnings("unchecked") private static void command(JSONObject b){
        try {
            String op=b.optString("op");
            if(op.equals("snapshot")){snapshot();return;}
            if(op.equals("stop")){shutdown();return;}
            if(op.equals("connect")){
                String address=b.optString("device");if(!BluetoothAdapter.checkBluetoothAddress(address))throw new IllegalArgumentException("Invalid peer address");
                BluetoothDevice d=adapter.getRemoteDevice(address);
                ParcelUuid[] uuids=d.getUuids();boolean ag=false;
                if(uuids!=null)for(ParcelUuid uuid:uuids)if(uuid.getUuid().equals(UUID.fromString("0000111f-0000-1000-8000-00805f9b34fb")))ag=true;
                event("HFP_PEER_AUDIO_GATEWAY_UUID_"+(ag?"PRESENT":uuids==null?"UNKNOWN":"ABSENT"));
                event("HFP_PEER_DEVICE_TYPE_"+d.getType());
                if(d.getType()==BluetoothDevice.DEVICE_TYPE_LE){event("HFP_PEER_BLE_ONLY_PAIR_CLASSIC_FROM_IPHONE_SETTINGS");return;}
                peer=address;
                if(proxy==null){event("HFP_CONNECT_PROXY_NOT_READY");return;}
                boolean policy=(Boolean)invoke(proxy,"setConnectionPolicy",new Class[]{BluetoothDevice.class,int.class},d,100);
                invoke(proxy,"setAudioRouteAllowed",new Class[]{BluetoothDevice.class,boolean.class},d,false);
                boolean ok=proxy.getConnectionState(d)!=0||(Boolean)invoke(proxy,"connect",new Class[]{BluetoothDevice.class},d);
                event(ok||policy?"HFP_CONNECT_REQUESTED":"HFP_CONNECT_REJECTED");return;
            }
            if(proxy==null){event("HFP_COMMAND_PROXY_NOT_READY");return;}
            String expected=b.optString("uuid"),address=b.optString("device");boolean matched=false;
            for(BluetoothDevice d:proxy.getConnectedDevices()){
                if(!address.equals(d.getAddress()))continue;
                List<?> calls=(List<?>)invoke(proxy,"getCurrentCalls",new Class[]{BluetoothDevice.class},d);if(calls==null)continue;
                for(Object call:calls){
                    if(!expected.equals(String.valueOf(invoke(call,"getUUID",new Class[]{}))))continue;
                    int state=(Integer)invoke(call,"getState",new Class[]{});boolean accepted=false;matched=true;
                    if(state==7){event("HFP_STALE_CALL_COMMAND_REJECTED");return;}
                    if(op.equals("answer")&&(state==4||state==5))accepted=(Boolean)invoke(proxy,"acceptCall",new Class[]{BluetoothDevice.class,int.class},d,0);
                    else if(op.equals("hangup"))accepted=(Boolean)invoke(proxy,"terminateCall",new Class[]{BluetoothDevice.class,call.getClass()},d,call);
                    else {event("HFP_COMMAND_INVALID_FOR_CURRENT_CALL_STATE");return;}
                    event(accepted?"HFP_"+op.toUpperCase(Locale.ROOT)+"_FORWARDED_AWAIT_STATE":"HFP_"+op.toUpperCase(Locale.ROOT)+"_REJECTED");
                }
            }if(!matched)event("HFP_STALE_CALL_COMMAND_REJECTED");snapshot();
        }catch(Throwable e){error("HFP_COMMAND_FAILED",e);}
    }
    private static void shutdown(){
        if(callWake!=null&&callWake.isHeld())callWake.release();
        try{
            if(proxy!=null){if(!peer.isEmpty())invoke(proxy,"setConnectionPolicy",new Class[]{BluetoothDevice.class,int.class},adapter.getRemoteDevice(peer),0);adapter.closeProfileProxy(CLIENT,proxy);}
        }catch(Exception e){error("HFP_STOP_FAILED",e);}event("HFP_WORKER_STOPPED");System.exit(0);
    }
}
