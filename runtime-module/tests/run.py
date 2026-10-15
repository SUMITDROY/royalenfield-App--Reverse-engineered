#!/usr/bin/env python3
import pathlib,subprocess,sys
root=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(root/'scripts'))
from build_support import java_bin
java=java_bin()
out=root/'output/bridge-protocol-tests';out.mkdir(exist_ok=True)
subprocess.run([str(java/'javac'),'-d',str(out),str(root/'runtime-module/src/local/re/screenoff/AppleProtocol.java'),str(root/'runtime-module/src/local/re/screenoff/CallActionGate.java'),str(root/'runtime-module/tests/AppleProtocolTest.java')],check=True)
subprocess.run([str(java/'java'),'-cp',str(out),'local.re.screenoff.AppleProtocolTest'],check=True)
