package local.re.screenoff;
import android.app.Application;
import android.os.Build;
public final class BridgeApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();BridgeState.init(this);
        BridgeState.log("BRIDGE_PROCESS_START_API_"+Build.VERSION.SDK_INT,null);
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            BridgeState.log("UNCAUGHT_JAVA_EXCEPTION",error);
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }
}
