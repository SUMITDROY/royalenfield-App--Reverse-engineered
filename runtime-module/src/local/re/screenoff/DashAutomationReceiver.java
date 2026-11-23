package local.re.screenoff;
import android.bluetooth.BluetoothDevice;
import android.content.*;

/** Explicitly selected Dash ACL only. Does not invent navigation state from a Bluetooth bond. */
public final class DashAutomationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        SharedPreferences p=c.getSharedPreferences("bridge",0);if(!p.getBoolean("autoRide",false))return;
        BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);if(d==null||!d.getAddress().equals(p.getString("dashPeer","")))return;
        if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())){
            p.edit().putBoolean("autoSession",true).apply();if(p.getString("profile","music").equals("navigation"))return;
            try{c.startForegroundService(new Intent(c,BridgeService.class));}catch(Exception e){BridgeState.init(c);BridgeState.log("AUTO_START_BLOCKED_OPEN_BRIDGE",e);}
        }else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(i.getAction())&&p.getBoolean("autoSession",false)){
            p.edit().putBoolean("autoSession",false).apply();if(BridgeService.current!=null)BridgeService.current.stopAfterDashDisconnect();
        }
    }
}
