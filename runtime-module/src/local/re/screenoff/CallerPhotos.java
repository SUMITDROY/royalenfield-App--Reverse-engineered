package local.re.screenoff;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.provider.ContactsContract;
import java.io.*;
import java.util.concurrent.*;

/** Matches only an actual HFP caller number; never guesses from ANCS display names. */
final class CallerPhotos {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static String key="";static volatile long version;static volatile boolean available;
    static synchronized void update(Context c,String number,String token){
        boolean allowed=c.checkSelfPermission("android.permission.READ_CONTACTS")==0&&c.getSharedPreferences("bridge",0).getBoolean("callerPhotos",false)&&!c.getSharedPreferences("bridge",0).getBoolean("privacy",false);
        String next=allowed?token+":"+number:"";if(next.equals(key))return;key=next;available=false;version++;new File(c.getFilesDir(),"caller.png").delete();
        if(!allowed||number==null||number.replaceAll("[^0-9]","").length()<3||token.isEmpty())return;
        worker.execute(()->{try{
            Uri uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number));String photo=null;
            try(Cursor cursor=c.getContentResolver().query(uri,new String[]{ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI},null,null,null)){if(cursor!=null&&cursor.moveToFirst())photo=cursor.getString(0);}
            if(photo==null)return;Bitmap decoded;
            try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(photo))){decoded=BitmapFactory.decodeStream(in);}
            if(decoded==null)return;Bitmap scaled=Bitmap.createScaledBitmap(decoded,72,72,true);
            synchronized(CallerPhotos.class){if(!key.equals(next))return;try(OutputStream out=new FileOutputStream(new File(c.getFilesDir(),"caller.png"))){scaled.compress(Bitmap.CompressFormat.PNG,100,out);}available=true;version++;}
        }catch(Exception e){BridgeState.log("CALLER_PHOTO_LOOKUP_FAILED",null);}});
    }
    static synchronized void clear(Context c){key="";available=false;version++;new File(c.getFilesDir(),"caller.png").delete();}
}
