package local.re.screenoff;

import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.security.MessageDigest;

/** Private IPC: only our UID and the installed official Royal Enfield signer. */
public final class BridgeProvider extends ContentProvider {
    static final String AUTHORITY="local.re.screenoff.module.bridge";
    static final Uri URI=Uri.parse("content://"+AUTHORITY);
    private void authorize() {
        int uid=Binder.getCallingUid();if(uid==android.os.Process.myUid())return;
        PackageManager pm=getContext().getPackageManager();String[] names=pm.getPackagesForUid(uid);
        if(names!=null)for(String name:names)if(name.equals("com.royalenfield.reprime"))try {
            PackageInfo info=pm.getPackageInfo(name,PackageManager.GET_SIGNING_CERTIFICATES);
            for(Signature cert:info.signingInfo.getApkContentsSigners()){
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(cert.toByteArray());StringBuilder hex=new StringBuilder();
                for(byte v:hash)hex.append(String.format(java.util.Locale.US,"%02x",v&255));
                if(hex.toString().equals("8d624c6e4768b6b619ddd97f8d3216f94a7ad923e7d35af332ae0a0f6540f1cf"))return;
            }
        }catch(Exception ignored){}
        throw new SecurityException("Bridge access denied");
    }
    @Override public boolean onCreate(){BridgeState.init(getContext());return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        authorize();
        if("state".equals(method)){
            if(Binder.getCallingUid()!=android.os.Process.myUid()){
                if(BridgeState.lastHook==0)BridgeState.log("OFFICIAL_RE_IPC_CONNECTED",null);
                BridgeState.lastHook=SystemClock.elapsedRealtime();
            }
            return BridgeState.snapshot();
        }
        if("command".equals(method)){
            int command=extras==null?-1:extras.getInt("command",-1);
            if(command>=0&&command<=6&&BridgeService.current!=null)BridgeService.current.command(command);
            else BridgeState.log("COMMAND_BRIDGE_NOT_READY_OR_INVALID",null);
            return Bundle.EMPTY;
        }
        if("callAction".equals(method)&&extras!=null){
            int action=extras.getInt("action",-1);String token=extras.getString("token","");
            if((action==0||action==1)&&BridgeService.current!=null)BridgeService.current.callAction(token,action);
            else BridgeState.log("CALL_ACTION_INVALID_OR_BRIDGE_STOPPED",null);
            return Bundle.EMPTY;
        }
        if("event".equals(method)&&arg!=null&&arg.matches("[A-Z0-9_]{1,80}")){BridgeState.log("DASH_"+arg,null);return Bundle.EMPTY;}
        return Bundle.EMPTY;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        authorize();if(!"r".equals(mode)||!"/art".equals(uri.getPath()))throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(),"cover.png"),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){authorize();return null;}
    @Override public String getType(Uri u){authorize();return "image/png";}
    @Override public Uri insert(Uri u,ContentValues v){authorize();throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){authorize();throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){authorize();throw new UnsupportedOperationException();}
}
