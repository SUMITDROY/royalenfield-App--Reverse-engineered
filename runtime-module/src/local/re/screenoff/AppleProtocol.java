package local.re.screenoff;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Apple ANCS/AMS wire formats; independent of Android for protocol tests. */
public final class AppleProtocol {
    public static final UUID ANCS=UUID.fromString("7905f431-b5ce-4e99-a40f-4b1e122d00d0");
    public static final UUID NS=UUID.fromString("9fbf120d-6301-42d9-8c58-25e699a21dbd");
    public static final UUID CP=UUID.fromString("69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9");
    public static final UUID DS=UUID.fromString("22eac6e9-24d6-4bb5-be44-b36ace7c7bfb");
    public static final UUID AMS=UUID.fromString("89d3502b-0f36-433a-8ef4-c502ad55f8dc");
    public static final UUID COMMAND=UUID.fromString("9b3c81d8-57b1-4a8a-b8df-0e56f7ca51c2");
    public static final UUID UPDATE=UUID.fromString("2f7cabce-808d-411f-9a0c-bb92ba96c102");
    public static final UUID ATTRIBUTE=UUID.fromString("c6b2f38c-23ab-46d8-a6ab-a3a870bbd5d7");
    public static final UUID CCC=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID CHANGED=UUID.fromString("00002a05-0000-1000-8000-00805f9b34fb");
    public static long uid(byte[] b,int p) {
        if(p<0 || b.length<p+4)throw new IllegalArgumentException("short UID");
        long v=0;for(int i=0;i<4;i++)v|=(long)(b[p+i]&255)<<(i*8);return v;
    }
    public static byte[] attributes(long uid) {
        return attributes(uid,0);
    }
    public static int attributeMask(int flags){return (1<<0)|(1<<1)|(1<<3)|((flags&8)!=0?1<<6:0)|((flags&16)!=0?1<<7:0);}
    public static byte[] action(long uid,int action){
        if(uid<0||uid>0xffffffffL||(action!=0&&action!=1))throw new IllegalArgumentException("Invalid notification action");
        return new byte[]{2,(byte)uid,(byte)(uid>>8),(byte)(uid>>16),(byte)(uid>>24),(byte)action};
    }
    public static byte[] attributes(long uid,int flags) {
        ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(0);
        for(int i=0;i<4;i++)b.write((int)(uid>>(i*8))&255);
        // App identifier, title and short message. No contact database or call log.
        b.write(0);b.write(1);b.write(96);b.write(0);b.write(3);b.write(160);b.write(0);
        if((flags&8)!=0)b.write(6);if((flags&16)!=0)b.write(7);
        return b.toByteArray();
    }
    public static final class Attributes {
        public final long uid;public final Map<Integer,String> values;
        Attributes(long id,Map<Integer,String> v){uid=id;values=v;}
    }
    /** Returns null until the whole response arrives; rejects unbounded input. */
    public static Attributes parseAttributes(byte[] b) {
        return parseAttributes(b,attributeMask(0));
    }
    public static Attributes parseAttributes(byte[] b,int requestedMask) {
        if(b.length>2048)throw new IllegalArgumentException("ANCS response too large");
        if(b.length<5)return null;
        if(b[0]!=0)throw new IllegalArgumentException("Unexpected ANCS response command");
        Map<Integer,String> values=new LinkedHashMap<>();int p=5;
        while(p<b.length){
            if(b.length-p<3)return null;
            int id=b[p++]&255;int n=(b[p++]&255)|((b[p++]&255)<<8);
            if(n>1024)throw new IllegalArgumentException("ANCS attribute too large");
            if(b.length-p<n)return null;
            if(id>7||(requestedMask&(1<<id))==0)throw new IllegalArgumentException("Unrequested ANCS attribute");
            if(values.containsKey(id))throw new IllegalArgumentException("Duplicate ANCS attribute");
            values.put(id,new String(b,p,n,StandardCharsets.UTF_8));p+=n;
        }
        return values.size()==Integer.bitCount(requestedMask)?new Attributes(uid(b,1),values):null;
    }
    public static String clean(String s,int limit) {
        if(s==null)return "";
        String cleaned=s.replaceAll("[\\p{Cntrl}]"," ").trim();
        return cleaned.substring(0,cleaned.offsetByCodePoints(0,Math.min(cleaned.codePointCount(0,cleaned.length()),Math.max(0,limit))));
    }
}
