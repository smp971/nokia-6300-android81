import json,shutil,os,zipfile
P="/root/kaios-simulator/kaiosrt-v2.5-ubuntu-20190925163557-n378/kaiosrt/gaia/profile/webapps"
S="/mnt/d/ROMS/nokia-6300-ta1286-dump/analysis/system/b2g/webapps"
skip={"launcher","system","settings","keyboard","wallpaper","default_theme"}
if not os.path.exists(P+"/webapps.json.6apps"): shutil.copy(P+"/webapps.json",P+"/webapps.json.6apps")
w=json.load(open(P+"/webapps.json")); d=json.load(open(S+"/webapps.json"))
nid=max(v["localId"] for v in w.values())+1
tpl=w["settings.gaiamobile.org"]
added=0
for k,src in d.items():
    base=k.replace(".gaiamobile.org","")
    if base in skip or k in w: continue
    sd=f"{S}/{k}"
    if not os.path.exists(sd+"/application.zip"): print("skip (no package):",k); continue
    os.makedirs(f"{P}/{k}",exist_ok=True)
    for f in os.listdir(sd): shutil.copy(f"{sd}/{f}",f"{P}/{k}/{f}")
    name=k
    try:
        with zipfile.ZipFile(sd+"/application.zip") as z:
            m=json.loads(z.read("manifest.webapp").decode("utf-8-sig")); name=m.get("name",k)
    except Exception as ex:
        try: name=json.load(open(sd+"/manifest.webapp",encoding="utf-8-sig")).get("name",k)
        except Exception: pass
    e=dict(tpl); e.update(src)
    e.update(id=k,basePath=P,name=name,localId=nid,removable=True,preinstalled=False)
    e.pop("packageHash",None); nid+=1; w[k]=e; added+=1
json.dump(w,open(P+"/webapps.json","w"),indent=1)
print("added",added,"total",len(w))
