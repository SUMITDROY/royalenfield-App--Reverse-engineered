#!/usr/bin/env python3
"""Isolated on-device graphics test; no Bluetooth, RE hooks or user data."""
import pathlib,subprocess,os,zipfile,sys,shutil
root=pathlib.Path(__file__).resolve().parents[2];tests=root/'runtime-module/tests'
out=root/'output/bridge-overlay-tests';out.mkdir(exist_ok=True)
classes=out/'classes';dex=out/'dex'
for folder in [classes,dex]:
    shutil.rmtree(folder,ignore_errors=True);folder.mkdir()
sys.path.insert(0,str(root/'scripts'))
from build_support import configuration,signing
java_bin,sdk,bt,android,env=configuration();java=java_bin.parent
def run(args):subprocess.run([str(a) for a in args],check=True,env=env)
base=root/'runtime-module/src/local/re/screenoff'
run([java/'bin/javac','-source','8','-target','8','-cp',android,'-d',classes,tests/'OverlayDeviceTest.java',tests/'CallStateDeviceTest.java',*[base/name for name in ['OverlayRenderer.java','HfpController.java','BridgeState.java','AppleProtocol.java','CallActionGate.java']]])
jar=out/'classes.jar'
with zipfile.ZipFile(jar,'w') as z:
    for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
run([bt/'d8','--min-api','31','--lib',android,'--output',dex,jar])
unsigned=out/'unsigned.apk';run([bt/'aapt','package','-f','-M',tests/'AndroidManifest.xml','-I',android,'-F',unsigned])
with zipfile.ZipFile(unsigned,'a') as z:z.write(dex/'classes.dex','classes.dex')
aligned=out/'aligned.apk';run([bt/'zipalign','-f','4',unsigned,aligned])
apk=out/'overlay-test.apk'
run([bt/'apksigner','sign',*signing(java_bin,env),'--out',apk,aligned])
print(apk)
