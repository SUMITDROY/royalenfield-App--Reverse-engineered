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
    private static String failureCodes(Context c){
        java.util.ArrayDeque<String> recent=new java.util.ArrayDeque<>();
        java.util.regex.Pattern pattern=java.util.regex.Pattern.compile("^\\d{4}-\\d{2}-\\d{2} [0-9:.]+ ([A-Z][A-Z0-9_]{2,100})(?: |$)");
        for(String name:new String[]{"iphone-bridge.previous.log","iphone-bridge.log"})try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.FileReader(new java.io.File(c.getFilesDir(),name)))){
            String line;int limit=0;while((line=reader.readLine())!=null&&limit++<20000){java.util.regex.Matcher m=pattern.matcher(line);if(!m.find())continue;String code=m.group(1);if(code.contains("FAIL")||code.contains("TIMEOUT")||code.contains("MISSING")||code.contains("DENIED")||code.contains("UNSUPPORTED")||code.contains("REJECTED")){recent.add(code);if(recent.size()>30)recent.removeFirst();}}
        }catch(java.io.IOException ignored){}
        return recent.isEmpty()?"No matching failure codes in available logs":android.text.TextUtils.join("\n",recent);
    }
    static String support(Context c){return "Tripper support report — deliberately excludes raw logs, identities, locations and credentials\nModule 0.5.1; RE "+version(c)+"; API "+Build.VERSION.SDK_INT+"\n"+battery(c)+"\nBridge running="+BridgeState.running+"; ANCS="+BridgeState.callsReady+"; AMS="+BridgeState.mediaReady+"; HFP="+BridgeState.hfpReady+"\nDash telemetry fresh="+(SystemClock.elapsedRealtime()-BridgeState.dashUpdated<10000)+"\nRaw logs can contain personal information; export separately only after review.\n"+compatibility(c)+"\nRecent sanitized failure codes (no messages/stack traces):\n"+failureCodes(c);}
}
