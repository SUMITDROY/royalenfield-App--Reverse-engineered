package local.re.screenoff;
import android.graphics.*;
import android.os.Bundle;

/** Draws a bounded card on a copy; never mutates the original navigation frame. */
public final class OverlayRenderer {
    private static String fit(String s,Paint p,float width){
        if(p.measureText(s)<=width)return s;String suffix="…";
        while(!s.isEmpty()&&p.measureText(s+suffix)>width){int end=s.offsetByCodePoints(s.length(),-1);s=s.substring(0,end);}return s+suffix;
    }
    public static Bitmap render(Bitmap source,Bundle b,Bitmap image){
        String call=b.getString("call",""),alert=b.getString("alert",""),title=b.getString("title","");
        if(source==null||source.isRecycled()||(call.isEmpty()&&alert.isEmpty()&&title.isEmpty()))return source;
        if(source.getWidth()<200||source.getHeight()<120||source.getWidth()>2048||source.getHeight()>2048)return source;
        Bitmap copy=source.copy(Bitmap.Config.ARGB_8888,true);if(copy==null)return source;
        Canvas canvas=new Canvas(copy);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        float scale=source.getWidth()/526f,pad=8*scale,height=(call.isEmpty()?60:88)*scale,width=Math.min(source.getWidth()*.45f,240*scale);
        float x=source.getWidth()-width-pad,y=source.getHeight()-height-16*scale;
        paint.setColor(Color.argb(222,16,18,22));canvas.drawRoundRect(x,y,x+width,y+height,8*scale,8*scale,paint);
        float textX=x+pad;
        if(call.isEmpty()&&alert.isEmpty()&&image!=null&&!image.isRecycled()){
            paint.setAlpha(255);
            canvas.drawBitmap(image,null,new RectF(x+pad,y+pad,x+pad+44*scale,y+pad+44*scale),paint);textX+=52*scale;
        }
        paint.setColor(Color.WHITE);paint.setTextSize(13*scale);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        String phase=b.getString("callPhase","");
        String top=!call.isEmpty()?(phase.equals("active")?"iPhone call connected":phase.equals("held")?"iPhone call on hold":phase.equals("outgoing")?"Calling from iPhone":"Incoming iPhone call"):!alert.isEmpty()?"iPhone notification":title;
        canvas.drawText(fit(top,paint,x+width-pad-textX),textX,y+24*scale,paint);
        paint.setColor(Color.LTGRAY);paint.setTextSize(11*scale);paint.setTypeface(Typeface.DEFAULT);
        String lower=!call.isEmpty()?call:!alert.isEmpty()?alert:b.getString("artist","");
        canvas.drawText(fit(lower,paint,x+width-pad-textX),textX,y+43*scale,paint);
        if(!call.isEmpty()){
            String positive=b.getString("positiveLabel",""),negative=b.getString("negativeLabel","");
            int selected=CallActionGate.selection(b.getInt("selectedAction",positive.isEmpty()?1:0),positive,negative);String label=selected==0?positive:negative;
            paint.setColor(Color.WHITE);paint.setTextSize(11*scale);
            String hint=label.isEmpty()?"No remote action available":"Press: "+label;
            canvas.drawText(fit(hint,paint,width-pad*2),x+pad,y+62*scale,paint);
            if(!positive.isEmpty()&&!negative.isEmpty()){paint.setColor(Color.LTGRAY);paint.setTextSize(9*scale);canvas.drawText("Next / previous: change action",x+pad,y+78*scale,paint);}
        }
        return copy;
    }
}
