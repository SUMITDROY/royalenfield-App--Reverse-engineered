package local.re.screenoff;
import android.content.*;
public final class BridgeBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||!c.getSharedPreferences("bridge",0).getBoolean("enabled",false))return;
        try{c.startForegroundService(new Intent(c,BridgeService.class));}catch(Exception e){BridgeState.init(c);BridgeState.log("BOOT_START_FAILED_OPEN_BRIDGE",e);}
    }
}
