#!/usr/bin/env python3
"""Validate exact hook targets against the original APK's decoded DEX contracts."""
import pathlib,re,argparse
root=pathlib.Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--version',type=int,choices=[193,195],default=193)
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
if args.version==195:
    targets={cls.replace('bluconnect/djf','bluconnect/tpg').replace('bluconnect/gub','bluconnect/cmc').replace('bluconnect/r42','bluconnect/oa2'):[m.replace('H()V','U()V').replace('N()V','e0()V').replace('M()V','Z()V').replace('s()V','y()V').replace('n()V','r()V').replace('Lbluconnect/r42;','Lbluconnect/oa2;') for m in methods] for cls,methods in targets.items()}
    targets['bluconnect/ppg']=['A()Lbluconnect/ppg;','p()Lbluconnect/ppg$b;','N()Lbluconnect/lvc;']
else:
    targets['bluconnect/zif']=['y()Lbluconnect/zif;','o()Lbluconnect/zif$b;','L()Lbluconnect/y2c;']
for cls in ['com/royalenfield/reprime/ui/home/navigation/fragment/NavigationFragment','com/royalenfield/reprime/ui/home/navigation/fragment/NavigationRootFragment','com/google/android/libraries/navigation/SupportNavigationFragment']:
    targets.setdefault(cls,[]).append('onPause()V')
targets['com/royalenfield/reprime/ui/home/navigation/fragment/NavigationFragment']+=['onStop()V','r(Landroid/graphics/Bitmap;)V']
targets['com/google/android/libraries/navigation/SupportNavigationFragment']+=['onStop()V','onStart()V','onResume()V']
targets['bluconnect/cmc' if args.version==195 else 'bluconnect/gub']+=['i()V','j()V']
targets['bluconnect/zfg' if args.version==195 else 'bluconnect/w9f']=['d()V','e()V']
for cls,methods in targets.items():
    files=list(args.decoded.glob('smali*/'+cls+'.smali'))
    assert len(files)==1,(cls,'original smali missing/ambiguous')
    source=files[0].read_text()
    for method in methods:
        assert re.search(r'^\.method [^\n]* '+re.escape(method)+r'$',source,re.M),(cls,method,'not declared')
print('PASS: original APK exact method signatures for all bridge hooks, metadata and call-forwarding targets')
