package local.re.screenoff;

/** Revalidated at enqueue and actual GATT submission to reject stale call commands. */
public final class CallActionGate {
    public static int selection(int requested,String positive,String negative){
        boolean hasPositive=positive!=null&&!positive.isEmpty(),hasNegative=negative!=null&&!negative.isEmpty();
        if(requested==0&&hasPositive)return 0;if(requested==1&&hasNegative)return 1;return hasPositive?0:1;
    }
    public static boolean allowed(int action,String expected,String actual,int category,int flags,long now,long expires,String positive,String negative){
        if(expected==null||expected.isEmpty()||!expected.equals(actual)||category!=1||now>=expires)return false;
        return action==0?(flags&8)!=0&&positive!=null&&!positive.isEmpty():action==1&&(flags&16)!=0&&negative!=null&&!negative.isEmpty();
    }
}
