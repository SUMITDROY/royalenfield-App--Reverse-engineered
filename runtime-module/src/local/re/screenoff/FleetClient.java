package local.re.screenoff;
import android.content.*;
import android.location.Location;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** No queued locations. Sharing is rechecked before each HTTPS send and disabled by default. */
final class FleetClient {
    private final Context context;private final ExecutorService worker=Executors.newSingleThreadExecutor();private long last;
    static volatile String status="No request this session";
    FleetClient(Context c){context=c.getApplicationContext();}
    private JSONObject request(String path,JSONObject body)throws Exception{
        SharedPreferences p=context.getSharedPreferences("bridge",0);String base=p.getString("fleetUrl","");URL u=new URL(base+path);
        if(!u.getProtocol().equals("https")||u.getUserInfo()!=null)throw new IOException("A trusted HTTPS server is required");
        HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("Authorization","Bearer "+p.getString("fleetToken",""));
        try{
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();if(code!=200)throw new IOException("Server returned HTTP "+code);
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>262144)throw new IOException("Response too large");out.write(b,0,n);}}
            return new JSONObject(out.toString("UTF-8"));
        }finally{c.disconnect();}
    }
    void location(Location l,long ride){SharedPreferences p=context.getSharedPreferences("bridge",0);if(!p.getBoolean("sharing",false)||p.getBoolean("guest",false)||System.currentTimeMillis()-last<15000)return;last=System.currentTimeMillis();
        worker.execute(()->{try{if(!p.getBoolean("sharing",false)||p.getBoolean("guest",false))return;
            request("/api/location",new JSONObject().put("lat",l.getLatitude()).put("lon",l.getLongitude()).put("accuracy",l.getAccuracy()).put("time",l.getTime()).put("ride",ride));status="Location sent at "+new java.util.Date();
        }catch(Exception e){status="Location not sent: "+e.getMessage();BridgeState.log("FLEET_SEND_FAILED",null);}});
    }
    void assignments(Runnable done){worker.execute(()->{try{JSONObject r=request("/api/assignments",null);context.getSharedPreferences("bridge",0).edit().putString("assignments",r.getJSONArray("routes").toString()).putString("groupPositions",r.optJSONArray("positions")==null?"[]":r.getJSONArray("positions").toString()).apply();status="Assignments refreshed";}catch(Exception e){status="Refresh failed: "+e.getMessage();}new android.os.Handler(android.os.Looper.getMainLooper()).post(done);});}
    void deleteMine(Runnable done){worker.execute(()->{try{request("/api/delete-my-data",new JSONObject());status="Remote rider data deleted";}catch(Exception e){status="Remote deletion failed: "+e.getMessage();}new android.os.Handler(android.os.Looper.getMainLooper()).post(done);});}
    void close(){worker.shutdownNow();}
}
