package local.re.screenoff;

import android.app.Activity;
import android.bluetooth.BluetoothManager;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.io.*;

/** Small setup screen; bridge runs as a connected-device foreground service. */
public final class BridgeActivity extends Activity {
    private TextView status;private Button positive,negative;private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        Bundle b=BridgeState.snapshot();
        String name="Android phone";try{name=getSystemService(BluetoothManager.class).getAdapter().getName();}catch(Exception ignored){}
        status.setText("Find in LightBlue: "+name+"\n"+b.getString("status")+"\nCalls: "+(b.getBoolean("callsReady")?"ready":"waiting")
            +" • Media: "+(b.getBoolean("mediaReady")?"ready":"waiting")+"\nDash hook: "+(b.getBoolean("hookAlive")?"connected":"open/restart Royal Enfield")
            +"\nCalls channel: "+b.getString("hfpStatus")+"\nCall: "+b.getString("callPhase")+" "+b.getString("call")
            +"\n"+b.getString("player")+"\n"+b.getString("title")+"\n"+b.getString("artist"));
        positive.setText(b.getString("positiveLabel","").isEmpty()?"No positive call action":b.getString("positiveLabel"));positive.setEnabled(!b.getString("positiveLabel","").isEmpty());
        negative.setText(b.getString("negativeLabel","").isEmpty()?"No negative call action":b.getString("negativeLabel"));negative.setEnabled(!b.getString("negativeLabel","").isEmpty());handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);BridgeState.init(this);
        ScrollView scroll=new ScrollView(this);LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(24,32,24,32);scroll.addView(layout);setContentView(scroll);
        TextView title=new TextView(this);title.setText("Tripper iPhone Bridge");title.setTextSize(25);layout.addView(title);
        status=new TextView(this);status.setTextSize(16);status.setPadding(0,20,0,20);layout.addView(status);
        TextView help=new TextView(this);help.setText("Android stays connected to the Dash for navigation. iPhone provides calls and media.\n\n1. Tap Start.\n2. Use Tripper Pair on iPhone (or LightBlue) to connect to this Android and accept pairing / notification sharing.\n3. Finish pairing and release the app connection. Start music on iPhone.\n4. Open Royal Enfield. Notification access must be enabled.\n\nIncoming-call actions appear only when iPhone reports them. With a call card visible, media Next/Previous selects an action; Play/Pause performs it. Actual Dash routing must be tested. The buttons below provide the same actions.\n\nConnect the root iPhone call channel for active-call details and Disconnect. Grant KernelSU root to this bridge when prompted. First activation may restart Bluetooth. The compatibility fix must be enabled for Bluetooth in Vector. Audio routing to Android is disabled once connected; verify headset audio during testing. A ringing alert disappearing alone is not interpreted as an answered call.\n\nThis build does not stream iPhone video.");layout.addView(help);
        button(layout,"Start bridge",v->start());button(layout,"Stop bridge",v->{getSharedPreferences("bridge",0).edit().putBoolean("enabled",false).apply();stopService(new Intent(this,BridgeService.class));});
        button(layout,"Refresh iPhone call/music services",v->{if(BridgeService.current!=null)BridgeService.current.refreshIphone();});
        positive=new Button(this);negative=new Button(this);layout.addView(positive);layout.addView(negative);
        positive.setOnClickListener(v->act(0));negative.setOnClickListener(v->act(1));
        button(layout,"Pair iPhone for calls",v->startActivity(new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120)));
        button(layout,"Connect iPhone calls (KernelSU root)",v->{
            if(BridgeService.current==null){Toast.makeText(this,"Start the bridge first",Toast.LENGTH_SHORT).show();return;}
            try {
                java.util.List<android.bluetooth.BluetoothDevice> devices=new java.util.ArrayList<>(getSystemService(BluetoothManager.class).getAdapter().getBondedDevices());
                devices.removeIf(d->d.getType()==android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE);
                if(devices.isEmpty()){Toast.makeText(this,"Use Pair iPhone for calls, then pair from iPhone Bluetooth Settings",Toast.LENGTH_LONG).show();return;}
                String[] names=new String[devices.size()];for(int i=0;i<names.length;i++)names[i]=devices.get(i).getName()==null?"Bluetooth device":devices.get(i).getName();
                new android.app.AlertDialog.Builder(this).setTitle("Select your iPhone for calls").setItems(names,(dialog,index)->{
                    String address=devices.get(index).getAddress();getSharedPreferences("bridge",0).edit().putString("hfpPeer",address).putBoolean("hfpEnabled",true).apply();BridgeService.current.connectHfp(address);
                }).show();
            }catch(Exception e){BridgeState.log("HFP_DEVICE_PICKER_FAILED",e);}
        });
        button(layout,"Stop iPhone call channel",v->{getSharedPreferences("bridge",0).edit().putBoolean("hfpEnabled",false).apply();if(BridgeService.current!=null)BridgeService.current.disableHfp();});
        CheckBox art=new CheckBox(this);art.setText("Look up cover art online (sends track title/artist to Apple)");art.setChecked(getSharedPreferences("bridge",0).getBoolean("artwork",false));layout.addView(art);
        art.setOnCheckedChangeListener((v,on)->{getSharedPreferences("bridge",0).edit().putBoolean("artwork",on).apply();if(BridgeService.current!=null)BridgeService.current.artworkPreferenceChanged();});
        CheckBox overlay=new CheckBox(this);overlay.setText("Show media/call overlay on navigation");overlay.setChecked(getSharedPreferences("bridge",0).getBoolean("overlay",true));layout.addView(overlay);
        overlay.setOnCheckedChangeListener((v,on)->getSharedPreferences("bridge",0).edit().putBoolean("overlay",on).apply());
        button(layout,"Royal Enfield notification access",v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button(layout,"Bridge battery settings",v->startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        button(layout,"Export bridge logs",v->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"tripper-iphone-bridge.log");startActivityForResult(i,44);});
        button(layout,"Reset remembered iPhone",v->{stopService(new Intent(this,BridgeService.class));getSharedPreferences("bridge",0).edit().remove("peer").putBoolean("enabled",false).apply();BridgeState.log("REMEMBERED_PEER_RESET",null);});
    }
    private void button(LinearLayout l,String text,View.OnClickListener click){Button b=new Button(this);b.setText(text);b.setOnClickListener(click);l.addView(b);}
    private void act(int action){Bundle b=BridgeState.snapshot();if(BridgeService.current!=null)BridgeService.current.callAction(b.getString("callToken",""),action);}
    private void start(){
        if(checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=0||checkSelfPermission("android.permission.BLUETOOTH_ADVERTISE")!=0||(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)){
            requestPermissions(Build.VERSION.SDK_INT>=33?new String[]{"android.permission.BLUETOOTH_CONNECT","android.permission.BLUETOOTH_ADVERTISE","android.permission.POST_NOTIFICATIONS"}:new String[]{"android.permission.BLUETOOTH_CONNECT","android.permission.BLUETOOTH_ADVERTISE"},42);return;
        }
        try{getSharedPreferences("bridge",0).edit().putBoolean("enabled",true).apply();startForegroundService(new Intent(this,BridgeService.class));}catch(Exception e){BridgeState.log("START_REQUEST_FAILED",e);}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==42&&checkSelfPermission("android.permission.BLUETOOTH_CONNECT")==0&&checkSelfPermission("android.permission.BLUETOOTH_ADVERTISE")==0){
            try{getSharedPreferences("bridge",0).edit().putBoolean("enabled",true).apply();startForegroundService(new Intent(this,BridgeService.class));}catch(Exception e){BridgeState.log("START_REQUEST_FAILED",e);}
        }
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=44||result!=RESULT_OK||data==null||data.getData()==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){
            for(String name:new String[]{"iphone-bridge.previous.log","iphone-bridge.log"}){
                File f=new File(getFilesDir(),name);if(!f.exists())continue;
                try(InputStream in=new FileInputStream(f)){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
            }Toast.makeText(this,"Logs saved",Toast.LENGTH_SHORT).show();
        }catch(Exception e){BridgeState.log("LOG_EXPORT_FAILED",e);Toast.makeText(this,"Log export failed",Toast.LENGTH_LONG).show();}
    }
}
