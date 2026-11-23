package local.re.screenoff;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** User-picked wallpaper copied privately; no broad media/storage permission. */
public final class AppearanceActivity extends Activity {
    private DashboardAppearance.Odometer preview;private TextView status;private boolean importing;
    @Override public void onCreate(Bundle b){super.onCreate(b);HudTheme.window(this);
        ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(24,24,24,32);scroll.addView(box);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars());scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);return i;});scroll.requestApplyInsets();
        box.addView(HudTheme.label(this,"PERSONALISE  /  ANDROID APP ONLY",11,HudTheme.CYAN));box.addView(HudTheme.label(this,"Your cockpit",30,HudTheme.TEXT));
        TextView help=HudTheme.label(this,"Wallpaper and odometer styling for this app. The bike’s native instruments are unchanged.",14,HudTheme.MUTED);help.setPadding(0,20,0,20);box.addView(help);
        preview=new DashboardAppearance.Odometer(this);box.addView(preview,new LinearLayout.LayoutParams(-1,HudTheme.dp(this,184)));status=HudTheme.label(this,"",13,HudTheme.MUTED);box.addView(status);
        button(box,"Choose wallpaper from photos",()->{if(importing)return;startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),301);});
        button(box,"Built-in wallpaper",()->new AlertDialog.Builder(this).setTitle("Background").setItems(new String[]{"Cyan technical grid","Ember technical grid","Plain dark"},(d,n)->{DashboardAppearance.prefs(this).edit().putString("wallpaper",new String[]{"grid","ember","plain"}[n]).apply();refresh();}).show());
        button(box,"Remove saved photo wallpaper",()->{new File(getFilesDir(),"dashboard-wallpaper.png").delete();DashboardAppearance.prefs(this).edit().putString("wallpaper","grid").apply();refresh();});
        button(box,"Set manual odometer (km)",this::editOdometer);
        button(box,"Odometer design",()->new AlertDialog.Builder(this).setTitle("Display design").setItems(new String[]{"Digital","Arc","Minimal"},(d,n)->{DashboardAppearance.prefs(this).edit().putString("odoStyle",new String[]{"digital","arc","minimal"}[n]).apply();refresh();}).show());
        button(box,"Odometer accent",()->new AlertDialog.Builder(this).setTitle("Accent colour").setItems(new String[]{"Cyan","Amber","Crimson"},(d,n)->{DashboardAppearance.prefs(this).edit().putInt("odoAccent",new int[]{HudTheme.CYAN,0xFFFFCA69,0xFFFF7E83}[n]).apply();refresh();}).show());
        button(box,"Display units",()->new AlertDialog.Builder(this).setTitle("Display units (stored reading stays in km)").setItems(new String[]{"Kilometres","Miles"},(d,n)->{DashboardAppearance.prefs(this).edit().putString("odoUnit",n==0?"km":"mi").apply();refresh();}).show());
        button(box,"Back to command centre",()->{startActivity(new Intent(this,BridgeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});HudTheme.decorate(box);refresh();
    }
    private void button(LinearLayout box,String name,Runnable action){Button b=new Button(this);b.setText(name);b.setOnClickListener(v->action.run());box.addView(b);}
    private void refresh(){DashboardAppearance.apply(this);preview.invalidate();preview.setContentDescription("Manual odometer "+DashboardAppearance.reading(this)+" "+DashboardAppearance.prefs(this).getString("odoUnit","km")+". User entered, not live bike data.");status.setText("Saved on this phone • wallpaper: "+DashboardAppearance.prefs(this).getString("wallpaper","grid"));}
    private void editOdometer(){EditText entry=new EditText(this);entry.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);entry.setText(DashboardAppearance.prefs(this).getString("odometer",""));entry.setHint("Enter your actual reading in km");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Manual odometer • kilometres").setMessage("For this app’s records only. Does not read or change the motorcycle odometer.").setView(entry).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{double km=Double.parseDouble(entry.getText().toString());if(!Double.isFinite(km)||km<0||km>10000000)throw new IllegalArgumentException();DashboardAppearance.prefs(this).edit().putString("odometer",Double.toString(km)).apply();refresh();dialog.dismiss();}catch(Exception e){entry.setError("Enter a valid reading from 0 to 10,000,000 km");}}));dialog.show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=301||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();importing=true;status.setText("Importing wallpaper…");
        new Thread(()->{File temp=null,output=null;String message;
            try{
                temp=File.createTempFile("wallpaper-input-",".tmp",getFilesDir());output=File.createTempFile("wallpaper-output-",".tmp",getFilesDir());
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>16*1024*1024)throw new IOException("Image file exceeds 16 MB");out.write(buf,0,n);}}
                Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(temp),(decoder,info,source)->{android.util.Size size=info.getSize();float scale=Math.min(1,1600f/Math.max(size.getWidth(),size.getHeight()));decoder.setTargetSize(Math.max(1,(int)(size.getWidth()*scale)),Math.max(1,(int)(size.getHeight()*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
                try(OutputStream out=new FileOutputStream(output)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Unable to save image");}finally{bitmap.recycle();}
                if(!output.renameTo(new File(getFilesDir(),"dashboard-wallpaper.png")))throw new IOException("Unable to replace wallpaper");
                DashboardAppearance.prefs(this).edit().putString("wallpaper","photo").apply();message="Wallpaper saved privately";
            }catch(Exception|OutOfMemoryError e){message="Wallpaper import failed; choose a supported image under 16 MB";BridgeState.log("WALLPAPER_IMPORT_FAILED",null);}finally{if(temp!=null)temp.delete();if(output!=null)output.delete();}
            final String notice=message;runOnUiThread(()->{importing=false;if(!isFinishing()&&!isDestroyed()){refresh();Toast.makeText(this,notice,Toast.LENGTH_LONG).show();}});
        },"WallpaperImport").start();
    }
}
