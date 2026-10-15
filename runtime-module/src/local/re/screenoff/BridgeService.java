package local.re.screenoff;

import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** BLE peripheral for pairing; GATT client over the same iPhone-initiated link. */
public final class BridgeService extends Service {
    static volatile BridgeService current;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private BluetoothManager manager;private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer server;private BluetoothGatt gatt;private BluetoothDevice phone;
    private MediaSession session;private ArtworkLookup artwork;
    private HfpController hfp;
    private final ArrayDeque<Op> ops=new ArrayDeque<>();private Op pending;
    private final ArrayDeque<Notice> notices=new ArrayDeque<>();private Notice awaiting;
    private final Map<Long,Notice> known=new LinkedHashMap<>();
    private final ByteArrayOutputStream data=new ByteArrayOutputStream();
    private BluetoothGattCharacteristic cp, remote, updates, attribute;
    private final Set<Integer> supported=new HashSet<>();private boolean commandListKnown;
    private long callUid=-1;private boolean stopping, registered, advertising, discovering;
    private String actionInFlight="";
    private int discoverRetries;
    private long lastMediaUpdate,lastNotification;
    private final Runnable heartbeat=new Runnable(){public void run(){
        if(stopping)return;
        android.os.PowerManager power=getSystemService(android.os.PowerManager.class);
        long now=SystemClock.elapsedRealtime();
        BridgeState.log("HEARTBEAT calls_ready="+BridgeState.callsReady+" media_ready="+BridgeState.mediaReady
            +" playing="+BridgeState.playing+" re_hook_age_ms="+(BridgeState.lastHook==0?-1:now-BridgeState.lastHook)
            +" media_event_age_ms="+(lastMediaUpdate==0?-1:now-lastMediaUpdate)
            +" notification_event_age_ms="+(lastNotification==0?-1:now-lastNotification)
            +" hfp_ready="+BridgeState.hfpReady+" hfp_age_ms="+(BridgeState.hfpLastUpdate==0?-1:now-BridgeState.hfpLastUpdate)
            +" interactive="+power.isInteractive()+" doze="+power.isDeviceIdleMode()
            +" battery_exempt="+power.isIgnoringBatteryOptimizations(getPackageName()),null);
        handler.postDelayed(this,10000);
    }};
    private static final UUID LOCAL=UUID.fromString("eae10100-238e-4a5c-9d1a-623139aeee10");
    private static final UUID PAIR=UUID.fromString("eae10101-238e-4a5c-9d1a-623139aeee10");
    private static final class Notice {long uid;int category,flags;Notice(long u,int c,int f){uid=u;category=c;flags=f;}}
    private static final class Op {
        String name;BluetoothGattCharacteristic characteristic;BluetoothGattDescriptor descriptor;byte[] bytes;
        boolean read;Runnable success;
        String callToken;int action=-1;
        Op(String n,BluetoothGattCharacteristic c,byte[] b,boolean r,Runnable done){name=n;characteristic=c;bytes=b;read=r;success=done;}
        Op(String n,BluetoothGattDescriptor d,byte[] b,Runnable done){name=n;descriptor=d;bytes=b;success=done;}
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();current=this;BridgeState.init(this);BridgeState.running=true;stopping=false;
        try {
            // URI access makes this package visible to the unmodified RE manifest on Android 11+.
            // The provider still checks the caller UID and official signing certificate on every call.
            grantUriPermission("com.royalenfield.reprime",BridgeProvider.URI,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            BridgeState.log("RE_PROVIDER_VISIBILITY_GRANTED",null);
        }catch(Exception e){BridgeState.log("RE_PROVIDER_VISIBILITY_GRANT_FAILED",e);}
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("bridge","iPhone bridge",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,BridgeActivity.class),PendingIntent.FLAG_IMMUTABLE);
        startForeground(10,new Notification.Builder(this,"bridge").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Tripper iPhone bridge").setContentText("Calls and media stay on iPhone").setContentIntent(open).setOngoing(true).build());
        session=new MediaSession(this,"Tripper iPhone");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){command(0);}@Override public void onPause(){command(1);}
            @Override public void onSkipToNext(){command(3);}@Override public void onSkipToPrevious(){command(4);}
            @Override public boolean onMediaButtonEvent(Intent i){
                android.view.KeyEvent k=i.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if(k==null||k.getAction()!=android.view.KeyEvent.ACTION_DOWN)return true;
                switch(k.getKeyCode()){
                    case android.view.KeyEvent.KEYCODE_MEDIA_PLAY:command(0);break;
                    case android.view.KeyEvent.KEYCODE_MEDIA_PAUSE:command(1);break;
                    case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:command(2);break;
                    case android.view.KeyEvent.KEYCODE_MEDIA_NEXT:command(3);break;
                    case android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS:command(4);break;
                    default:return false;
                }return true;
            }
        },handler);
        artwork=new ArtworkLookup(this,this::publishMedia);
        if(getSharedPreferences("bridge",0).getBoolean("hfpEnabled",false))enableHfp();
        handler.postDelayed(heartbeat,10000);
        try {
            if(checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=0||checkSelfPermission("android.permission.BLUETOOTH_ADVERTISE")!=0){
                BridgeState.status("BLUETOOTH_PERMISSION_MISSING");stopSelf();return;
            }
            manager=getSystemService(BluetoothManager.class);
            IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction("android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED");filter.addAction("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED");
            registerReceiver(bonds,filter,Build.VERSION.SDK_INT>=33?Context.RECEIVER_EXPORTED:0);registered=true;
            startPairingServer();
        }catch(Exception e){BridgeState.log("BRIDGE_START_FAILED",e);BridgeState.status("Bridge start failed; export logs");stopSelf();}
    }
    @Override public int onStartCommand(Intent i,int flags,int id){return START_STICKY;}
    private void startPairingServer(){
        if(server!=null||stopping)return;
        if(manager==null||manager.getAdapter()==null||!manager.getAdapter().isEnabled()){BridgeState.status("Bluetooth off — waiting for restart");return;}
        server=manager.openGattServer(this,serverCallback);
        if(server==null)throw new IllegalStateException("GATT server unavailable");
        BluetoothGattService local=new BluetoothGattService(LOCAL,BluetoothGattService.SERVICE_TYPE_PRIMARY);
        local.addCharacteristic(new BluetoothGattCharacteristic(PAIR,BluetoothGattCharacteristic.PROPERTY_READ,BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED));
        if(!server.addService(local))throw new IllegalStateException("Cannot add pairing service");
    }
    private void advertise(){
        if(stopping||advertising)return;
        try {
            BluetoothAdapter adapter=manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){BridgeState.status("BLUETOOTH_DISABLED");return;}
            advertiser=adapter.getBluetoothLeAdvertiser();
            if(advertiser==null){BridgeState.status("BLE_PERIPHERAL_UNSUPPORTED");return;}
            AdvertiseData ad=new AdvertiseData.Builder().addServiceSolicitationUuid(new ParcelUuid(AppleProtocol.ANCS)).build();
            // Do not change the phone's global Bluetooth name. Long names won't fit legacy packets.
            AdvertiseData.Builder scanBuilder=new AdvertiseData.Builder();String name=adapter.getName();
            if(name!=null&&name.getBytes(StandardCharsets.UTF_8).length<=26)scanBuilder.setIncludeDeviceName(true);
            else scanBuilder.addServiceData(new ParcelUuid(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")),"TripperBridge".getBytes(StandardCharsets.US_ASCII));
            AdvertiseData scan=scanBuilder.build();
            advertiser.startAdvertising(new AdvertiseSettings.Builder().setConnectable(true).setTimeout(0)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).build(),ad,scan,adCallback);
        }catch(Exception e){BridgeState.log("ADVERTISE_START_FAILED",e);}
    }
    private final AdvertiseCallback adCallback=new AdvertiseCallback(){
        @Override public void onStartSuccess(AdvertiseSettings s){handler.post(()->{advertising=true;BridgeState.status("Advertising — connect with LightBlue on iPhone");});}
        @Override public void onStartFailure(int code){handler.post(()->BridgeState.status("ADVERTISE_FAILED_CODE_"+code));}
    };
    private final BluetoothGattServerCallback serverCallback=new BluetoothGattServerCallback(){
        @Override public void onServiceAdded(int status,BluetoothGattService s){handler.post(()->{if(status==0)advertise();else BridgeState.status("PAIRING_SERVICE_FAILED_"+status);});}
        @Override public void onCharacteristicReadRequest(BluetoothDevice d,int id,int offset,BluetoothGattCharacteristic c){
            byte[] value="Tripper iPhone Bridge".getBytes(StandardCharsets.UTF_8);
            server.sendResponse(d,id,offset<=value.length?BluetoothGatt.GATT_SUCCESS:BluetoothGatt.GATT_INVALID_OFFSET,offset,
                offset<=value.length?Arrays.copyOfRange(value,offset,value.length):null);
        }
        @Override public void onConnectionStateChange(BluetoothDevice d,int status,int state){handler.post(()->{
            if(stopping)return;
            if(state==BluetoothProfile.STATE_CONNECTED){
                String saved=getSharedPreferences("bridge",0).getString("peer",null);
                if(saved!=null&&!saved.equals(d.getAddress())){server.cancelConnection(d);BridgeState.log("UNEXPECTED_PEER_REJECTED",null);return;}
                if(phone!=null&&!phone.equals(d)){server.cancelConnection(d);return;}
                phone=d;BridgeState.status("iPhone connected — waiting for pairing");
                try{if(d.getBondState()==BluetoothDevice.BOND_NONE&&!d.createBond())BridgeState.log("BOND_REQUEST_REJECTED",null);connectClient(d);}
                catch(Exception e){BridgeState.log("PAIRING_FAILED",e);}
            }else if(state==BluetoothProfile.STATE_DISCONNECTED&&phone!=null&&phone.equals(d)){disconnect("IPHONE_DISCONNECTED_STATUS_"+status);}
        });}
    };
    private final BroadcastReceiver bonds=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(i.getAction()!=null&&i.getAction().startsWith("android.bluetooth.headsetclient.profile.action.")){if(hfp!=null)hfp.refresh();return;}
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(i.getAction())){
                int state=i.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
                if(state==BluetoothAdapter.STATE_OFF){
                    advertising=false;advertiser=null;
                    try{if(server!=null)server.close();}catch(Exception e){BridgeState.log("GATT_SERVER_RESET_FAILED",e);}server=null;
                    disconnect("BLUETOOTH_OFF");
                }else if(state==BluetoothAdapter.STATE_ON){
                    try{startPairingServer();}catch(Exception e){BridgeState.log("GATT_RESTART_FAILED",e);}
                }return;
            }
            BluetoothDevice d=i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(phone==null||!phone.equals(d))return;
            int state=i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,-1);
            if(state==BluetoothDevice.BOND_BONDED){
                getSharedPreferences("bridge",0).edit().putString("peer",d.getAddress()).apply();
                BridgeState.log("BLE_BONDED",null);handler.postDelayed(()->discover(),1500);
            }else if(state==BluetoothDevice.BOND_NONE){
                int reason=i.getIntExtra("android.bluetooth.device.extra.REASON",-1);
                BridgeState.status("PAIRING_FAILED_REASON_"+reason+" — unlock both phones and reconnect");
            }
        }
    };
    private void connectClient(BluetoothDevice d){
        if(gatt!=null)return;
        gatt=d.connectGatt(this,false,clientCallback,BluetoothDevice.TRANSPORT_LE);
        if(gatt==null)BridgeState.status("GATT_CLIENT_UNAVAILABLE");
    }
    private void discover(){
        if(stopping||gatt==null||phone==null||phone.getBondState()!=BluetoothDevice.BOND_BONDED||pending!=null||discovering)return;
        BridgeState.log("GATT_DISCOVERY_START",null);discovering=true;
        if(!gatt.discoverServices()){discovering=false;BridgeState.log("GATT_DISCOVERY_REJECTED",null);}
    }
    private final BluetoothGattCallback clientCallback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){handler.post(()->{
            if(g!=gatt||stopping)return;
            if(state==BluetoothProfile.STATE_CONNECTED&&status==0){handler.postDelayed(()->discover(),1000);}
            else if(state==BluetoothProfile.STATE_DISCONNECTED){disconnect("GATT_DISCONNECTED_STATUS_"+status);}
        });}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){handler.post(()->{if(g==gatt){discovering=false;services(status);}});}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){handler.post(()->{if(g==gatt&&pending!=null&&pending.descriptor==d)finish(status);});}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){handler.post(()->{if(g==gatt&&pending!=null&&!pending.read&&pending.characteristic==c)finish(status);});}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status){handler.post(()->{
            if(g==gatt&&pending!=null&&pending.read&&pending.characteristic==c){
                if(status==0&&extendedEntity>=0){applyMedia(extendedEntity,extendedAttribute,new String(value,StandardCharsets.UTF_8));extendedEntity=-1;}
                finish(status);
            }
        });}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status){
            if(Build.VERSION.SDK_INT<33)onCharacteristicRead(g,c,c.getValue()==null?new byte[0]:c.getValue().clone(),status);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){
            final byte[] copy=value.clone();handler.post(()->{if(g==gatt)receive(c.getUuid(),copy);});
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){
            if(Build.VERSION.SDK_INT<33&&c.getValue()!=null)onCharacteristicChanged(g,c,c.getValue().clone());
        }
        @Override public void onServiceChanged(BluetoothGatt g){handler.post(()->{if(g==gatt){BridgeState.log("APPLE_SERVICES_CHANGED",null);resetProtocol();handler.postDelayed(()->discover(),1000);}});}
    };
    private void services(int status){
        if(status!=0){BridgeState.status("GATT_DISCOVERY_FAILED_"+status);return;}
        BluetoothGattService a=gatt.getService(AppleProtocol.ANCS),m=gatt.getService(AppleProtocol.AMS);
        BluetoothGattService generic=gatt.getService(UUID.fromString("00001801-0000-1000-8000-00805f9b34fb"));
        if(generic!=null)subscribe(generic.getCharacteristic(AppleProtocol.CHANGED),true,()->{});
        if(a!=null){
            cp=a.getCharacteristic(AppleProtocol.CP);
            BluetoothGattCharacteristic ds=a.getCharacteristic(AppleProtocol.DS),ns=a.getCharacteristic(AppleProtocol.NS);
            if(cp!=null&&ds!=null&&ns!=null){
                subscribe(ds,false,()->subscribe(ns,false,()->{BridgeState.callsReady=true;BridgeState.status("iPhone calls ready");nextNotice();}));
            }else BridgeState.log("ANCS_ATTRIBUTES_UNAVAILABLE",null);
        }else BridgeState.log("ANCS_MISSING_ENABLE_SHARE_SYSTEM_NOTIFICATIONS",null);
        if(m!=null){
            remote=m.getCharacteristic(AppleProtocol.COMMAND);updates=m.getCharacteristic(AppleProtocol.UPDATE);attribute=m.getCharacteristic(AppleProtocol.ATTRIBUTE);
            if(remote!=null&&updates!=null){
                subscribe(remote,false,()->{});
                subscribe(updates,false,()->{
                    write("AMS_REGISTER_PLAYER",updates,new byte[]{0,0,1,2},()->{});
                    write("AMS_REGISTER_TRACK",updates,new byte[]{2,0,1,2,3},()->{
                        BridgeState.mediaReady=true;BridgeState.status("iPhone media ready");publishMedia();
                        // Some iPhones retain subscriptions across a fast Android process restart
                        // and omit unchanged initial notifications. Explicitly refresh these values.
                        for(int[] pair:new int[][]{{0,0},{0,1},{2,0},{2,1},{2,2}})requestAttribute(pair[0],pair[1]);
                    });
                });
            }else BridgeState.log("AMS_CHARACTERISTICS_MISSING",null);
        }else BridgeState.log("AMS_MISSING_START_MEDIA_ON_IPHONE",null);
        if((a==null||m==null)&&discoverRetries++<3)handler.postDelayed(()->discover(),5000);
    }
    private void subscribe(BluetoothGattCharacteristic c,boolean indication,Runnable done){
        if(c==null){BridgeState.log("SUBSCRIBE_CHARACTERISTIC_MISSING",null);return;}
        BluetoothGattDescriptor d=c.getDescriptor(AppleProtocol.CCC);
        if(d==null||!gatt.setCharacteristicNotification(c,true)){BridgeState.log("SUBSCRIBE_SETUP_FAILED",null);return;}
        enqueue(new Op("SUBSCRIBE_"+c.getUuid(),d,indication?BluetoothGattDescriptor.ENABLE_INDICATION_VALUE:BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,done));
    }
    private void write(String name,BluetoothGattCharacteristic c,byte[] bytes,Runnable done){
        if(c==null){BridgeState.log(name+"_CHARACTERISTIC_MISSING",null);return;}
        enqueue(new Op(name,c,bytes,false,done));
    }
    private void enqueue(Op op){if(ops.size()>64){BridgeState.log("GATT_QUEUE_FULL",null);return;}ops.add(op);pump();}
    private void pump(){
        if(pending!=null||gatt==null||ops.isEmpty()||stopping)return;
        pending=ops.remove();final Op item=pending;boolean accepted=false;
        if(item.callToken!=null&&!validCallAction(item.callToken,item.action)){
            pending=null;BridgeState.log("STALE_QUEUED_CALL_ACTION_REJECTED",null);pump();return;
        }
        try {
            if(item.descriptor!=null){
                if(Build.VERSION.SDK_INT>=33)accepted=gatt.writeDescriptor(item.descriptor,item.bytes)==BluetoothStatusCodes.SUCCESS;
                else {item.descriptor.setValue(item.bytes);accepted=gatt.writeDescriptor(item.descriptor);}
            }
            else if(item.read)accepted=gatt.readCharacteristic(item.characteristic);
            else if(Build.VERSION.SDK_INT>=33)accepted=gatt.writeCharacteristic(item.characteristic,item.bytes,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
            else {item.characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);item.characteristic.setValue(item.bytes);accepted=gatt.writeCharacteristic(item.characteristic);}
        }catch(Exception e){BridgeState.log(item.name+"_EXCEPTION",e);}
        if(!accepted){BridgeState.log(item.name+"_REJECTED",null);pending=null;handler.postDelayed(this::pump,100);return;}
        handler.postDelayed(()->{if(pending==item){BridgeState.log(item.name+"_TIMEOUT",null);disconnect("GATT_OPERATION_TIMEOUT");}},10000);
    }
    private void finish(int status){
        Op finished=pending;if(finished==null)return;pending=null;
        if(status!=0)BridgeState.log(finished.name+"_FAILED_STATUS_"+status,null);
        else if(finished.success!=null)finished.success.run();
        pump();
    }
    private void receive(UUID uuid,byte[] b){
        try {
            if(uuid.equals(AppleProtocol.CHANGED)){resetProtocol();handler.postDelayed(()->discover(),1000);}
            else if(uuid.equals(AppleProtocol.COMMAND)){supported.clear();for(byte v:b)supported.add(v&255);commandListKnown=true;BridgeState.log("AMS_SUPPORTED_COMMANDS_UPDATED",null);publishMedia();}
            else if(uuid.equals(AppleProtocol.UPDATE)){
                if(b.length<3){BridgeState.log("AMS_SHORT_UPDATE",null);return;}
                int entity=b[0]&255,attr=b[1]&255;
                applyMedia(entity,attr,new String(b,3,b.length-3,StandardCharsets.UTF_8));
                if((b[2]&1)!=0&&attribute!=null){
                    requestAttribute(entity,attr);
                }
            }else if(uuid.equals(AppleProtocol.NS))source(b);
            else if(uuid.equals(AppleProtocol.DS))attributes(b);
        }catch(Exception e){BridgeState.log("APPLE_PACKET_REJECTED",e);}
    }
    private int extendedEntity=-1,extendedAttribute;
    private void requestAttribute(final int entity,final int attr){
        if(attribute==null)return;
        write("AMS_SELECT_ATTRIBUTE_"+entity+"_"+attr,attribute,new byte[]{(byte)entity,(byte)attr},()->{
            // Read must precede any other attribute-select writes already queued.
            ops.addFirst(new Op("AMS_READ_ATTRIBUTE_"+entity+"_"+attr,attribute,null,true,null));extendedEntity=entity;extendedAttribute=attr;
        });
    }
    private void applyMedia(int entity,int attr,String raw){
        lastMediaUpdate=SystemClock.elapsedRealtime();
        String v=AppleProtocol.clean(raw,200);
        if(entity==0){
            if(attr==0)BridgeState.player=v;
            if(attr==1){String[] parts=v.split(",");if(parts.length>0)BridgeState.playing="1".equals(parts[0]);}
        }else if(entity==2){
            if(attr==0)BridgeState.artist=v;if(attr==1)BridgeState.album=v;if(attr==2)BridgeState.title=v;
            if(attr<=2)artwork.changed();
        }
        BridgeState.changed++;publishMedia();BridgeState.log("AMS_METADATA_UPDATE_ENTITY_"+entity+"_ATTRIBUTE_"+attr,null);
    }
    private void source(byte[] b){
        lastNotification=SystemClock.elapsedRealtime();
        if(b.length!=8)throw new IllegalArgumentException("ANCS notification source must be 8 bytes");
        int event=b[0]&255,flags=b[1]&255,category=b[2]&255;long uid=AppleProtocol.uid(b,4);
        if(event==2){
            known.remove(uid);notices.removeIf(n->n.uid==uid);
            if(callUid==uid){callUid=-1;if(!BridgeState.hfpReady)BridgeState.clearCall();BridgeState.changed++;BridgeState.log("IPHONE_RINGING_ENDED_STATE_NOT_INFERRED",null);}
            return;
        }
        if(event>1||(flags&4)!=0)return; // Ignore backlog; never replay stale calls/messages.
        Notice n=new Notice(uid,category,flags);known.put(uid,n);
        if(known.size()>128){Iterator<Long> i=known.keySet().iterator();i.next();i.remove();}
        if(category==1){
            if(BridgeState.hfpReady&&BridgeState.callToken.startsWith("hfp:")){callUid=uid;BridgeState.log("ANCS_INCOMING_HFP_OWNS_CALL_STATE",null);}
            else {
            if(callUid!=uid){BridgeState.callToken=UUID.randomUUID().toString();BridgeState.positiveLabel="";BridgeState.negativeLabel="";}
            callUid=uid;BridgeState.call="Incoming iPhone call";BridgeState.callPhase="ringing";BridgeState.callUntil=SystemClock.elapsedRealtime()+90000;BridgeState.changed++;
            if((flags&8)==0)BridgeState.positiveLabel="";if((flags&16)==0)BridgeState.negativeLabel="";
            }
        }
        if(notices.size()<32){notices.add(n);nextNotice();}else BridgeState.log("ANCS_NOTICE_QUEUE_FULL",null);
        BridgeState.log("ANCS_EVENT_"+event+"_CATEGORY_"+category,null);
    }
    private void nextNotice(){
        if(awaiting!=null||notices.isEmpty()||cp==null||!BridgeState.callsReady)return;
        awaiting=notices.remove();data.reset();final Notice n=awaiting;
        write("ANCS_GET_ATTRIBUTES",cp,AppleProtocol.attributes(n.uid,n.category==1?n.flags:0),()->{});
        handler.postDelayed(()->{if(awaiting==n){BridgeState.log("ANCS_ATTRIBUTE_RESPONSE_TIMEOUT",null);awaiting=null;data.reset();nextNotice();}},8000);
    }
    private void attributes(byte[] bytes){
        if(awaiting==null){BridgeState.log("ANCS_UNSOLICITED_DATA_IGNORED",null);return;}
        if(data.size()+bytes.length>2048){data.reset();awaiting=null;BridgeState.log("ANCS_RESPONSE_OVERSIZE",null);nextNotice();return;}
        data.write(bytes,0,bytes.length);
        AppleProtocol.Attributes parsed=AppleProtocol.parseAttributes(data.toByteArray(),AppleProtocol.attributeMask(awaiting.category==1?awaiting.flags:0));if(parsed==null)return;
        Notice notice=awaiting;awaiting=null;data.reset();
        if(parsed.uid!=notice.uid){BridgeState.log("ANCS_RESPONSE_UID_MISMATCH",null);nextNotice();return;}
        if(known.containsKey(parsed.uid)){
            String title=AppleProtocol.clean(parsed.values.get(1),96);
            if(notice.category==1&&callUid==parsed.uid){
                if(BridgeState.hfpReady&&BridgeState.callToken.startsWith("hfp:")){
                    if(BridgeState.hfpCallCount==1&&BridgeState.callPhase.equals("ringing")&&!title.isEmpty()){BridgeState.call=title;BridgeState.log("IPHONE_LABEL_FOR_SINGLE_RINGING_HFP_CALL",null);}
                    nextNotice();return;
                }
                if(BridgeState.hfpReady&&BridgeState.callToken.isEmpty()){nextNotice();return;}
                BridgeState.call=title.isEmpty()?"Incoming iPhone call":title;
                Notice latest=known.get(parsed.uid);
                BridgeState.positiveLabel=(latest.flags&8)!=0?AppleProtocol.clean(parsed.values.get(6),32):"";
                BridgeState.negativeLabel=(latest.flags&16)!=0?AppleProtocol.clean(parsed.values.get(7),32):"";
                BridgeState.log("IPHONE_CALL_ACTIONS positive="+!BridgeState.positiveLabel.isEmpty()+" negative="+!BridgeState.negativeLabel.isEmpty(),null);
            }
            else if(notice.category!=1){
                String app=AppleProtocol.clean(parsed.values.get(0),80);
                BridgeState.alert=(app.isEmpty()?"iPhone":app)+": "+title;
                BridgeState.alertUntil=SystemClock.elapsedRealtime()+12000;BridgeState.log("IPHONE_NOTIFICATION_RECEIVED",null);
            }BridgeState.changed++;
        }nextNotice();
    }
    void command(final int id){handler.post(()->{
        if(!BridgeState.mediaReady||remote==null){BridgeState.log("MEDIA_COMMAND_NOT_CONNECTED",null);return;}
        if(commandListKnown&&!supported.contains(id)){BridgeState.log("MEDIA_COMMAND_UNSUPPORTED_"+id,null);return;}
        write("AMS_COMMAND_"+id,remote,new byte[]{(byte)id},()->BridgeState.log("AMS_COMMAND_FORWARDED_"+id+"_PLAYER_ACTION_UNCONFIRMED",null));
    });}
    void artworkPreferenceChanged(){if(artwork!=null)artwork.preferenceChanged();}
    void refreshIphone(){handler.post(()->{if(gatt==null){BridgeState.log("IPHONE_NOT_CONNECTED_FOR_REFRESH",null);return;}resetProtocol();discoverRetries=0;discover();});}
    private boolean validCallAction(String token,int action){
        Notice notice=known.get(callUid);
        return BridgeState.callsReady&&cp!=null&&notice!=null&&CallActionGate.allowed(action,token,BridgeState.callToken,
            notice.category,notice.flags,SystemClock.elapsedRealtime(),BridgeState.callUntil,BridgeState.positiveLabel,BridgeState.negativeLabel);
    }
    void callAction(final String token,final int action){handler.post(()->{
        if(BridgeState.hfpReady&&hfp!=null&&hfp.action(token,action))return;
        if(!validCallAction(token,action)){BridgeState.log("CALL_ACTION_UNAVAILABLE_OR_STALE",null);return;}
        if(token.equals(actionInFlight)){BridgeState.log("DUPLICATE_CALL_ACTION_IGNORED",null);return;}
        actionInFlight=token;handler.postDelayed(()->{if(token.equals(actionInFlight))actionInFlight="";},3000);
        Op op=new Op("ANCS_CALL_ACTION_"+action,cp,AppleProtocol.action(callUid,action),false,
            ()->BridgeState.log("ANCS_CALL_ACTION_"+action+"_FORWARDED_RESULT_UNCONFIRMED",null));
        op.callToken=token;op.action=action;enqueue(op);
    });}
    void enableHfp(){if(hfp==null)hfp=new HfpController(this);hfp.start();}
    void connectHfp(String address){enableHfp();hfp.connect(address);}
    void disableHfp(){if(hfp!=null){hfp.stop();hfp=null;}BridgeState.hfpStatus="Stopped";}
    void publishMedia(){
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(this::publishMedia);return;}
        if(session==null)return;
        session.setActive(BridgeState.mediaReady&&!BridgeState.title.isEmpty());
        MediaMetadata.Builder metadata=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,BridgeState.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST,BridgeState.artist).putString(MediaMetadata.METADATA_KEY_ALBUM,BridgeState.album)
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,BridgeState.title+"/"+BridgeState.artVersion);
        android.graphics.Bitmap cover=artwork==null?null:artwork.bitmap();
        if(cover!=null){metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,cover);metadata.putBitmap(MediaMetadata.METADATA_KEY_ART,cover);metadata.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON,cover);}
        session.setMetadata(metadata.build());
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(BridgeState.playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,PlaybackState.PLAYBACK_POSITION_UNKNOWN,BridgeState.playing?1:0).build());
    }
    private void resetProtocol(){
        ops.clear();pending=null;notices.clear();awaiting=null;data.reset();known.clear();supported.clear();commandListKnown=false;
        cp=null;remote=null;updates=null;attribute=null;extendedEntity=-1;discovering=false;BridgeState.disconnected();publishMedia();
    }
    private void disconnect(String reason){
        if(gatt!=null){BluetoothGatt old=gatt;gatt=null;try{old.disconnect();old.close();}catch(Exception e){BridgeState.log("GATT_DISCONNECT_FAILED",e);}}
        phone=null;resetProtocol();discoverRetries=0;artwork.changed();BridgeState.status(reason+" — waiting for iPhone");
        if(!stopping)advertise();
    }
    @Override public void onDestroy(){
        disableHfp();
        stopping=true;current=null;BridgeState.running=false;handler.removeCallbacksAndMessages(null);
        try{if(registered)unregisterReceiver(bonds);if(advertiser!=null)advertiser.stopAdvertising(adCallback);if(server!=null)server.close();if(gatt!=null){gatt.disconnect();gatt.close();}}catch(Exception e){BridgeState.log("BRIDGE_STOP_FAILED",e);}
        BridgeState.disconnected();if(artwork!=null)artwork.close();if(session!=null){session.setActive(false);session.release();session=null;}
        BridgeState.status("Stopped");super.onDestroy();
    }
}
