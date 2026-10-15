package local.re.screenoff;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Timer;
import java.util.TimerTask;

/** Local diagnostics only. No network upload, location coordinates or route names. */
public final class Diagnostics {
    private static Context context;
    private static File file;
    private static Timer timer;
    private static boolean installedHandler;
    private static long started, snapshots, frames, lastSnapshot, lastFrame;
    private static long signatureChanges, signature, lastSignatureChange, lastSample;
    private static boolean haveSignature, active;
    private static final String TAG = "REScreenOff";

    public static synchronized void init(Context supplied) {
        if (supplied == null || context != null) return;
        try {
            context = supplied.getApplicationContext();
            File directory = context.getExternalFilesDir("screenoff");
            if (directory == null) directory = new File(context.getFilesDir(), "screenoff");
            if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException("Cannot create diagnostics directory");
            file = new File(directory, "navigation.log");
            write("SESSION_INIT", "build=screenoff-logs-v1 api=" + Build.VERSION.SDK_INT
                    + " manufacturer=" + Build.MANUFACTURER + " model=" + Build.MODEL + " " + state());
            if (!installedHandler) {
                final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override public void uncaughtException(Thread thread, Throwable error) {
                        Diagnostics.error("UNCAUGHT_EXCEPTION thread=" + thread.getName(), error);
                        if (previous != null) previous.uncaughtException(thread, error);
                        else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
                    }
                });
                installedHandler = true;
            }
        } catch (Throwable error) { Log.e(TAG, "LOG_INIT_FAILED", error); }
    }

    public static synchronized void begin() {
        if (timer != null) timer.cancel();
        active = true;
        started = SystemClock.elapsedRealtime();
        snapshots = frames = signatureChanges = lastSnapshot = lastFrame = 0;
        lastSignatureChange = lastSample = 0;
        haveSignature = false;
        write("ENCODER_STARTED", state());
        timer = new Timer("RE-screenoff-diagnostics", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { heartbeat(); }
        }, 10000L, 10000L);
    }

    public static synchronized void end() {
        active = false;
        if (timer != null) { timer.cancel(); timer = null; }
        write("ENCODER_STOP_REQUESTED", counts());
    }

    public static synchronized void decision(Context supplied, String reason) {
        init(supplied);
        write("LIFECYCLE_DECISION", "reason=" + reason + " " + counts() + " " + state());
    }

    public static synchronized void event(Context supplied, String stage) {
        init(supplied);
        write("LIFECYCLE", "stage=" + stage + " " + counts() + " " + state());
    }

    public static synchronized void snapshot(Bitmap bitmap) {
        if (bitmap == null) { write("SNAPSHOT_NULL", "Google map callback returned null; cause unknown"); return; }
        snapshots++;
        lastSnapshot = SystemClock.elapsedRealtime();
        // Sample at most once per second, avoiding full-image copies and allocations.
        if (lastSnapshot - lastSample < 1000L) return;
        lastSample = lastSnapshot;
        try {
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            long value = width * 31L + height;
            for (int y = 1; y <= 4; y++) for (int x = 1; x <= 4; x++)
                value = value * 31L + bitmap.getPixel(Math.min(width - 1, x * width / 5), Math.min(height - 1, y * height / 5));
            if (!haveSignature || value != signature) { signatureChanges++; lastSignatureChange = lastSnapshot; }
            signature = value;
            haveSignature = true;
        } catch (Throwable error) { error("SNAPSHOT_SAMPLE_FAILED", error); }
    }

    public static synchronized void frameSubmitted() {
        frames++;
        lastFrame = SystemClock.elapsedRealtime();
    }

    public static synchronized void error(String stage, Throwable error) {
        try {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            write("ERROR", "stage=" + stage + " " + state() + "\n" + trace);
        } catch (Throwable ignored) { Log.e(TAG, stage, error); }
    }

    private static synchronized void heartbeat() {
        try {
            if (!active) return;
            long now = SystemClock.elapsedRealtime();
            String diagnosis = "PIPELINE_PROGRESS";
            if (now - (lastSnapshot == 0 ? started : lastSnapshot) > 20000L) diagnosis = "NO_RECENT_MAP_SNAPSHOT";
            else if (now - (lastFrame == 0 ? started : lastFrame) > 20000L) diagnosis = "NO_RECENT_ENCODER_SUBMISSION";
            else if (haveSignature && now - lastSignatureChange > 30000L) diagnosis = "SAMPLED_IMAGE_UNCHANGED_STATIONARY_OR_FROZEN";
            write("HEARTBEAT", "observation=" + diagnosis + " " + counts() + " " + state()
                    + " submission_is_not_dash_delivery=true");
        } catch (Throwable error) { Log.e(TAG, "HEARTBEAT_FAILED", error); }
    }

    private static String counts() {
        long now = SystemClock.elapsedRealtime();
        return "active=" + active + " snapshots=" + snapshots + " encoder_submissions=" + frames
                + " sampled_image_changes=" + signatureChanges
                + " snapshot_age_ms=" + (lastSnapshot == 0 ? -1 : now - lastSnapshot)
                + " submission_age_ms=" + (lastFrame == 0 ? -1 : now - lastFrame);
    }

    private static String state() {
        try {
            if (context == null) return "context=unavailable";
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return "interactive=" + power.isInteractive() + " doze=" + power.isDeviceIdleMode()
                    + " power_save=" + power.isPowerSaveMode()
                    + " doze_exempt=" + power.isIgnoringBatteryOptimizations(context.getPackageName())
                    + " low_power_standby=" + (Build.VERSION.SDK_INT >= 33 ? Boolean.toString(power.isLowPowerStandbyEnabled()) : "unsupported")
                    + " fine_location_granted=" + (context.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == 0)
                    + " background_location_granted=" + (context.checkSelfPermission("android.permission.ACCESS_BACKGROUND_LOCATION") == 0);
        } catch (Throwable error) { return "state_query_failed=" + error.getClass().getName(); }
    }

    private static synchronized void write(String code, String detail) {
        String line = System.currentTimeMillis() + " elapsed_ms=" + SystemClock.elapsedRealtime() + " " + code + " " + detail;
        Log.i(TAG, line);
        if (file == null) return;
        try {
            if (file.length() > 2 * 1024 * 1024) {
                File previous = new File(file.getParentFile(), "navigation.previous.log");
                if (previous.exists() && !previous.delete()) throw new java.io.IOException("Cannot rotate previous log");
                if (!file.renameTo(previous)) throw new java.io.IOException("Cannot rotate navigation log");
            }
            try (FileOutputStream stream = new FileOutputStream(file, true)) {
                stream.write((line + "\n").getBytes("UTF-8"));
                stream.flush();
            }
        } catch (Throwable error) { Log.e(TAG, "LOG_WRITE_FAILED (logcat remains available)", error); }
    }
}
