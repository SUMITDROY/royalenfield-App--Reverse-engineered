#!/usr/bin/env python3
"""Build the separate module; never repackages or installs Royal Enfield."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile
root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root/'scripts'))
from build_support import configuration, signing, xposed_api

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--check-hooks', action='store_true', help='Validate against your local APKTool decode (not distributed)')
args = parser.parse_args()
module = root/'runtime-module'
java, sdk, bt, android, env = configuration()
api = xposed_api()
if args.check_hooks:
    subprocess.run([sys.executable, str(module/'tests/check_hook_contracts.py')], check=True)
else:
    print('Hook contract validation not requested; run --check-hooks with your own decoded APK.')
build = root/'output/runtime-module-build'; build.mkdir(parents=True, exist_ok=True)
classes = build/'classes'; dex = build/'dex'
for folder in [classes, dex]:
    shutil.rmtree(folder, ignore_errors=True); folder.mkdir()
def run(arguments):
    subprocess.run([str(a) for a in arguments], check=True, env=env)
sources = sorted((module/'src').rglob('*.java')) + [root/'patches/java/local/re/screenoff/Diagnostics.java']
run([java/'javac', '-source', '8', '-target', '8', '-cp', str(android)+os.pathsep+str(api), '-d', classes, *sources])
jar = build/'classes.jar'
with zipfile.ZipFile(jar, 'w') as archive:
    for path in sorted(classes.rglob('*.class')):
        archive.write(path, path.relative_to(classes))
run([bt/'d8', '--min-api', '31', '--lib', android, '--classpath', api, '--output', dex, jar])
unsigned = build/'module-unsigned.apk'
run([bt/'aapt', 'package', '-f', '-M', module/'AndroidManifest.xml', '-S', module/'res', '-A', module/'assets', '-I', android, '-F', unsigned])
with zipfile.ZipFile(unsigned, 'a') as archive:
    archive.write(dex/'classes.dex', 'classes.dex')
aligned = build/'module-aligned.apk'
run([bt/'zipalign', '-f', '-P', '16', '4', unsigned, aligned])
out = root/'output/tripper-screen-off-lsposed.apk'
run([bt/'apksigner', 'sign', *signing(java, env), '--out', out, aligned])
run([bt/'apksigner', 'verify', out])
print(out)
