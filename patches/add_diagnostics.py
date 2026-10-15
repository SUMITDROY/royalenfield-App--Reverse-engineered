#!/usr/bin/env python3
"""Add logging to a tree already patched by prepare_screen_off.py.

Compile Diagnostics.java with Android's javac/D8 and decode that small DEX
with APKTool first; pass the decoded smali directory as the second argument.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

D = "Llocal/re/screenoff/Diagnostics;"
H = "Llocal/re/screenoff/ScreenOffMode;"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tree", type=Path)
    parser.add_argument("logger_smali", type=Path)
    args = parser.parse_args()
    root = args.tree
    changes = {}
    def locate(relative):
        paths = list(root.glob("smali*/" + relative))
        if len(paths) != 1: raise ValueError("Missing or ambiguous " + relative)
        return paths[0]
    def once(text, old, new):
        if text.count(old) != 1: raise ValueError("Missing or ambiguous anchor " + old)
        return text.replace(old, new)
    def change(path, transform):
        original = path.read_text()
        changes[path] = (original, transform(original))
    def method(text, signature, transform):
        pattern = re.compile(r"(^\.method " + re.escape(signature) + r"\n.*?^\.end method)", re.M | re.S)
        if len(pattern.findall(text)) != 1: raise ValueError("Missing method " + signature)
        return pattern.sub(lambda match: transform(match.group()), text)
    def lifecycle(text, callback, superclass):
        def inject(body):
            count = re.search(r"\.locals (\d+)", body)
            if int(count.group(1)) < 2:
                body = once(body, count.group(), ".locals 2")
            anchor = f"    invoke-super {{p0}}, {superclass}->" + callback + "()V"
            code = f"\n    invoke-virtual {{p0}}, Landroidx/fragment/app/Fragment;->getContext()Landroid/content/Context;\n    move-result-object v0\n    const-string v1, \"{callback}\"\n    invoke-static {{v0, v1}}, {D}->event(Landroid/content/Context;Ljava/lang/String;)V\n"
            return once(body, anchor, anchor + code)
        return method(text, "public " + callback + "()V", inject)

    app = locate("com/royalenfield/reprime/RoyalEnfieldApp.smali")
    change(app, lambda text: method(text, "public onCreate()V", lambda body: once(body,
        "    invoke-super {p0}, Lbluconnect/ch8;->onCreate()V",
        "    invoke-super {p0}, Lbluconnect/ch8;->onCreate()V\n\n    invoke-static {p0}, " + D + "->init(Landroid/content/Context;)V")))
    nav = locate("com/royalenfield/reprime/ui/home/navigation/fragment/NavigationFragment.smali")
    def navigation(text):
        for callback in ("onPause", "onResume", "onDestroy"):
            text = lifecycle(text, callback, "Landroidx/fragment/app/Fragment;")
        # onStop has two superclass sites (retained and normal). Log at method entry.
        def stop(body):
            return once(body, "    .locals 1", f"    .locals 2\n\n    invoke-virtual {{p0}}, Landroidx/fragment/app/Fragment;->getContext()Landroid/content/Context;\n    move-result-object v0\n    const-string v1, \"onStop\"\n    invoke-static {{v0, v1}}, {D}->event(Landroid/content/Context;Ljava/lang/String;)V")
        text = method(text, "public onStop()V", stop)
        return method(text, "public r(Landroid/graphics/Bitmap;)V", lambda body: once(body,
            "    if-eqz p1, :cond_0", f"    invoke-static {{p1}}, {D}->snapshot(Landroid/graphics/Bitmap;)V\n\n    if-eqz p1, :cond_0"))
    change(nav, navigation)
    encoder = locate("bluconnect/w9f.smali")
    def encode(text):
        def submit(body):
            anchor = "    invoke-virtual {p0, p1, v0}, Lbluconnect/gub;->g(Landroid/graphics/Bitmap;Ljava/lang/String;)V"
            body = once(body, anchor, anchor + f"\n\n    invoke-static {{}}, {D}->frameSubmitted()V")
            return once(body, "    move-exception p0", f"    move-exception p0\n\n    const-string v0, \"ENCODER_SUBMIT_FAILED\"\n    invoke-static {{v0, p0}}, {D}->error(Ljava/lang/String;Ljava/lang/Throwable;)V")
        text = method(text, "public final c(Landroid/graphics/Bitmap;)V", submit)
        def start(body):
            body = once(body, "    .locals 0", "    .locals 1")
            return once(body, "    move-exception p0", f"    move-exception p0\n\n    const-string v0, \"ENCODER_START_FAILED\"\n    invoke-static {{v0, p0}}, {D}->error(Ljava/lang/String;Ljava/lang/Throwable;)V")
        text = method(text, "public final d()V", start)
        def stop(body):
            body = once(body, "    .locals 0", "    .locals 1")
            return body.replace("    move-exception p0", f"    move-exception p0\n\n    const-string v0, \"ENCODER_STOP_FAILED\"\n    invoke-static {{v0, p0}}, {D}->error(Ljava/lang/String;Ljava/lang/Throwable;)V")
        return method(text, "public final e()V", stop)
    change(encoder, encode)
    helper = locate("local/re/screenoff/ScreenOffMode.smali")
    def helper_code(text):
        for name in ("begin", "end"):
            text = method(text, f"public static {name}()V", lambda body, name=name: once(body,
                "    return-void", f"    invoke-static {{}}, {D}->{name}()V\n    return-void"))
        retain = f""".method public static retain(Landroid/content/Context;)Z
    .locals 3
    const-string v2, "NO_CONTEXT"
    if-eqz p0, :disabled
    const-string v2, "ENCODER_NOT_STARTED"
    sget-boolean v0, {H}->active:Z
    if-eqz v0, :disabled
    const-string v2, "DEVICE_NOT_TRIPPER_DASH"
    invoke-static {{}}, Lbluconnect/zif;->y()Lbluconnect/zif;
    move-result-object v0
    invoke-virtual {{v0}}, Lbluconnect/zif;->o()Lbluconnect/zif$b;
    move-result-object v0
    sget-object v1, Lbluconnect/zif$b;->o2:Lbluconnect/zif$b;
    if-ne v0, v1, :disabled
    const-string v2, "POWER_SERVICE_UNAVAILABLE"
    const-string v0, "power"
    invoke-virtual {{p0, v0}}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/os/PowerManager;
    if-eqz v0, :disabled
    const-string v2, "DISPLAY_STILL_INTERACTIVE"
    invoke-virtual {{v0}}, Landroid/os/PowerManager;->isInteractive()Z
    move-result v0
    if-nez v0, :disabled
    const-string v2, "RETAINED_WHILE_SCREEN_OFF"
    invoke-static {{p0, v2}}, {D}->decision(Landroid/content/Context;Ljava/lang/String;)V
    const/4 v0, 0x1
    return v0
    :disabled
    invoke-static {{p0, v2}}, {D}->decision(Landroid/content/Context;Ljava/lang/String;)V
    const/4 v0, 0x0
    return v0
.end method"""
        return method(text, "public static retain(Landroid/content/Context;)Z", lambda body: retain)
    change(helper, helper_code)
    logger_files = list(args.logger_smali.glob("local/re/screenoff/Diagnostics*.smali"))
    if len(logger_files) < 3: raise ValueError("Compiled diagnostics and callback classes required")
    # Existing DEX files are close to Android's reference limit. Put the
    # diagnostics implementation into its own DEX rather than overflowing them.
    dest = root / "smali_classes11/local/re/screenoff"
    if any((dest / file.name).exists() for file in logger_files): raise ValueError("Diagnostics already installed")
    # Validate all anchors before modifying the tree.
    for path, (_, after) in changes.items(): path.write_text(after)
    dest.mkdir(parents=True, exist_ok=True)
    for file in logger_files: (dest / file.name).write_bytes(file.read_bytes())
    # Free reference capacity in classes8.dex as well: the encoder wrapper
    # and helper retain their class names but live in the diagnostics DEX.
    moves = [helper, encoder] + list(encoder.parent.glob("w9f$*.smali"))
    relocations = []
    for file in moves:
        dex_root = next(parent for parent in file.parents if parent.name.startswith("smali"))
        target = root / "smali_classes11" / file.relative_to(dex_root)
        target.parent.mkdir(parents=True, exist_ok=True)
        file.rename(target)
        relocations.append({"from": str(file.relative_to(root)), "to": str(target.relative_to(root))})
    ledger = {"build": "screenoff-logs-v1", "changes": [{"path": str(path.relative_to(root)),
        "before_sha256": hashlib.sha256(before.encode()).hexdigest(),
        "after_sha256": hashlib.sha256(after.encode()).hexdigest()} for path, (before, after) in changes.items()],
        "logger_dex": "classes11.dex", "relocations": relocations, "logger_classes": [file.name for file in logger_files]}
    print(json.dumps(ledger, indent=2))


if __name__ == "__main__": main()
