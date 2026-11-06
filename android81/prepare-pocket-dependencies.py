"""Verify pinned dependencies and remove Java-only metadata/MR variants for Android 27."""
from pathlib import Path
import argparse,hashlib,json,struct,zipfile
ROOT=Path(__file__).resolve().parent

def strip_parameters(data):
    pos=8
    def u2():
        nonlocal pos
        n=int.from_bytes(data[pos:pos+2],"big");pos+=2;return n
    count=u2();names={};i=1
    while i<count:
        tag=data[pos];pos+=1
        if tag==1:
            n=u2();names[i]=data[pos:pos+n].decode("utf-8",errors="replace");pos+=n
        elif tag in (3,4):pos+=4
        elif tag in (5,6):pos+=8;i+=1
        elif tag in (7,8,16,19,20):pos+=2
        elif tag in (9,10,11,12,17,18):pos+=4
        elif tag==15:pos+=3
        else:raise ValueError("Unknown class constant")
        i+=1
    pos+=6;n=u2();pos+=2*n
    out=bytearray(data[:pos])
    for kind in ("fields","methods"):
        n=u2();out.extend(n.to_bytes(2,"big"))
        for _ in range(n):
            out.extend(data[pos:pos+6]);pos+=6;attributes=u2();kept=[]
            for _ in range(attributes):
                start=pos;name=u2();size=int.from_bytes(data[pos:pos+4],"big");pos+=4+size
                if names.get(name)!="MethodParameters":kept.append(data[start:pos])
            out.extend(len(kept).to_bytes(2,"big"))
            for attribute in kept:out.extend(attribute)
    out.extend(data[pos:]);return bytes(out)

def prepare(destination):
    vendor=ROOT/"third-party/pocket";pins=json.loads((vendor/"sources.json").read_text())
    for name,meta in pins.items():
        if hashlib.sha256((vendor/name).read_bytes()).hexdigest()!=meta["sha256"]:raise ValueError("Dependency hash mismatch: "+name)
    destination=Path(destination);destination.mkdir(parents=True,exist_ok=True);count=0
    with zipfile.ZipFile(vendor/"jsch-2.28.0.jar") as source,zipfile.ZipFile(destination/"jsch.jar","w",zipfile.ZIP_DEFLATED) as target:
        for entry in source.infolist():
            if not entry.filename.endswith(".class") or entry.filename.startswith("META-INF/") or entry.filename.endswith("module-info.class"):continue
            data=source.read(entry.filename)
            if int.from_bytes(data[6:8],"big")>52:raise ValueError("Expected Java 8 root classes")
            target.writestr(entry,strip_parameters(data));count+=1
    print("Prepared",count,"Java 8 SSH classes; signatures, code and host-key checks unchanged")
    return destination/"jsch.jar"
if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--output",required=True);prepare(parser.parse_args().output)
