import json,shutil,os
P="/root/kaios-simulator/kaiosrt-v2.5-ubuntu-20190925163557-n378/kaiosrt/gaia/profile/webapps"
S="/mnt/d/ROMS/nokia-6300-ta1286-dump/analysis/system/b2g/webapps"
apps=["calculator","clock","notes","unitconverter","snake","doodlejump"]
if not os.path.exists(P+"/webapps.json.orig"): shutil.copy(P+"/webapps.json",P+"/webapps.json.orig")
w=json.load(open(P+"/webapps.json")); d=json.load(open(S+"/webapps.json"))
nid=max(v["localId"] for v in w.values())+1
tpl=w["settings.gaiamobile.org"]
for a in apps:
    k=a+".gaiamobile.org"
    os.makedirs(f"{P}/{k}",exist_ok=True)
    for f in ("application.zip","manifest.webapp"): shutil.copy(f"{S}/{k}/{f}",f"{P}/{k}/{f}")
    m=json.load(open(f"{P}/{k}/manifest.webapp",encoding="utf-8-sig"))
    e=dict(tpl); e.update(d[k])
    e.update(id=k,basePath=P,name=m.get("name",a),localId=nid,removable=True,preinstalled=False)
    e.pop("packageHash",None); nid+=1
    w[k]=e; print("added",k,"-",e["name"])
json.dump(w,open(P+"/webapps.json","w"),indent=1)
print("total",len(w))
