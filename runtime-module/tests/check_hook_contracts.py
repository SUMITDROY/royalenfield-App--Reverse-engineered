#!/usr/bin/env python3
"""Validate exact hook targets against the original APK's decoded DEX contracts."""
import pathlib,re,argparse
root=pathlib.Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--decoded',type=pathlib.Path,default=root/'analysis/apktool')
args=parser.parse_args()
if not args.decoded.is_dir():raise SystemExit('Decode your own original version-193 APK with APKTool; supply --decoded PATH.')
targets={
 'com/royalenfield/reprime/ui/home/navigation/service/REForeGroundService':[
   'onStartCommand(Landroid/content/Intent;II)I','H()V','M()V','N()V','n()V','s()V','A(Landroid/support/v4/media/MediaMetadataCompat;)V'],
 'bluconnect/djf':['H()V','M()V','N()V','n()V','s()V'],
 'bluconnect/gub':['g(Landroid/graphics/Bitmap;Ljava/lang/String;)V'],
 'bluconnect/r42':['m()Lbluconnect/r42;','h(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;','y(Ljava/lang/String;Ljava/lang/String;)V','u(Ljava/lang/String;)V'],
 'android/support/v4/media/MediaMetadataCompat':['b(Ljava/lang/Object;)Landroid/support/v4/media/MediaMetadataCompat;'],
}
for cls,methods in targets.items():
    files=list(args.decoded.glob('smali*/'+cls+'.smali'))
    assert len(files)==1,(cls,'original smali missing/ambiguous')
    source=files[0].read_text()
    for method in methods:
        assert re.search(r'^\.method [^\n]* '+re.escape(method)+r'$',source,re.M),(cls,method,'not declared')
print('PASS: original APK exact method signatures for all bridge hooks, metadata and call-forwarding targets')
