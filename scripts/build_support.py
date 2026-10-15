"""Portable, stdlib-only Android build configuration; no private SDKs or keys."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
XPOSED_SHA256 = 'f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25'
XPOSED_URL = ('https://raw.githubusercontent.com/rovo89/XposedBridge/'
              '79f70cf567fdb42f83313f6cad97a6ffc878f3ac/de/robv/android/xposed/api/82/api-82.jar')

def java_bin():
    home = os.environ.get('JAVA_HOME')
    if home and (Path(home)/'bin/javac').is_file():
        return Path(home)/'bin'
    javac = shutil.which('javac')
    if javac:
        return Path(javac).resolve().parent
    raise SystemExit('Install JDK 17+ and set JAVA_HOME (see docs/BUILDING.md).')

def sdk_root():
    candidates = [os.environ.get('ANDROID_SDK_ROOT'), os.environ.get('ANDROID_HOME'),
                  Path.home()/'Library/Android/sdk', Path.home()/'Android/Sdk']
    for candidate in filter(None, candidates):
        if (Path(candidate)/'platforms/android-35/android.jar').is_file():
            return Path(candidate)
    raise SystemExit('Install platforms;android-35 and set ANDROID_SDK_ROOT.')

def xposed_api(download=False):
    path = ROOT/'tools/xposed/api-82.jar'
    if not path.exists() and download:
        path.parent.mkdir(parents=True, exist_ok=True)
        data = urllib.request.urlopen(XPOSED_URL, timeout=45).read()
        if hashlib.sha256(data).hexdigest() != XPOSED_SHA256:
            raise SystemExit('Xposed API checksum mismatch; dependency not saved.')
        path.write_bytes(data)
    if not path.exists():
        raise SystemExit('Run python3 scripts/bootstrap.py to fetch the compile-only Xposed API.')
    if hashlib.sha256(path.read_bytes()).hexdigest() != XPOSED_SHA256:
        raise SystemExit('Xposed API checksum mismatch.')
    return path

def configuration():
    java = java_bin(); sdk = sdk_root()
    bt = sdk/'build-tools'/os.environ.get('ANDROID_BUILD_TOOLS_VERSION', '36.1.0')
    for name in ['aapt', 'd8', 'zipalign', 'apksigner']:
        if not (bt/name).exists():
            raise SystemExit(f'Missing {bt/name}; install the documented Android build tools.')
    env = dict(os.environ, JAVA_HOME=str(java.parent))
    return java, sdk, bt, sdk/'platforms/android-35/android.jar', env

def signing(java, env):
    configured = os.environ.get('RE_KEYSTORE')
    key = Path(configured).expanduser() if configured else ROOT/'.local/signing/debug.jks'
    alias = os.environ.get('RE_KEY_ALIAS', 'tripper-debug')
    password = os.environ.get('RE_KEYSTORE_PASSWORD')
    password_file = os.environ.get('RE_KEYSTORE_PASSWORD_FILE')
    if password_file:
        password = Path(password_file).expanduser().read_text().strip()
    if configured and not password:
        raise SystemExit('Custom keystore requires RE_KEYSTORE_PASSWORD or RE_KEYSTORE_PASSWORD_FILE.')
    if not configured:
        password = 'android'  # Public debug default; never a release credential.
        if not key.exists():
            key.parent.mkdir(parents=True, exist_ok=True)
            key.parent.chmod(0o700)
            key_env = dict(env, RE_BUILD_STORE_PASSWORD=password)
            subprocess.run([str(java/'keytool'), '-genkeypair', '-noprompt', '-keystore', str(key),
                '-storepass:env', 'RE_BUILD_STORE_PASSWORD', '-keypass:env', 'RE_BUILD_STORE_PASSWORD',
                '-alias', alias, '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
                '-dname', 'CN=Tripper local development'], check=True, env=key_env)
            key.chmod(0o600)
    if not key.is_file():
        raise SystemExit(f'Keystore not found: {key}')
    env['RE_BUILD_STORE_PASSWORD'] = password
    return ['--ks', key, '--ks-key-alias', alias, '--ks-pass', 'env:RE_BUILD_STORE_PASSWORD']
