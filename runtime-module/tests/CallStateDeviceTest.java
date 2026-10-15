package local.re.screenoff;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.*;
import java.lang.reflect.Method;

public final class CallStateDeviceTest extends Instrumentation {
    private void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private JSONObject state(String uuid,int phase,String number)throws Exception {
        JSONArray calls=new JSONArray();if(uuid!=null)calls.put(new JSONObject().put("uuid",uuid).put("state",phase).put("number",number).put("device","00:11:22:33:44:55"));
        return new JSONObject().put("event","HFP_STATE").put("connectedDevices",1).put("calls",calls);
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();try {
        BridgeState.init(getTargetContext());BridgeState.clearCall();
        HfpController controller=new HfpController(getTargetContext());Method receive=HfpController.class.getDeclaredMethod("receive",JSONObject.class);receive.setAccessible(true);
        receive.invoke(controller,state("call-a",4,"Caller A"));
        check(BridgeState.callPhase.equals("ringing")&&BridgeState.positiveLabel.equals("Answer")&&BridgeState.negativeLabel.equals("Decline"),"Incoming controls");
        receive.invoke(controller,state("call-b",4,"Caller B"));
        check(BridgeState.call.equals("Caller B"),"A new call must not inherit the previous caller label");
        check(!controller.action("hfp:call-a",1),"Stale call must not be disconnected");
        receive.invoke(controller,state("call-b",0,"Caller B"));
        check(BridgeState.callPhase.equals("active")&&BridgeState.positiveLabel.isEmpty()&&BridgeState.negativeLabel.equals("Disconnect"),"Active call controls");
        check(!controller.action("hfp:call-b",0),"Never answer an already active call");
        receive.invoke(controller,state("call-b",1,"Caller B"));check(BridgeState.callPhase.equals("held"),"Held state");
        receive.invoke(controller,state(null,0,""));check(BridgeState.call.isEmpty()&&BridgeState.callToken.isEmpty(),"Ended call clears labels and action token");
        result.putString("stream","PASS: incoming/active/held/ended state, contextual actions, caller isolation and stale-action rejection\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e);finish(0,result);}}
}
