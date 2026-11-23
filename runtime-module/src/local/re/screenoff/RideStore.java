package local.re.screenoff;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.location.Location;
import org.json.*;
import java.io.*;
import java.util.*;

/** App-private ride data. Additive schema; no destructive migrations or remote uploads. */
final class RideStore extends SQLiteOpenHelper {
    RideStore(Context c){super(c,"rides.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE rides(id INTEGER PRIMARY KEY,start INTEGER,end INTEGER,distance REAL DEFAULT 0,interruptions INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE points(ride INTEGER,time INTEGER,lat REAL,lon REAL,accuracy REAL)");
        d.execSQL("CREATE INDEX points_ride ON points(ride,time)");
        d.execSQL("CREATE TABLE records(id INTEGER PRIMARY KEY,kind TEXT,time INTEGER,data TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int old,int next){throw new IllegalStateException("Unsupported schema migration");}
    synchronized long begin(){ContentValues v=new ContentValues();v.put("start",System.currentTimeMillis());return getWritableDatabase().insertOrThrow("rides",null,v);}
    synchronized void finish(long id,double meters,int interruptions){ContentValues v=new ContentValues();v.put("end",System.currentTimeMillis());v.put("distance",meters);v.put("interruptions",interruptions);getWritableDatabase().update("rides",v,"id=?",new String[]{""+id});}
    synchronized void checkpoint(long id,double meters,int interruptions){ContentValues v=new ContentValues();v.put("distance",meters);v.put("interruptions",interruptions);getWritableDatabase().update("rides",v,"id=?",new String[]{""+id});}
    synchronized void point(long id,Location l){ContentValues v=new ContentValues();v.put("ride",id);v.put("time",l.getTime());v.put("lat",l.getLatitude());v.put("lon",l.getLongitude());v.put("accuracy",l.getAccuracy());getWritableDatabase().insertOrThrow("points",null,v);}
    synchronized void record(String kind,JSONObject data){ContentValues v=new ContentValues();v.put("kind",kind);v.put("time",System.currentTimeMillis());v.put("data",data.toString());getWritableDatabase().insertOrThrow("records",null,v);}
    synchronized JSONArray records(String kind){JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,time,data FROM records WHERE kind=? ORDER BY id DESC LIMIT 200",new String[]{kind})){while(c.moveToNext())try{JSONObject o=new JSONObject(c.getString(2));o.put("id",c.getLong(0));o.put("time",c.getLong(1));a.put(o);}catch(JSONException ignored){}}return a;}
    synchronized JSONArray rides(){JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,start,end,distance,interruptions FROM rides ORDER BY id DESC LIMIT 200",null)){while(c.moveToNext())try{a.put(new JSONObject().put("id",c.getLong(0)).put("start",c.getLong(1)).put("end",c.isNull(2)?0:c.getLong(2)).put("meters",c.getDouble(3)).put("interruptions",c.getInt(4)));}catch(JSONException ignored){}}return a;}
    synchronized long latest(){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM rides ORDER BY id DESC LIMIT 1",null)){return c.moveToFirst()?c.getLong(0):-1;}}
    synchronized void gpx(long id,OutputStream out)throws IOException {
        Writer w=new OutputStreamWriter(out,java.nio.charset.StandardCharsets.UTF_8);
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"TripperBridge\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>Recorded ride "+id+"</name><trkseg>");
        java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        try(Cursor c=getReadableDatabase().rawQuery("SELECT time,lat,lon FROM points WHERE ride=? ORDER BY time",new String[]{""+id})){while(c.moveToNext())w.write("<trkpt lat=\""+c.getDouble(1)+"\" lon=\""+c.getDouble(2)+"\"><time>"+fmt.format(new Date(c.getLong(0)))+"</time></trkpt>");}
        w.write("</trkseg></trk></gpx>");w.flush();
    }
    synchronized JSONObject backup(Context c)throws Exception {
        JSONObject prefs=new JSONObject();for(Map.Entry<String,?> e:c.getSharedPreferences("bridge",0).getAll().entrySet()){
            // No credentials, device identifiers, automatic root startup or location consent in portable backups.
            if(Arrays.asList("profile","privacy","overlay","overlayColor","overlaySide","healthOverlay","artwork").contains(e.getKey()))prefs.put(e.getKey(),e.getValue());
        }return new JSONObject().put("schema",1).put("preferences",prefs);
    }
    synchronized void restore(Context c,JSONObject backup)throws Exception {
        if(backup.getInt("schema")!=1)throw new IOException("Unsupported backup schema");
        JSONObject p=backup.getJSONObject("preferences");SharedPreferences.Editor edit=c.getSharedPreferences("bridge",0).edit();
        for(String k:new String[]{"privacy","overlay","healthOverlay","artwork"})if(p.has(k))edit.putBoolean(k,p.getBoolean(k));
        if(p.has("overlayColor"))edit.putInt("overlayColor",p.getInt("overlayColor"));
        if(p.has("overlaySide"))edit.putString("overlaySide",p.getString("overlaySide").equals("left")?"left":"right");
        if(p.has("profile")){String v=p.getString("profile");if(Arrays.asList("navigation","music","full").contains(v))edit.putString("profile",v);}
        edit.putBoolean("hfpEnabled",false).putBoolean("sharing",false).apply();
    }
}
