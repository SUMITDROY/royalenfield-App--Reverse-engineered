package local.re.screenoff;

import android.content.Context;
import android.graphics.*;
import android.net.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;

/** Opt-in catalog lookup, with exact title/artist matching; never claims exact iPhone artwork. */
final class ArtworkLookup {
    private final Context context;private final Runnable updated;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile Bitmap cover;private volatile long generation;private String key="";
    ArtworkLookup(Context c,Runnable r){context=c;updated=r;clear();}
    Bitmap bitmap(){return cover;}
    private void clear(){cover=null;new File(context.getFilesDir(),"cover.png").delete();BridgeState.artVersion++;}
    void changed(){
        final String title=BridgeState.title,artist=BridgeState.artist;
        boolean enabled=context.getSharedPreferences("bridge",0).getBoolean("artwork",false);
        String next=enabled?title+"\n"+artist:"";
        if(next.equals(key))return;key=next;final long token=++generation;clear();updated.run();
        if(!enabled||title.isEmpty()||artist.isEmpty())return;
        handler.postDelayed(()->{
            if(token!=generation)return;
            worker.execute(()->lookup(title,artist,token));
        },1500);
    }
    void preferenceChanged(){key="\u0000";changed();}
    private static String normalized(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    private byte[] download(URL url,int max)throws Exception {
        if(!"https".equals(url.getProtocol()))throw new IOException("HTTPS required");
        ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);Network selected=null;
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            if(caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)){
                selected=n;if(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))break;
            }
        }
        if(selected==null)throw new IOException("No validated internet network; Dash Wi-Fi may have no internet");
        HttpURLConnection connection=(HttpURLConnection)selected.openConnection(url);
        connection.setConnectTimeout(7000);connection.setReadTimeout(7000);connection.setInstanceFollowRedirects(false);
        try {
            int code=connection.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);
            if(connection.getContentLengthLong()>max)throw new IOException("Response exceeds size limit");
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int count;
                while((count=in.read(buffer))!=-1){if(out.size()+count>max)throw new IOException("Response exceeds size limit");out.write(buffer,0,count);}
                return out.toByteArray();
            }
        }finally{connection.disconnect();}
    }
    private void lookup(String title,String artist,long token){
        try {
            String query=URLEncoder.encode(title+" "+artist,"UTF-8");
            JSONObject response=new JSONObject(new String(download(new URL("https://itunes.apple.com/search?entity=song&limit=8&term="+query),256*1024),StandardCharsets.UTF_8));
            JSONArray results=response.optJSONArray("results");String url=null;
            if(results!=null)for(int i=0;i<results.length();i++){
                JSONObject item=results.getJSONObject(i);
                if(normalized(title).equals(normalized(item.optString("trackName")))&&normalized(artist).equals(normalized(item.optString("artistName")))){
                    url=item.optString("artworkUrl100",null);break;
                }
            }
            if(url==null){BridgeState.log("ARTWORK_NO_EXACT_CATALOG_MATCH",null);return;}
            URL image=new URL(url);String host=image.getHost().toLowerCase(Locale.ROOT);
            if(!host.endsWith(".mzstatic.com"))throw new IOException("Unexpected artwork host");
            byte[] imageBytes=download(image,512*1024);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length,bounds);
            if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>1024||bounds.outHeight>1024)throw new IOException("Invalid artwork dimensions");
            Bitmap decoded=BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);
            if(decoded==null)throw new IOException("Artwork decode failed");
            Bitmap small=Bitmap.createScaledBitmap(decoded,96,96,true);if(small!=decoded)decoded.recycle();
            handler.post(()->{
                if(token!=generation){small.recycle();return;}
                File temp=new File(context.getFilesDir(),"cover.tmp"),target=new File(context.getFilesDir(),"cover.png");
                try {
                    try(FileOutputStream out=new FileOutputStream(temp)){if(!small.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Artwork encode failed");}
                    if(!temp.renameTo(target))throw new IOException("Artwork cache rename failed");
                    cover=small;BridgeState.artVersion++;BridgeState.changed++;BridgeState.log("ARTWORK_CATALOG_MATCH_READY",null);updated.run();
                }catch(Exception e){temp.delete();BridgeState.log("ARTWORK_CACHE_FAILED",e);}
            });
        }catch(Exception e){BridgeState.log("ARTWORK_LOOKUP_FAILED",e);}
    }
    void close(){generation++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();clear();}
}
