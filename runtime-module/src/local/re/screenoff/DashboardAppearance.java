package local.re.screenoff;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.io.*;
import java.util.Locale;

/** App-only background and manually entered odometer. Never writes bike firmware/ECU data. */
final class DashboardAppearance {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("bridge",0);}
    static String reading(Context c){String raw=prefs(c).getString("odometer","");try{double km=Double.parseDouble(raw);if(!Double.isFinite(km)||km<0)return "—";return String.format(Locale.US,"%,.1f",prefs(c).getString("odoUnit","km").equals("mi")?km/1.609344:km);}catch(Exception e){return "—";}}
    static int accent(Context c){return prefs(c).getInt("odoAccent",HudTheme.CYAN);}
    static void apply(android.app.Activity a){a.getWindow().setBackgroundDrawable(new Wallpaper(a));}
    static final class Wallpaper extends Drawable {
        private final Paint paint=new Paint(3);private Bitmap bitmap;private final String preset;
        Wallpaper(Context c){preset=prefs(c).getString("wallpaper","grid");if(preset.equals("photo")){BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.RGB_565;try{bitmap=BitmapFactory.decodeFile(new File(c.getFilesDir(),"dashboard-wallpaper.png").toString(),o);}catch(OutOfMemoryError ignored){}}}
        @Override public void draw(Canvas canvas){Rect b=getBounds();canvas.drawColor(HudTheme.BG);
            if(bitmap!=null){float scale=Math.max(b.width()/(float)bitmap.getWidth(),b.height()/(float)bitmap.getHeight());float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;paint.setColor(Color.WHITE);canvas.drawBitmap(bitmap,null,new RectF(b.centerX()-w/2,b.centerY()-h/2,b.centerX()+w/2,b.centerY()+h/2),paint);canvas.drawColor(0xBE030B12);return;}
            int tint=preset.equals("ember")?0xFF7D321E:0xFF124252;
            if(preset.equals("plain"))return;
            paint.setShader(new RadialGradient(b.width()*.8f,b.height()*.12f,Math.max(1,b.height()*.75f),tint,HudTheme.BG,Shader.TileMode.CLAMP));canvas.drawRect(b,paint);paint.setShader(null);paint.setColor(0x162EE3F0);paint.setStrokeWidth(1);
            float step=48;for(float x=0;x<b.width();x+=step)canvas.drawLine(x,0,x,b.height(),paint);for(float y=0;y<b.height();y+=step)canvas.drawLine(0,y,b.width(),y,paint);
        }
        @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
    static final class Odometer extends View {
        private final Paint p=new Paint(3);
        Odometer(Context c){super(c);setContentDescription("Manual odometer reading; not connected to bike ECU");}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight(),pad=HudTheme.dp(getContext(),16);String style=prefs(getContext()).getString("odoStyle","digital");int accent=accent(getContext());
            p.setStyle(Paint.Style.FILL);p.setColor(0xEC0A1922);canvas.drawRoundRect(pad/4,4,w-pad/4,h-4,16,16,p);p.setStyle(Paint.Style.STROKE);p.setColor(accent);p.setStrokeWidth(1);canvas.drawRoundRect(pad/4,4,w-pad/4,h-4,16,16,p);
            if(style.equals("arc")){p.setStrokeWidth(3);RectF arc=new RectF(w*.22f,h*.16f,w*.78f,h*1.1f);canvas.drawArc(arc,195,150,false,p);}
            p.setStyle(Paint.Style.FILL);p.setColor(HudTheme.MUTED);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(HudTheme.dp(getContext(),10));canvas.drawText("MANUAL ODOMETER",w/2,h*.2f,p);
            String value=reading(getContext());p.setColor(accent);p.setTypeface(Typeface.create(style.equals("digital")?"monospace":"sans-serif-light",0));p.setTextSize(HudTheme.dp(getContext(),style.equals("minimal")?32:40));if(p.measureText(value)>w-pad*2)p.setTextSize(p.getTextSize()*(w-pad*2)/p.measureText(value));canvas.drawText(value,w/2,h*.6f,p);
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(HudTheme.dp(getContext(),11));p.setColor(HudTheme.TEXT);canvas.drawText(prefs(getContext()).getString("odoUnit","km").equals("mi")?"MILES":"KILOMETRES",w/2,h*.76f,p);
            p.setColor(HudTheme.MUTED);p.setTextSize(HudTheme.dp(getContext(),9));canvas.drawText("USER ENTERED  /  NO LIVE BIKE DATA",w/2,h*.91f,p);
        }
    }
}
