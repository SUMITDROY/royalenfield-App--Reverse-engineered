#!/usr/bin/env python3
"""Prepare a version-pinned, experimental screen-off lifecycle patch.

Run against a fresh APKTool decode of the supplied Royal Enfield base APK.
This does not install an app or claim that background rendering works.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

APK_SHA256 = "d08478e8c564624503b440834a40499cd4acdc1c8572e49fe1b4830472ad37a4"
NAV = "Lcom/royalenfield/reprime/ui/home/navigation/fragment/NavigationFragment;"
HELPER = "Llocal/re/screenoff/ScreenOffMode;"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("decoded", type=Path)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--apply", action="store_true", help="Otherwise validate without writing")
    args = parser.parse_args()
    with args.apk.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != APK_SHA256:
            parser.error("APK identity differs; this patch is only for version 10.1.23(45), code 193")
    root = args.decoded
    changes = {}

    def path_for(relative):
        matches = list(root.glob("smali*/" + relative))
        if len(matches) != 1:
            raise ValueError(f"Expected one class {relative}, found {len(matches)}")
        return matches[0]

    def change(path, transform):
        before = changes.get(path, (path.read_text(), None))[0]
        current = changes.get(path, (before, before))[1]
        after = transform(current)
        if after == current:
            raise ValueError(f"Patch made no change to {path}")
        changes[path] = before, after

    def modify_method(text, signature, transform):
        pattern = re.compile(r"(^\.method " + re.escape(signature) + r"\n.*?^\.end method)", re.M | re.S)
        matches = list(pattern.finditer(text))
        if len(matches) != 1:
            raise ValueError(f"Missing or ambiguous method {signature}")
        match = matches[0]
        return text[:match.start()] + transform(match.group()) + text[match.end():]

    def once(text, old, new):
        if text.count(old) != 1:
            raise ValueError(f"Expected one anchor: {old!r}, found {text.count(old)}")
        return text.replace(old, new)

    def guard(text, signature, superclass, extra=""):
        def insert(method):
            # Existing methods use v0 after this insertion; locals are preserved.
            anchor = f"    invoke-super {{p0}}, {superclass}->" + signature.split()[-1]
            fragment = f"""

    invoke-virtual {{p0}}, Landroidx/fragment/app/Fragment;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-static {{v0}}, {HELPER}->retain(Landroid/content/Context;)Z
    move-result v0
    if-eqz v0, :screenoff_original
{extra}
    return-void
    :screenoff_original
"""
            return once(method, anchor, anchor + fragment)
        return modify_method(text, signature, insert)

    nav = path_for("com/royalenfield/reprime/ui/home/navigation/fragment/NavigationFragment.smali")
    # Keep normal Home/back behavior; bypass stop work only with an active
    # encoder, a Tripper Dash connection, and a non-interactive display.
    change(nav, lambda t: guard(t, "public onPause()V", "Landroidx/fragment/app/Fragment;"))

    def stop_nav(text):
        def insert(method):
            anchor = "    .locals 1"
            code = f"""

    invoke-virtual {{p0}}, Landroidx/fragment/app/Fragment;->getContext()Landroid/content/Context;
    move-result-object v0
    invoke-static {{v0}}, {HELPER}->retain(Landroid/content/Context;)Z
    move-result v0
    if-eqz v0, :screenoff_original
    invoke-super {{p0}}, Lcom/royalenfield/reprime/ui/home/navigation/fragment/a;->onStop()V
    return-void
    :screenoff_original
"""
            return once(method, anchor, anchor + code)
        return modify_method(text, "public onStop()V", insert)
    change(nav, stop_nav)

    def flags(text):
        def insert(method):
            method = once(method, "const v1, 0x3000001a", "const/4 v1, 0x1")
            return once(method, "const v1, 0x680080", "const/4 v1, 0x0")
        return modify_method(text, "public onCreate(Landroid/os/Bundle;)V", insert)
    change(nav, flags)

    root_fragment = path_for("com/royalenfield/reprime/ui/home/navigation/fragment/NavigationRootFragment.smali")
    change(root_fragment, lambda t: guard(t, "public onPause()V", "Landroidx/fragment/app/Fragment;"))

    encoder = path_for("bluconnect/w9f.smali")
    def encoder_flags(text):
        def start(method):
            anchor = "    invoke-virtual {p0}, Lbluconnect/gub;->i()V"
            return once(method, anchor, anchor + f"\n\n    invoke-static {{}}, {HELPER}->begin()V")
        text = modify_method(text, "public final d()V", start)
        def stop(method):
            return once(method, "    .locals 0", "    .locals 0" + f"\n\n    invoke-static {{}}, {HELPER}->end()V")
        return modify_method(text, "public final e()V", stop)
    change(encoder, encoder_flags)

    sdk = path_for("com/google/android/libraries/navigation/SupportNavigationFragment.smali")
    def sdk_lifecycle(text):
        first_method = text.index(".method ")
        text = text[:first_method] + ".field private screenoffPaused:Z\n.field private screenoffStopped:Z\n\n" + text[first_method:]
        for callback, field in [("onPause", "screenoffPaused"), ("onStop", "screenoffStopped")]:
            text = guard(text, f"public {callback}()V", "Landroidx/fragment/app/Fragment;", f"    const/4 v0, 0x1\n    iput-boolean v0, p0, Lcom/google/android/libraries/navigation/SupportNavigationFragment;->{field}:Z")
        # Pair retained callbacks, avoiding double resume/start of an SDK view
        # that never received the corresponding pause/stop.
        for callback, field in [("onResume", "screenoffPaused"), ("onStart", "screenoffStopped")]:
            def insert(method, callback=callback, field=field):
                anchor = f"    invoke-super {{p0}}, Landroidx/fragment/app/Fragment;->{callback}()V"
                code = f"""

    iget-boolean v0, p0, Lcom/google/android/libraries/navigation/SupportNavigationFragment;->{field}:Z
    if-eqz v0, :screenoff_original
    const/4 v0, 0x0
    iput-boolean v0, p0, Lcom/google/android/libraries/navigation/SupportNavigationFragment;->{field}:Z
    return-void
    :screenoff_original
"""
                return once(method, anchor, anchor + code)
            text = modify_method(text, f"public {callback}()V", insert)
        return text
    change(sdk, sdk_lifecycle)

    # Use the same DEX directory as NavigationFragment, avoiding a new split.
    helper_path = next(parent for parent in nav.parents if parent.name.startswith("smali")) / "local/re/screenoff/ScreenOffMode.smali"
    if helper_path.exists():
        raise ValueError("Helper already exists; use a fresh decode")
    helper = f""".class public final {HELPER}
.super Ljava/lang/Object;
.field private static volatile active:Z

.method public static begin()V
    .locals 1
    const/4 v0, 0x1
    sput-boolean v0, {HELPER}->active:Z
    return-void
.end method

.method public static end()V
    .locals 1
    const/4 v0, 0x0
    sput-boolean v0, {HELPER}->active:Z
    return-void
.end method

.method public static retain(Landroid/content/Context;)Z
    .locals 2
    if-eqz p0, :disabled
    sget-boolean v0, {HELPER}->active:Z
    if-eqz v0, :disabled
    invoke-static {{}}, Lbluconnect/zif;->y()Lbluconnect/zif;
    move-result-object v0
    invoke-virtual {{v0}}, Lbluconnect/zif;->o()Lbluconnect/zif$b;
    move-result-object v0
    sget-object v1, Lbluconnect/zif$b;->o2:Lbluconnect/zif$b;
    if-ne v0, v1, :disabled
    const-string v0, "power"
    invoke-virtual {{p0, v0}}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/os/PowerManager;
    if-eqz v0, :disabled
    invoke-virtual {{v0}}, Landroid/os/PowerManager;->isInteractive()Z
    move-result v0
    if-nez v0, :disabled
    const-string v0, "REScreenOff"
    const-string v1, "Retaining active Tripper Dash map lifecycle while display is off"
    invoke-static {{v0, v1}}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    const/4 v0, 0x1
    return v0
    :disabled
    const/4 v0, 0x0
    return v0
.end method
"""
    ledger = [{"path": str(p.relative_to(root)), "before_sha256": hashlib.sha256(before.encode()).hexdigest(), "after_sha256": hashlib.sha256(after.encode()).hexdigest()} for p, (before, after) in changes.items()]
    if args.apply:
        for path, (_, after) in changes.items():
            path.write_text(after)
        helper_path.parent.mkdir(parents=True, exist_ok=True)
        helper_path.write_text(helper)
    print(json.dumps({"experimental": True, "applied": args.apply, "apk_sha256": APK_SHA256, "changes": ledger, "helper": str(helper_path.relative_to(root))}, indent=2))


if __name__ == "__main__":
    main()
