package local.re.screenoff;
/** Explicit version mappings, never heuristic runtime guessing. */
final class HookTargets {
    static long version=193;
    static String encoder(){return version==195?"bluconnect.cmc":"bluconnect.gub";}
    static String wrapper(){return version==195?"bluconnect.zfg":"bluconnect.w9f";}
    static String manager(){return version==195?"bluconnect.ppg":"bluconnect.zif";}
    static String singleton(){return version==195?"A":"y";}
    static String device(){return version==195?"p":"o";}
    static String dash(){return version==195?"e3":"o2";}
    static String transport(){return version==195?"N":"L";}
    static String calls(){return version==195?"bluconnect.oa2":"bluconnect.r42";}
    static String media(){return version==195?"bluconnect.tpg":"bluconnect.djf";}
    static String[] buttons(){return version==195?new String[]{"U","y","r","e0","Z"}:new String[]{"H","s","n","N","M"};}
}
