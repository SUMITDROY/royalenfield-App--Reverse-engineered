package local.re.screenoff;
import android.content.*;
import android.os.Build;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Fix only the Android-15 HFP profile's legacy receiver registration. */
final class HfpCompatibilityHooks {
    private static final ThreadLocal<Boolean> inStart=new ThreadLocal<>();
    static void install(XC_LoadPackage.LoadPackageParam p){
        if(Build.VERSION.SDK_INT!=35||!p.packageName.equals("com.android.bluetooth")||!p.processName.equals("com.android.bluetooth"))return;
        XC_MethodHook.Unhook start=null,receiver=null,callUi=null;
        try {
            start=XposedHelpers.findAndHookMethod("com.android.bluetooth.hfpclient.HeadsetClientService",p.classLoader,"start",new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam param){inStart.set(true);}
                @Override protected void afterHookedMethod(MethodHookParam param){inStart.remove();}
            });
            receiver=XposedHelpers.findAndHookMethod(ContextWrapper.class,"registerReceiver",BroadcastReceiver.class,IntentFilter.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam param){
                    if(!Boolean.TRUE.equals(inStart.get()))return;
                    IntentFilter filter=(IntentFilter)param.args[1];
                    if(filter==null||!filter.hasAction("mediatek.bluetooth.action.headset_button_ckpd")||!filter.hasAction("android.media.VOLUME_CHANGED_ACTION"))return;
                    Object result=XposedHelpers.callMethod(param.thisObject,"registerReceiver",param.args[0],filter,Context.RECEIVER_NOT_EXPORTED);
                    param.setResult(result);android.util.Log.i("REHfpCompatibility","HFP_RECEIVER_REGISTERED_NOT_EXPORTED");
                }
            });
            callUi=XposedHelpers.findAndHookMethod(ContextWrapper.class,"startService",Intent.class,new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam param){
                    if(!Boolean.TRUE.equals(inStart.get()))return;
                    Intent intent=(Intent)param.args[0];ComponentName component=intent==null?null:intent.getComponent();
                    if(component!=null&&component.getClassName().equals("com.android.bluetooth.hfpclient.HfpClientConnectionService")){
                        // Our bridge owns call controls; avoid importing iPhone calls into the
                        // Android dialer, which otherwise presents an incoming-call screen.
                        param.setResult(null);android.util.Log.i("REHfpCompatibility","HFP_ANDROID_CALL_UI_SKIPPED_BRIDGE_CONTROLS");
                    }
                }
            });
            android.util.Log.i("REHfpCompatibility","HFP_RECEIVER_FLAG_PATCH_READY");
        }catch(Throwable e){if(start!=null)start.unhook();if(receiver!=null)receiver.unhook();if(callUi!=null)callUi.unhook();XposedBridge.log(e);android.util.Log.e("REHfpCompatibility","HFP_COMPATIBILITY_DISABLED",e);}
    }
}
