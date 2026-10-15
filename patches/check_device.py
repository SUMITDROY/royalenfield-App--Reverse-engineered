#!/usr/bin/env python3
"""Read-only Tripper Dash preflight and optional APK extraction via ADB.

Does not install/uninstall apps, change settings, unlock, or switch the display.
Reports device observations; never equates preflight success with a working fix.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

PACKAGE = "com.royalenfield.reprime"
PERMISSIONS = (
    "ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION", "BLUETOOTH_CONNECT",
    "POST_NOTIFICATIONS", "FOREGROUND_SERVICE_LOCATION",
)


def find_adb(explicit):
    if explicit:
        return explicit
    executable = shutil.which("adb")
    if executable:
        return executable
    roots = [os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME"),
             str(Path.home() / "Library/Android/sdk"), str(Path.home() / "Android/Sdk")]
    for root in filter(None, roots):
        path = Path(root) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
        if path.is_file():
            return str(path)
    raise ValueError("ADB not found. Supply --adb /path/to/adb.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb")
    parser.add_argument("--serial", help="Required if multiple devices are connected")
    parser.add_argument("--output", type=Path, default=Path("output/device-check"))
    parser.add_argument("--pull-apks", action="store_true", help="Copy all installed APK splits locally")
    args = parser.parse_args()
    try:
        adb = find_adb(args.adb)

        def run(arguments, serial=None, timeout=15, required=True):
            command = [adb] + (["-s", serial] if serial else []) + arguments
            result = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
            if required and result.returncode:
                raise ValueError(result.stderr.strip() or result.stdout.strip() or "ADB command failed")
            return {"available": result.returncode == 0, "output": result.stdout.strip(),
                    **({"error": result.stderr.strip()} if result.returncode else {})}

        lines = run(["devices"])['output'].splitlines()[1:]
        devices = dict(line.split()[:2] for line in lines if len(line.split()) >= 2)
        serial = args.serial
        if serial is None:
            usable = [device for device, state in devices.items() if state == "device"]
            if len(usable) != 1:
                raise ValueError("Connect one USB-debugging-authorized phone, or select it with --serial. "
                                 f"Detected device states: {list(devices.values())}")
            serial = usable[0]
        if devices.get(serial) != "device":
            raise ValueError("Selected device is offline or unauthorized. Approve USB debugging on the phone.")

        def shell(*arguments, required=False):
            return run(["shell", *arguments], serial, required=required)

        def prop(name):
            return shell("getprop", name, required=True)['output']

        api = int(prop("ro.build.version.sdk"))
        pkg = shell("dumpsys", "package", PACKAGE, required=True)['output']
        paths_result = shell("pm", "path", PACKAGE, required=True)['output']
        paths = [line.removeprefix("package:") for line in paths_result.splitlines() if line.startswith("package:")]
        if not paths:
            raise ValueError("Royal Enfield app is not installed on this device.")
        version_match = re.search(r"\bversionCode=(\d+)", pkg)
        version = int(version_match.group(1)) if version_match else None
        permissions = {name: re.findall(r"android\.permission\." + name + r": granted=(true|false)", pkg)
                       for name in PERMISSIONS}
        power = shell("dumpsys", "power")
        power_lines = [line.strip() for line in power['output'].splitlines()
                       if re.search(r"mWakefulness=|mIsPowered=|mLowPowerStandby|mLowPowerMode|Display Power:|RE::WakelockTag|reprime::WakeLock", line)]
        whitelisted = shell("cmd", "deviceidle", "whitelist", "=" + PACKAGE)
        services = shell("dumpsys", "activity", "services", PACKAGE)
        service_lines = [line.strip() for line in services['output'].splitlines()
                         if re.search(r"ServiceRecord\{|isForeground=|foregroundServiceType=", line)]
        issues = []
        if api < 31:
            issues.append("Unsupported by this APK: minimum Android API 31 (Android 12).")
        if version != 193:
            issues.append("Installed app version differs from the version-pinned patch; do not apply it to these APKs.")
        if len(paths) == 1:
            issues.append("Only one APK reported; verify package completeness before building an installation.")
        if permissions["ACCESS_FINE_LOCATION"] == ["false"]:
            issues.append("Precise location permission is denied.")
        record = {
            "collected_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "device": {"manufacturer": prop("ro.product.manufacturer"), "model": prop("ro.product.model"),
                       "android_release": prop("ro.build.version.release"), "api": api,
                       "abis": prop("ro.product.cpu.abilist")},
            "app": {"package": PACKAGE, "version_code": version, "apk_count": len(paths),
                    "apk_names": [Path(path).name for path in paths]},
            "permission_grants": permissions,
            "power_observations": {"available": power['available'], "lines": power_lines},
            "doze_exemption_query": whitelisted,
            "service_observations": {"available": services['available'], "lines": service_lines},
            "issues": issues,
            "compatibility_status": "UNVERIFIED: preflight does not test screen-off map rendering",
            "required_runtime_checks": ["Changing map frames on Tripper Dash while phone is locked",
                                        "GPS/navigation progress and rerouting", "Unlock/reconnect/end-navigation cleanup",
                                        "Battery saver and OEM background restrictions"],
        }
        args.output.mkdir(parents=True, exist_ok=True)
        if args.pull_apks:
            destination = args.output / "apks"
            destination.mkdir(exist_ok=True)
            extracted = []
            for index, remote in enumerate(paths):
                # Use an index and basename only; device paths never become local directories.
                local = destination / f"{index:02d}-{Path(remote).name}"
                if local.exists():
                    raise ValueError(f"Refusing to overwrite {local}; select a fresh --output directory.")
                run(["pull", remote, str(local)], serial, timeout=180)
                with local.open("rb") as stream:
                    digest = hashlib.file_digest(stream, "sha256").hexdigest()
                extracted.append({"file": str(local), "sha256": digest})
            record['extracted_apks'] = extracted
        report = args.output / "preflight.json"
        report.write_text(json.dumps(record, indent=2) + "\n")
        print(f"Saved {report}. Screen-off navigation compatibility remains unverified.")
        for issue in issues:
            print(issue)
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        parser.exit(1, f"Device check could not complete: {error}\n")


if __name__ == "__main__":
    main()
