#!/usr/bin/env python3
"""Read-only export of root-accessible bridge logs and RE navigation diagnostics."""
import argparse,pathlib,subprocess,json,datetime,shutil
from check_device import find_adb
p=argparse.ArgumentParser();p.add_argument('--adb');p.add_argument('--output',default='output/iphone-bridge-logs');p.add_argument('--serial');a=p.parse_args()
try:adb=find_adb(a.adb)
except ValueError as error:raise SystemExit(str(error))
prefix=[adb]+(['-s',a.serial] if a.serial else [])
out=pathlib.Path(a.output);out.mkdir(parents=True,exist_ok=True);results=[]
def collect(name,args):
    try:
        r=subprocess.run(prefix+args,capture_output=True,timeout=40)
        (out/name).write_bytes(r.stdout)
        results.append({'file':name,'returncode':r.returncode,'stderr':r.stderr.decode(errors='replace')})
    except subprocess.TimeoutExpired:results.append({'file':name,'error':'ADB timeout after 40s'})
for name in ['iphone-bridge.log','iphone-bridge.previous.log']:
    collect(name,['exec-out','su','-c','cat /data/user/0/local.re.screenoff.module/files/'+name])
collect('navigation.log',['exec-out','cat','/sdcard/Android/data/com.royalenfield.reprime/files/screenoff/navigation.log'])
collect('bridge-logcat.txt',['logcat','-d','-v','threadtime','-s','REIPhoneBridge','REScreenOff','REHfpCompatibility','AndroidRuntime'])
for package in ['local.re.screenoff.module','com.royalenfield.reprime','com.android.bluetooth']:
    collect(package+'-exits.txt',['shell','dumpsys','activity','exit-info',package])
(out/'export-result.json').write_text(json.dumps({'exported_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'results':results},indent=2)+'\n')
print(out.resolve())
print('Root is required for private bridge files. Export bridge logs in the app is the non-root fallback.')
