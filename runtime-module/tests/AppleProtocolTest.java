package local.re.screenoff;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class AppleProtocolTest {
    static void check(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    static void field(ByteArrayOutputStream b,int id,String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);b.write(id);b.write(bytes.length&255);b.write(bytes.length>>8);b.write(bytes,0,bytes.length);}
    static void rejected(byte[] data){try{AppleProtocol.parseAttributes(data);throw new AssertionError("Malformed input accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        check(AppleProtocol.uid(new byte[]{-1,-1,-1,-1},0)==4294967295L,"Unsigned UID");
        check(Arrays.equals(AppleProtocol.attributes(0x12345678L),new byte[]{0,0x78,0x56,0x34,0x12,0,1,96,0,3,(byte)160,0}),"Request byte order and attribute limits");
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(0);out.write(0x78);out.write(0x56);out.write(0x34);out.write(0x12);
        field(out,0,"com.apple.mobilephone");field(out,1,"Caller 😀");field(out,3,"Incoming call");byte[] response=out.toByteArray();
        for(int end=0;end<response.length;end++)check(AppleProtocol.parseAttributes(Arrays.copyOf(response,end))==null,"Fragment boundary "+end);
        AppleProtocol.Attributes parsed=AppleProtocol.parseAttributes(response);
        check(parsed.uid==0x12345678L&&parsed.values.get(1).equals("Caller 😀"),"UTF-8 fragmented response");
        rejected(new byte[]{1,0,0,0,0});rejected(new byte[]{0,0,0,0,0,1,1,5});rejected(new byte[2049]);
        ByteArrayOutputStream dup=new ByteArrayOutputStream();dup.write(0);for(int i=0;i<4;i++)dup.write(0);field(dup,1,"");field(dup,1,"");rejected(dup.toByteArray());
        check(AppleProtocol.clean(" hi\nthere ",20).equals("hi there"),"Control characters");
        check(AppleProtocol.clean(null,20).equals(""),"Null attribute");
        check(AppleProtocol.clean("😀z",1).equals("😀"),"Never split a Unicode surrogate pair");
        check(Arrays.equals(AppleProtocol.action(0x12345678L,0),new byte[]{2,0x78,0x56,0x34,0x12,0}),"Positive action encoding");
        check(Arrays.equals(AppleProtocol.action(0xffffffffL,1),new byte[]{2,-1,-1,-1,-1,1}),"Negative action unsigned UID");
        try{AppleProtocol.action(1,2);throw new AssertionError("Invalid action accepted");}catch(IllegalArgumentException expected){}
        ByteArrayOutputStream actions=new ByteArrayOutputStream();actions.write(response,0,response.length);
        field(actions,6,"Answer");field(actions,7,"Decline");byte[] actionResponse=actions.toByteArray();
        for(int end=0;end<actionResponse.length;end++)check(AppleProtocol.parseAttributes(Arrays.copyOf(actionResponse,end),AppleProtocol.attributeMask(24))==null,"Action fragment boundary "+end);
        check(AppleProtocol.parseAttributes(actionResponse,AppleProtocol.attributeMask(24)).values.get(7).equals("Decline"),"Actual action labels");
        rejected(actionResponse); // Labels that weren't requested are never accepted silently.
        check(CallActionGate.allowed(0,"current","current",1,24,100,200,"Answer","Decline"),"Supported incoming action");
        check(!CallActionGate.allowed(0,"old","current",1,24,100,200,"Answer","Decline"),"Reject stale call token");
        check(!CallActionGate.allowed(1,"current","current",1,8,100,200,"Answer","Decline"),"Reject action missing flag");
        check(!CallActionGate.allowed(0,"current","current",1,24,200,200,"Answer","Decline"),"Reject expired call");
        check(!CallActionGate.allowed(0,"current","current",1,24,100,200,"","Decline"),"Reject unlabelled action");
        check(!CallActionGate.allowed(0,"current","current",6,24,100,200,"Answer","Decline"),"Never execute a non-call notification action");
        check(!CallActionGate.allowed(2,"current","current",1,24,100,200,"Answer","Decline"),"Reject invalid action number");
        check(CallActionGate.selection(0,"","Disconnect")==1,"Answer selection must become Disconnect after connection");
        check(CallActionGate.selection(1,"Answer","Decline")==1,"Preserve a valid decline selection");
        System.out.println("PASS: unsigned IDs, command encoding, all fragmentation boundaries, UTF-8, malformed/oversize/duplicate responses, text cleaning");
    }
}
