package local.re.screenoff;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadata;
import android.os.*;
import android.net.Uri;
import de.robv.android.xposed.*;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.*;

/** Optional bridge integration; failures never remove the screen-off hooks. */
final class IPhoneDashHooks {
    private static Context context;private static ClassLoader loader;
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static Handler worker;private static volatile Bundle state=Bundle.EMPTY;
    private static volatile Bitmap art;private static volatile long lastPoll;
    private static long artVersion=-1,lastError,lastForward;private static String sentCall="",sentMedia="";
    private static WeakReference<Object> foreground=new WeakReference<>(null);
    private static boolean reportedOverlay;
    private static volatile boolean overlayDisabled;
    private static volatile int selectedAction=0;private static volatile String actionToken="";
    private static final String SERVICE="com.royalenfield.reprime.ui.home.navigation.service.REForeGroundService";
    static void install(Context c,ClassLoader l){
        context=c;loader=l;List<XC_MethodHook.Unhook> hooks=new ArrayList<>();
        try {
            hooks.add(XposedHelpers.findAndHookMethod(SERVICE,loader,"onStartCommand",android.content.Intent.class,int.class,int.class,new XC_MethodHook(){
                @Override protected void afterHookedMethod(MethodHookParam p){foreground=new WeakReference<>(p.thisObject);sentMedia="";}
            }));
            for(String cls:new String[]{SERVICE,"bluconnect.djf"}){
                // Verified version-193 mappings: H toggle, s next, n previous, N/M volume.
                for(String method:new String[]{"H","s","n","N","M"}){
                    final int command="H".equals(method)?2:"s".equals(method)?3:"n".equals(method)?4:"N".equals(method)?5:6;
                    hooks.add(XposedHelpers.findAndHookMethod(cls,loader,method,new XC_MethodHook(){
                        @Override protected void beforeHookedMethod(MethodHookParam p){
                            if(fresh()&&state.getBoolean("running")&&!state.getString("call","").isEmpty()&&(command==2||command==3||command==4)){
                                event("CALL_CONTROL_BUTTON_RECEIVED_"+command);
                                final Bundle current=state;final String token=current.getString("callToken","");
                                if(!token.equals(actionToken)){actionToken=token;selectedAction=current.getString("positiveLabel","").isEmpty()?1:0;}
                                selectedAction=CallActionGate.selection(selectedAction,current.getString("positiveLabel",""),current.getString("negativeLabel",""));
                                if(command==2){
                                    int action=selectedAction;String label=current.getString(action==0?"positiveLabel":"negativeLabel","");
                                    if(!label.isEmpty()&&!token.isEmpty())worker.post(()->{
                                        event("CALL_ACTION_BUTTON_SUBMITTED_"+action);
                                        try{Bundle args=new Bundle();args.putString("token",token);args.putInt("action",action);context.getContentResolver().call(BridgeProvider.URI,"callAction",null,args);}
                                        catch(Exception e){error("IPHONE_CALL_ACTION_IPC_FAILED",e);}
                                    });else event("CALL_ACTION_UNAVAILABLE");
                                }else{
                                    if(!current.getString("positiveLabel","").isEmpty()&&!current.getString("negativeLabel","").isEmpty())selectedAction=1-selectedAction;
                                }
                                p.setResult(null);return;
                            }
                            if(!fresh()||!state.getBoolean("mediaReady")||!state.getBoolean("running"))return;
                            worker.post(()->{
                                try{Bundle b=new Bundle();b.putInt("command",command);context.getContentResolver().call(BridgeProvider.URI,"command",null,b);}
                                catch(Exception e){error("IPHONE_MEDIA_COMMAND_IPC_FAILED",e);}
                            });p.setResult(null);
                        }
                    }));
                }
            }
            hooks.add(XposedHelpers.findAndHookMethod("bluconnect.gub",loader,"g",Bitmap.class,String.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p){
                    if(overlayDisabled||!fresh()||!state.getBoolean("overlay")||!state.getBoolean("running"))return;
                    try{Bitmap original=(Bitmap)p.args[0];Bitmap composite=overlay(original);if(composite!=original)p.args[0]=composite;}
                    catch(Throwable e){if(e instanceof OutOfMemoryError)overlayDisabled=true;error("IPHONE_OVERLAY_FAILED",e);}
                }
            }));
            HandlerThread thread=new HandlerThread("TripperIPhoneBridge");thread.start();worker=new Handler(thread.getLooper());
            worker.post(new Runnable(){public void run(){poll();worker.postDelayed(this,1000);}});
            Diagnostics.event(context,"IPHONE_BRIDGE_HOOKS_READY");
        }catch(Throwable e){for(XC_MethodHook.Unhook h:hooks)h.unhook();Diagnostics.error("IPHONE_BRIDGE_HOOK_SETUP_FAILED_SCREENOFF_UNCHANGED",e);}
    }
    private static boolean fresh(){return lastPoll>0&&SystemClock.elapsedRealtime()-lastPoll<5000;}
    private static void poll(){
        try {
            Bundle b=context.getContentResolver().call(BridgeProvider.URI,"state",null,null);
            if(b==null)throw new IllegalStateException("Empty bridge provider response");
            long version=b.getLong("artVersion");
            if(version!=artVersion){
                artVersion=version;art=null;
                if(b.getBoolean("mediaReady")&&!b.getString("title","").isEmpty())try(InputStream in=context.getContentResolver().openInputStream(Uri.withAppendedPath(BridgeProvider.URI,"art"))){
                    Bitmap decoded=BitmapFactory.decodeStream(in);if(decoded!=null&&decoded.getWidth()<=96&&decoded.getHeight()<=96)art=decoded;
                }catch(java.io.FileNotFoundException ignored){}
            }
            state=b;lastPoll=SystemClock.elapsedRealtime();main.post(()->forward(b));
        }catch(Exception e){state=Bundle.EMPTY;art=null;lastPoll=0;error("IPHONE_BRIDGE_IPC_FAILED",e);}
    }
    private static void event(String s){worker.post(()->{try{context.getContentResolver().call(BridgeProvider.URI,"event",s,null);}catch(Exception ignored){}});}
    private static void error(String s,Throwable e){long now=SystemClock.elapsedRealtime();if(now-lastError<15000)return;lastError=now;Diagnostics.error(s,e);event(s);}
    private static boolean connected(){
        Object manager=XposedHelpers.callStaticMethod(XposedHelpers.findClass("bluconnect.zif",loader),"y");
        Object dash=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("bluconnect.zif$b",loader),"o2");
        if(XposedHelpers.callMethod(manager,"o")!=dash)return false;
        Object transport=XposedHelpers.callMethod(manager,"L");return transport!=null&&XposedHelpers.getObjectField(transport,"b")!=null;
    }
    private static void forward(Bundle b){
        try {
            if(!connected()){sentCall="";sentMedia="";return;}
            String call=b.getString("call",""),phase=b.getString("callPhase","");String callKey=call+"/"+phase;
            if(!callKey.equals(sentCall)){
                Object calls=XposedHelpers.callStaticMethod(XposedHelpers.findClass("bluconnect.r42",loader),"m");
                if(!call.isEmpty()){
                    String encoded=(String)XposedHelpers.callMethod(calls,"h",call,"");
                    XposedHelpers.callMethod(calls,"y",encoded,"0522");XposedHelpers.callMethod(calls,"u",phase.equals("active")||phase.equals("held")?"ACTIVE_CALL":phase.equals("outgoing")?"OUTGOING_CALL":"INCOMING_CALL");event("IPHONE_CALL_PACKET_SUBMITTED");
                }else if(!sentCall.isEmpty()&&!sentCall.equals("/")){XposedHelpers.callMethod(calls,"u","NO_CALL");event("IPHONE_RINGING_CLEAR_PACKET_SUBMITTED");}
                sentCall=callKey;
            }
            String title=b.getString("title","");String key=title+"/"+b.getString("artist","")+"/"+b.getString("album","")+"/"+b.getLong("artVersion")+"/"+b.getBoolean("playing");
            Object service=foreground.get();
            if(!b.getBoolean("mediaReady")&&!sentMedia.isEmpty()&&service!=null){
                XposedHelpers.callMethod(service,"A",new Object[]{null});sentMedia="";event("IPHONE_MEDIA_CLEAR_PACKET_SUBMITTED");
            }
            if(b.getBoolean("mediaReady")&&!title.isEmpty()&&service!=null&&!key.equals(sentMedia)&&SystemClock.elapsedRealtime()-lastForward>1000){
                MediaMetadata.Builder metadata=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST,b.getString("artist",""))
                    .putString(MediaMetadata.METADATA_KEY_ALBUM,b.getString("album",""))
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,key);
                Bitmap image=art;if(image!=null)metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,image);
                Object compat=XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.support.v4.media.MediaMetadataCompat",loader),"b",metadata.build());
                XposedHelpers.callMethod(service,"A",compat);sentMedia=key;lastForward=SystemClock.elapsedRealtime();event("IPHONE_MEDIA_PACKET_SUBMITTED");
            }
        }catch(Throwable e){error("IPHONE_DASH_FORWARD_FAILED",e);}
    }
    static Bitmap overlay(Bitmap source){
        Bundle current=new Bundle(state);String token=current.getString("callToken","");
        if(!token.equals(actionToken)){actionToken=token;selectedAction=current.getString("positiveLabel","").isEmpty()?1:0;}
        selectedAction=CallActionGate.selection(selectedAction,current.getString("positiveLabel",""),current.getString("negativeLabel",""));
        current.putInt("selectedAction",selectedAction);
        Bitmap copy=OverlayRenderer.render(source,current,art);
        if(copy!=source&&!reportedOverlay){reportedOverlay=true;event("IPHONE_OVERLAY_ENCODE_SUBMITTED");}
        return copy;
    }
}
