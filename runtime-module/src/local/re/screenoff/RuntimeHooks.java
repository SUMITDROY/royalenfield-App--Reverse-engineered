package local.re.screenoff;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.PowerManager;
import android.view.Window;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.ArrayList;
import java.util.List;

/** Version-pinned lifecycle hooks. Does not change account authentication. */
public final class RuntimeHooks implements IXposedHookLoadPackage {
    private static final String NAV="com.royalenfield.reprime.ui.home.navigation.fragment.NavigationFragment";
    private static Context context;
    private static volatile boolean active;
    private static boolean ready;
    private static Throwable setupError;
    private static ClassLoader loader;
    private static final List<XC_MethodHook.Unhook> installed=new ArrayList<>();

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if("com.android.bluetooth".equals(p.packageName)){HfpCompatibilityHooks.install(p);return;}
        if (!"com.royalenfield.reprime".equals(p.packageName) || !p.packageName.equals(p.processName)) return;
        XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if(context!=null)return;
                context=(Context)param.args[0]; loader=p.classLoader;
                // Application.getApplicationContext can be null during attach; initialize after onCreate.
                XposedBridge.hookAllMethods(XposedHelpers.findClass("com.royalenfield.reprime.RoyalEnfieldApp",loader),"onCreate",new XC_MethodHook(){
                    @Override protected void afterHookedMethod(MethodHookParam ignored){
                        Diagnostics.init(context);
                        Diagnostics.event(context,ready ? "RUNTIME_HOOKS_READY_VERSION_193" : "RUNTIME_HOOKS_DISABLED");
                        if(setupError!=null)Diagnostics.error("HOOK_SETUP_FAILED",setupError);
                        if(ready)IPhoneDashHooks.install(context,loader);
                    }
                });
                try {
                    long version=context.getPackageManager().getPackageInfo(p.packageName,0).getLongVersionCode();
                    if(version!=193){android.util.Log.e("REScreenOff","UNSUPPORTED_VERSION "+version);return;}
                    setup();ready=true;
                } catch(Throwable error){
                    for(XC_MethodHook.Unhook hook:installed)hook.unhook();installed.clear();
                    active=false; setupError=error;android.util.Log.e("REScreenOff","HOOK_SETUP_FAILED",error);
                    XposedBridge.log(error);
                }
            }
        });
    }

    private static void hook(String cls,String method,XC_MethodHook callback,Object... types) {
        Object[] args=new Object[types.length+1];System.arraycopy(types,0,args,0,types.length);args[types.length]=callback;
        installed.add(XposedHelpers.findAndHookMethod(cls,loader,method,args));
    }

    private static boolean retain(){
        String reason="ENCODER_NOT_STARTED";
        try {
            if(active){
                Object manager=XposedHelpers.callStaticMethod(XposedHelpers.findClass("bluconnect.zif",loader),"y");
                Object device=XposedHelpers.callMethod(manager,"o");
                Object dash=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("bluconnect.zif$b",loader),"o2");
                if(device!=dash)reason="DEVICE_NOT_TRIPPER_DASH";
                else {
                    PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
                    if(power!=null && !power.isInteractive()){
                        Diagnostics.decision(context,"RETAINED_WHILE_SCREEN_OFF");return true;
                    }
                    reason="DISPLAY_STILL_INTERACTIVE";
                }
            }
            Diagnostics.decision(context,reason);
        }catch(Throwable error){Diagnostics.error("RETAIN_CHECK_FAILED",error);}
        return false;
    }

    private static void baseLifecycle(Object fragment,String callback)throws Throwable{
        // Version 193 Fragment base callbacks only set mCalled=true. Reflective
        // invocation dispatches back into the override on Vector, recursing.
        XposedHelpers.setBooleanField(fragment,"mCalled",true);
    }

    private static void lifecycle(String cls,String callback,final String paired) {
        hook(cls,callback,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{
                if(retain()){
                    baseLifecycle(p.thisObject,callback);
                    if(paired!=null)XposedHelpers.setAdditionalInstanceField(p.thisObject,paired,Boolean.TRUE);
                    Diagnostics.event(context,cls+"."+callback+"_RETAINED");p.setResult(null);
                }
            }
        });
    }

    private static void setup(){
        // Resolve every required method before enabling any behavioral hooks.
        String root="com.royalenfield.reprime.ui.home.navigation.fragment.NavigationRootFragment";
        String sdk="com.google.android.libraries.navigation.SupportNavigationFragment";
        XposedHelpers.findField(XposedHelpers.findClass("androidx.fragment.app.Fragment",loader),"mCalled");
        for(String cls:new String[]{NAV,root,sdk}){
            XposedHelpers.findMethodExact(XposedHelpers.findClass(cls,loader),"onPause");
        }
        XposedHelpers.findMethodExact(XposedHelpers.findClass("bluconnect.w9f",loader),"d");
        XposedHelpers.findMethodExact(XposedHelpers.findClass("bluconnect.w9f",loader),"e");
        hook("bluconnect.gub","i",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable()){Diagnostics.error("ENCODER_START_FAILED",p.getThrowable());return;}
                active=true;Diagnostics.begin();
            }
        });
        hook("bluconnect.gub","j",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){active=false;Diagnostics.end();}
            @Override protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())Diagnostics.error("ENCODER_STOP_FAILED",p.getThrowable());}
        });
        lifecycle(NAV,"onPause",null);lifecycle(NAV,"onStop",null);lifecycle(root,"onPause",null);
        lifecycle(sdk,"onPause","re_paused");lifecycle(sdk,"onStop","re_stopped");
        for(final String[] pair:new String[][]{{"onResume","re_paused"},{"onStart","re_stopped"}}){
            hook(sdk,pair[0],new XC_MethodHook(){
                @Override protected void beforeHookedMethod(MethodHookParam p)throws Throwable{
                    if(Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(p.thisObject,pair[1]))){
                        baseLifecycle(p.thisObject,pair[0]);XposedHelpers.removeAdditionalInstanceField(p.thisObject,pair[1]);p.setResult(null);
                    }
                }
            });
        }
        installed.add(XposedHelpers.findAndHookMethod(PowerManager.class,"newWakeLock",int.class,String.class,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                if("reprime::WakeLock".equals(p.args[1]) && ((Integer)p.args[0])==0x3000001a){
                    p.args[0]=PowerManager.PARTIAL_WAKE_LOCK;
                    Diagnostics.event(context,"NAV_WAKELOCK_CHANGED_TO_PARTIAL");
                }
            }
        }));
        installed.add(XposedHelpers.findAndHookMethod(Window.class,"addFlags",int.class,new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
                if(((Integer)p.args[0])==0x680080)p.args[0]=0;
            }
        }));
        // Bitmap callback and encoder submission logs, without storing the bitmap.
        hook(NAV,"r",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){Diagnostics.snapshot((Bitmap)p.args[0]);}
        },Bitmap.class);
        hook("bluconnect.gub","g",new XC_MethodHook(){
            @Override protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable())Diagnostics.error("ENCODER_SUBMIT_FAILED",p.getThrowable());else Diagnostics.frameSubmitted();
            }
        },Bitmap.class,String.class);
    }
}
