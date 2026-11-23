package local.re.screenoff;
import android.app.Instrumentation;
import android.os.Bundle;
import android.location.Location;
import java.io.*;
import org.json.*;

public final class RideToolsDeviceTest extends Instrumentation {
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();RideStore store=null;try{
        android.content.Context c=getTargetContext();c.deleteDatabase("rides.db");c.getSharedPreferences("bridge",0).edit().clear().commit();BridgeState.init(c);store=new RideStore(c);
        long id=store.begin();Location l=new Location("test");l.setLatitude(12.3);l.setLongitude(77.4);l.setAccuracy(5);l.setTime(100000);store.point(id,l);store.checkpoint(id,125,2);
        JSONObject active=store.rides().getJSONObject(0);check(active.getLong("end")==0&&active.getDouble("meters")==125,"Checkpoint preserves active ride");
        store.finish(id,150,2);check(store.rides().getJSONObject(0).getLong("end")>0,"Saved summary");
        ByteArrayOutputStream out=new ByteArrayOutputStream();store.gpx(id,out);String gpx=out.toString("UTF-8");check(gpx.contains("lat=\"12.3\"")&&gpx.contains("1970-01-01T00:01:40.000Z"),"GPX contains UTC and coordinates");
        store.record("maintenance",new JSONObject().put("task","Tyres"));check(store.records("maintenance").length()==1,"Maintenance persists");
        c.getSharedPreferences("bridge",0).edit().putBoolean("privacy",true).putString("fleetToken","PRIVATE_SECRET").putString("emergencyContact","PRIVATE_PERSON").putBoolean("sharing",true).putBoolean("hfpEnabled",true).commit();
        JSONObject backup=store.backup(c);check(!backup.toString().contains("PRIVATE"),"Portable backup excludes credentials and personal data");
        store.restore(c,backup);check(!c.getSharedPreferences("bridge",0).getBoolean("sharing",true)&&!c.getSharedPreferences("bridge",0).getBoolean("hfpEnabled",true),"Restore cannot activate sharing/root");
        BridgeState.call="Private name";BridgeState.alert="Private text";BridgeState.callUntil=0;Bundle b=BridgeState.snapshot();check(b.getString("call").equals("Private caller")&&b.getString("alert").equals("Private notification"),"Privacy filters IPC");
        c.getSharedPreferences("bridge",0).edit().putString("profile","navigation").commit();b=BridgeState.snapshot();check(!b.getBoolean("running")&&b.getString("call").isEmpty(),"Navigation profile removes call data");
        String report=RideHealth.support(c);check(!report.contains("PRIVATE")&&!report.contains("Private name"),"Redacted report excludes sensitive state");
        result.putString("stream","PASS: ride checkpoints, summary, GPX UTC, maintenance, safe backup/restore, privacy/profile boundaries and support report\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e);finish(0,result);}finally{if(store!=null)store.close();}}
}
