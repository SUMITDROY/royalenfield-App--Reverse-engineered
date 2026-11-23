package local.re.screenoff;
import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import org.json.*;

/** Location recording starts only from the visible app with runtime location consent. */
public final class TripRecorderService extends Service implements LocationListener {
    static volatile TripRecorderService current;
    private RideStore store;private LocationManager locations;private Location last;private long ride,started;private double meters;private int interruptions;private boolean previousDash;private FleetClient fleet;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable check=new Runnable(){public void run(){
        boolean connected=BridgeState.dashConnected&&SystemClock.elapsedRealtime()-BridgeState.dashUpdated<10000;
        if(previousDash&&!connected){interruptions++;park();}previousDash=connected;
        store.checkpoint(ride,meters,interruptions);
        handler.postDelayed(this,5000);
    }};
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();store=new RideStore(this);locations=getSystemService(LocationManager.class);fleet=new FleetClient(this);current=this;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("ride","Trip recording",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(ride!=0)return START_NOT_STICKY;
        if(checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")!=0&&checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION")!=0){stopSelf();return START_NOT_STICKY;}
        try{
            PendingIntent open=PendingIntent.getActivity(this,70,new Intent(this,RideActivity.class),PendingIntent.FLAG_IMMUTABLE);
            startForeground(70,new Notification.Builder(this,"ride").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Recording ride location").setContentText("Stop recording in Ride tools").setOngoing(true).setContentIntent(open).build(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            ride=store.begin();started=SystemClock.elapsedRealtime();previousDash=BridgeState.dashConnected;
            int subscriptions=0;
            for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})try{if(locations.isProviderEnabled(provider)){locations.requestLocationUpdates(provider,5000,5,this);subscriptions++;}}catch(SecurityException e){BridgeState.log("TRIP_PROVIDER_PERMISSION_UNAVAILABLE",null);}
            if(subscriptions==0)throw new IllegalStateException("No permitted location provider");
            handler.post(check);BridgeState.log("TRIP_RECORDING_STARTED",null);
        }catch(Exception e){BridgeState.log("TRIP_RECORDING_START_FAILED",e);stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onLocationChanged(Location l){
        if(ride==0||!l.hasAccuracy()||l.getAccuracy()>75||System.currentTimeMillis()-l.getTime()>30000)return;
        if(last!=null){long dt=l.getElapsedRealtimeNanos()-last.getElapsedRealtimeNanos();if(dt<=0)return;float delta=last.distanceTo(l);if(delta/(dt/1e9)>75)return;if(delta<Math.max(5,Math.min(l.getAccuracy(),last.getAccuracy())/2))return;meters+=delta;}
        store.point(ride,l);last=new Location(l);fleet.location(l,ride);
    }
    private void park(){if(last==null||SystemClock.elapsedRealtimeNanos()-last.getElapsedRealtimeNanos()>120000000000L)return;try{getSharedPreferences("bridge",0).edit().putString("parked",new JSONObject().put("lat",last.getLatitude()).put("lon",last.getLongitude()).put("accuracy",last.getAccuracy()).put("time",last.getTime()).toString()).apply();}catch(Exception ignored){}}
    String summary(){return (last==null?"Waiting for a precise location fix • ":"")+String.format(java.util.Locale.US,"Recording: %.2f km • %d min • %d Dash-link interruptions",meters/1000,(SystemClock.elapsedRealtime()-started)/60000,interruptions);}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);try{locations.removeUpdates(this);}catch(Exception ignored){}if(ride!=0){park();store.finish(ride,meters,interruptions);}current=null;fleet.close();store.close();BridgeState.log("TRIP_RECORDING_STOPPED",null);super.onDestroy();}
}
