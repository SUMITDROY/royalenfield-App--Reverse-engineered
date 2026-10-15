package local.re.screenoff.tests;
import android.app.Instrumentation;
import android.graphics.*;
import android.os.Bundle;
import local.re.screenoff.OverlayRenderer;

public final class OverlayDeviceTest extends Instrumentation {
    private void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();try {
            Bitmap map=Bitmap.createBitmap(526,300,Bitmap.Config.ARGB_8888);map.eraseColor(Color.BLUE);
            Bundle state=new Bundle();
            check(OverlayRenderer.render(map,state,null)==map,"Empty state must preserve original frame");
            state.putString("title","Long title 😀 with Unicode — "+new String(new char[100]).replace('\0','x'));state.putString("artist","Artist");
            Bitmap cover=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);cover.eraseColor(Color.RED);
            Bitmap rendered=OverlayRenderer.render(map,state,cover);
            check(rendered!=map&&rendered.isMutable(),"Overlay needs a separate mutable bitmap");
            check(map.getPixel(300,235)==Color.BLUE,"Original frame was modified");
            check(rendered.getPixel(10,10)==Color.BLUE,"Map outside card changed");
            check(rendered.getPixel(310,240)==Color.RED,"Cover wasn't drawn");
            state.putString("call","Caller");Bitmap call=OverlayRenderer.render(map,state,cover);
            check(call.getPixel(310,240)!=Color.RED,"Call should take precedence over cover");
            Bitmap small=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
            check(OverlayRenderer.render(small,state,null)==small,"Unexpected dimensions must fail open");
            Bitmap immutable=map.copy(Bitmap.Config.ARGB_8888,false);
            check(OverlayRenderer.render(immutable,state,null)!=immutable,"Immutable inputs must be supported");
            result.putString("stream","PASS: overlay bounds, original-frame preservation, cover rendering, Unicode text, call priority, dimension guard, immutable input\n");finish(-1,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e);finish(0,result);}
    }
}
