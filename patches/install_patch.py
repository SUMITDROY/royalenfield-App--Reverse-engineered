#!/usr/bin/env python3
"""Install all four patch APKs together and save the exact ADB result.

Never uninstalls an existing application or clears its data. Android rejects
an update when the installed app has a different signing certificate.
"""
import argparse
import datetime
import json
import re
import shutil
import subprocess
from pathlib import Path

NAMES = ["royalenfield-screen-off-with-logs.apk", "split_config.arm64_v8a.apk",
         "split_config.en.apk", "split_config.xxhdpi.apk"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb")
    parser.add_argument("--serial")
    parser.add_argument("--apks-dir", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    parent = Path(__file__).resolve().parent
    directory = args.apks_dir or (parent if (parent / NAMES[0]).exists()
                                 else parent.parent / "output/tripper-dash-with-logs")
    report = args.report or directory / ("install-result-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S-%f") + ".json")
    result = {"installed": False, "uninstall_performed": False, "phase": "preflight"}
    try:
        missing = [name for name in NAMES if not (directory / name).is_file()]
        if missing: raise ValueError("Missing companion APKs: " + ", ".join(missing))
        adb = args.adb or shutil.which("adb")
        if not adb:
            for base in (Path.home() / "Library/Android/sdk", Path.home() / "Android/Sdk"):
                candidate = base / "platform-tools/adb"
                if candidate.is_file(): adb = str(candidate); break
        if not adb: raise ValueError("ADB not found; supply --adb /path/to/adb")
        listing = subprocess.run([adb, "devices"], capture_output=True, text=True, timeout=15)
        if listing.returncode: raise ValueError(listing.stderr.strip())
        devices = dict(line.split()[:2] for line in listing.stdout.splitlines()[1:] if len(line.split()) >= 2)
        phones = [serial for serial, state in devices.items() if state == "device" and not serial.startswith("emulator-")]
        serial = args.serial
        if not serial:
            if len(phones) != 1: raise ValueError("Connect one phone and authorize USB debugging, or select --serial")
            serial = phones[0]
        if devices.get(serial) != "device": raise ValueError("Selected device is not connected/authorized")
        command = [adb, "-s", serial, "install-multiple", "-r"] + [str(directory / name) for name in NAMES]
        result['phase'] = "android_install"
        operation = subprocess.run(command, capture_output=True, text=True, timeout=240)
        result.update(returncode=operation.returncode, stdout=operation.stdout, stderr=operation.stderr)
        combined = operation.stdout + operation.stderr
        match = re.search(r"(?:Failure\s*\[)?(INSTALL_[A-Z_]+)[\]: ]?", combined)
        result['android_error_code'] = match.group(1) if match else None
        result['installed'] = operation.returncode == 0 and "Success" in operation.stdout
        explanations = {
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE": "Installed official app and test build have different certificates. No app was removed. Replacement requires explicit approval because uninstalling can erase data.",
            "INSTALL_FAILED_MISSING_SPLIT": "Android rejected an incomplete split set. All four supplied APKs must be submitted together.",
            "INSTALL_FAILED_NO_MATCHING_ABIS": "This arm64 split does not match this device's supported ABI.",
            "INSTALL_FAILED_OLDER_SDK": "This APK needs Android 12/API 31 or newer.",
            "INSTALL_FAILED_INSUFFICIENT_STORAGE": "Android reported insufficient installation storage.",
        }
        if result['android_error_code'] in explanations:
            result['explanation'] = explanations[result['android_error_code']]
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        result['tool_error'] = str(error)
    finally:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(result, indent=2) + "\n")
        print(f"Installation report saved: {report}")
        if result.get('stdout'): print(result['stdout'].strip())
        if result.get('stderr'): print(result['stderr'].strip())
        if result.get('tool_error'): print(result['tool_error'])
        if result.get('explanation'): print(result['explanation'])
    return 0 if result['installed'] else 1


if __name__ == "__main__": raise SystemExit(main())
