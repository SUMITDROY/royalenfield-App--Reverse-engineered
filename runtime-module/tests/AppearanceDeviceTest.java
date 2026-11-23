package local.re.screenoff;
import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.*;

public final class AppearanceDeviceTest extends Instrumentation {
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        android.content.Context c=getTargetContext();android.content.SharedPreferences p=DashboardAppearance.prefs(c);p.edit().clear().commit();
        check(DashboardAppearance.reading(c).equals("—"),"Unset odometer must not invent mileage");
        p.edit().putString("odometer","1609.344").putString("odoUnit","mi").commit();check(DashboardAppearance.reading(c).equals("1,000.0"),"Kilometres to miles display conversion");check(p.getString("odometer","").equals("1609.344"),"Changing display units must not mutate stored km");
        for(String invalid:new String[]{"NaN","Infinity","-1","not a number"}){p.edit().putString("odometer",invalid).commit();check(DashboardAppearance.reading(c).equals("—"),"Reject invalid odometer values");}
        p.edit().putString("odometer","12345.6").putString("odoUnit","km").commit();
        for(String style:new String[]{"digital","arc","minimal"}){p.edit().putString("odoStyle",style).commit();DashboardAppearance.Odometer v=new DashboardAppearance.Odometer(c);v.layout(0,0,400,240);Bitmap bitmap=Bitmap.createBitmap(400,240,Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));check(bitmap.getPixel(200,40)!=0,"Odometer card renders "+style);bitmap.recycle();}
        for(String preset:new String[]{"grid","ember","plain","photo"}){p.edit().putString("wallpaper",preset).commit();DashboardAppearance.Wallpaper wallpaper=new DashboardAppearance.Wallpaper(c);wallpaper.setBounds(0,0,320,640);Bitmap bitmap=Bitmap.createBitmap(320,640,Bitmap.Config.ARGB_8888);wallpaper.draw(new Canvas(bitmap));check(Color.alpha(bitmap.getPixel(10,10))==255,"Background/fallback covers screen "+preset);bitmap.recycle();}
        result.putString("stream","PASS: unset/invalid manual odometer, miles conversion without data mutation, three designs and wallpaper presets/fallback\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e);finish(0,result);}}
}
