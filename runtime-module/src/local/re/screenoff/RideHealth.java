package local.re.screenoff;
import android.content.*;
import android.os.*;
import android.content.pm.PackageInfo;

final class RideHealth {
    static String battery(Context c){Intent i=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(i==null)return "Android battery unknown";int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);return "Android "+(level<0||scale<=0?"?":level*100/scale)+"%"+(i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0?" charging":"");}
    static long version(Context c){try{return c.getPackageManager().getPackageInfo("com.royalenfield.reprime",0).getLongVersionCode();}catch(Exception e){return -1;}}
    static String compatibility(Context c){long v=version(c);StringBuilder s=new StringBuilder("Royal Enfield: "+v+" ("+(v==193||v==195?"supported hook version":"UNSUPPORTED — hooks disabled")+")\nAndroid API "+Build.VERSION.SDK_INT+"; HFP compatibility fix: API 35 only\n");
        for(String p:new String[]{"BLUETOOTH_CONNECT","BLUETOOTH_ADVERTISE","POST_NOTIFICATIONS","ACCESS_FINE_LOCATION","READ_CONTACTS"})s.append(p).append(": ").append(c.checkSelfPermission("android.permission."+p)==0?"granted":"not granted").append('\n');
        android.location.LocationManager lm=c.getSystemService(android.location.LocationManager.class);s.append("Location services: ").append(lm!=null&&lm.isLocationEnabled()?"on":"off");
        s.append("\nModule loading: ").append(BridgeState.lastHook>0&&SystemClock.elapsedRealtime()-BridgeState.lastHook<10000?"RE IPC alive (not proof of Dash delivery)":"no recent RE IPC");
        s.append("\nOffline map downloads: not exposed by official RE app; verify manually in navigation provider.");return s.toString();}
    static String support(Context c){return "Tripper support report — deliberately excludes raw logs, identities, locations and credentials\nModule 0.5; RE "+version(c)+"; API "+Build.VERSION.SDK_INT+"\n"+battery(c)+"\nBridge running="+BridgeState.running+"; ANCS="+BridgeState.callsReady+"; AMS="+BridgeState.mediaReady+"; HFP="+BridgeState.hfpReady+"\nDash telemetry fresh="+(SystemClock.elapsedRealtime()-BridgeState.dashUpdated<10000)+"\nRaw logs can contain personal information; export separately only after review.\n"+compatibility(c);}
}
