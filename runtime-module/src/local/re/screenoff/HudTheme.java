package local.re.screenoff;
import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Lightweight native HUD styling. No animation loops or background rendering. */
final class HudTheme {
    static final int BG=0xFF060D13,PANEL=0xFF0C1B25,CYAN=0xFF68E8F3,TEXT=0xFFE5F1F5,MUTED=0xFF90ABB9,LINE=0xFF254450;
    static int dp(Context c,float n){return (int)(c.getResources().getDisplayMetrics().density*n+.5f);}
    static GradientDrawable panel(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(14);d.setStroke(1,stroke);return d;}
    static void window(Activity a){a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);a.getWindow().getDecorView().setBackgroundColor(BG);a.getWindow().getInsetsController().setSystemBarsAppearance(0,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
    static void decorate(View v){
        if(v instanceof Button&&!(v instanceof CheckBox)){Button b=(Button)v;b.setAllCaps(false);b.setTextSize(14);b.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{CYAN,0xFF667E8A}));b.setTypeface(Typeface.create("sans-serif-medium",0));b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setPadding(dp(v.getContext(),16),dp(v.getContext(),12),dp(v.getContext(),16),dp(v.getContext(),12));b.setMinHeight(dp(v.getContext(),52));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x335FE6F3),panel(PANEL,LINE),null));
            if(b.getLayoutParams() instanceof LinearLayout.LayoutParams){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)b.getLayoutParams();lp.topMargin=dp(v.getContext(),5);lp.bottomMargin=dp(v.getContext(),5);b.setLayoutParams(lp);}
        }else if(v instanceof CheckBox){CheckBox c=(CheckBox)v;c.setTextColor(TEXT);c.setTextSize(14);c.setButtonTintList(ColorStateList.valueOf(CYAN));c.setPadding(0,dp(v.getContext(),8),0,dp(v.getContext(),8));}
        else if(v instanceof TextView){TextView t=(TextView)v;if(!"hud-label".equals(t.getTag()))t.setTextColor(TEXT);if(t.getTextSize()/t.getResources().getDisplayMetrics().scaledDensity>=20){t.setTextColor(CYAN);t.setTypeface(Typeface.create("sans-serif-medium",0));}else t.setLineSpacing(dp(v.getContext(),3),1);}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)decorate(g.getChildAt(i));}
    }
    static TextView label(Context c,String text,int size,int color){TextView t=new TextView(c);t.setTag("hud-label");t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif-medium",0));return t;}
    static final class Reactor extends View {
        private boolean active;
        Reactor(Context c){super(c);setContentDescription("Bridge connection indicator");}
        void active(boolean b){if(active!=b){active=b;invalidate();}}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-8;Paint p=new Paint(3);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(LINE);canvas.drawCircle(cx,cy,r,p);p.setColor(active?CYAN:MUTED);p.setStrokeWidth(4);
            RectF arc=new RectF(cx-r+8,cy-r+8,cx+r-8,cy+r-8);for(int i=0;i<3;i++)canvas.drawArc(arc, i*120+12,92,false,p);
            p.setStrokeWidth(1);canvas.drawCircle(cx,cy,r*.59f,p);p.setStyle(Paint.Style.FILL);p.setColor(active?0x225FE6F3:0x11254450);canvas.drawCircle(cx,cy,r*.53f,p);
            p.setColor(active?CYAN:MUTED);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(r*.23f);canvas.drawText(active?"ONLINE":"STANDBY",cx,cy+5,p);
            for(int i=0;i<24;i++){double a=i*Math.PI/12;float out=r+3;p.setStrokeWidth(1);canvas.drawLine(cx+(float)Math.cos(a)*(out-4),cy+(float)Math.sin(a)*(out-4),cx+(float)Math.cos(a)*out,cy+(float)Math.sin(a)*out,p);}
        }
    }
}
