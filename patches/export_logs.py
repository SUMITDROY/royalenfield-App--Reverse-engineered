#!/usr/bin/env python3
"""Export persistent patch logs, Android crash logs and process exit reasons."""
import argparse
import datetime
import json
import subprocess
from pathlib import Path
from check_device import find_adb


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb")
    parser.add_argument("--serial")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        adb = find_adb(args.adb)
        def run(command):
            return subprocess.run([adb] + command, capture_output=True, text=True, timeout=30)
        found = run(["devices"])
        if found.returncode: raise ValueError(found.stderr)
        devices = [line.split()[0] for line in found.stdout.splitlines()[1:]
                   if len(line.split()) >= 2 and line.split()[1] == "device"]
        serial = args.serial
        if not serial:
            phones = [device for device in devices if not device.startswith("emulator-")]
            if len(phones) != 1: raise ValueError("Connect one authorized phone or supply --serial; emulators are not selected automatically.")
            serial = phones[0]
        if serial not in devices: raise ValueError("Selected device is not authorized/connected.")
        stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
        output = args.output or Path("output/logs") / stamp
        if output.exists(): raise ValueError("Select a new output directory to preserve earlier logs.")
        output.mkdir(parents=True)
        def device(command): return run(["-s", serial] + command)
        results = {}
        remote = "/sdcard/Android/data/com.royalenfield.reprime/files/screenoff/"
        pulled = device(["pull", remote, str(output / "persistent")])
        results['persistent_logs'] = {"copied": pulled.returncode == 0,
            "detail": (pulled.stdout + pulled.stderr).strip(), "remote_path": remote}
        commands = {
            "android-logcat.txt": ["logcat", "-d", "-v", "threadtime", "REScreenOff:V", "AndroidRuntime:E", "ActivityManager:I", "*:S"],
            "process-exit-reasons.txt": ["shell", "dumpsys", "activity", "exit-info", "com.royalenfield.reprime"],
            "power-state.txt": ["shell", "dumpsys", "power"],
            "navigation-services.txt": ["shell", "dumpsys", "activity", "services", "com.royalenfield.reprime"],
        }
        for name, command in commands.items():
            result = device(command)
            (output / name).write_text(result.stdout + ("\nCOMMAND_ERROR:\n" + result.stderr if result.returncode else ""))
            results[name] = {"available": result.returncode == 0}
        results['interpretation'] = "Exceptions and exit reasons are evidence; no-snapshot/stale-image warnings do not establish an exact root cause."
        (output / "export-status.json").write_text(json.dumps(results, indent=2) + "\n")
        print(f"Logs saved to {output}")
        if pulled.returncode:
            print("Persistent log unavailable: check patched build was opened; Android logcat/exit information was saved where available.")
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        parser.exit(1, f"Log export failed: {error}\n")


if __name__ == "__main__": main()
