"""Build recovery-only root diagnostics from verified stock backups and shell V4."""
from pathlib import Path
import gzip, hashlib, json, struct, subprocess

HERE=Path(__file__).resolve().parent
OUT=HERE/'output'
saved=json.loads((HERE/'backup-live/checksums.json').read_text())
def sha(b): return hashlib.sha256(b).hexdigest()
def unpack(b):
    assert b[:8]==b'ANDROID!'
    k,_,r,_,s,_,_,page=struct.unpack_from('<8I',b,8)
    assert page==2048 and s==0
    off=page+(k+page-1)//page*page
    c=gzip.decompress(b[off:off+r]); entries={}; i=0
    while True:
        assert c[i:i+6]==b'070701'
        f=[int(c[i+6+8*j:i+14+8*j],16) for j in range(13)]
        n=c[i+110:i+109+f[11]].decode(); start=(i+110+f[11]+3)&~3
        end=(start+f[6]+3)&~3
        if n=='TRAILER!!!': return k,r,page,off,c,entries,i,end
        assert n not in entries
        entries[n]=(f,c[start:start+f[6]],i,end)
        i=end

boot=(HERE/'backup-live/boot-device.img').read_bytes()
stock=(HERE/'backup-live/recovery-device.img').read_bytes()
assert sha(boot)==saved['boot']['sha256'] and sha(stock)==saved['recovery']['sha256']
be=unpack(boot)[5]; se=unpack(stock)[5]
source=(OUT/'recovery-shell.img').read_bytes()
assert sha(source)=='ce3fb2788ed592f322560c41469ef4b8322b2302c3433c65751cac723b90679e'
k,r,page,off,c,e,trail,trailend=unpack(source)
adbd=be['sbin/adbd'][1]
assert sha(adbd)=='a44268d77f41d332e1e4fd69bbf345761210f701936c891eee589f849c12addd'
assert adbd[:6]==b'\x7fELF\x01\x01' and struct.unpack_from('<H',adbd,18)[0]==40
phoff=struct.unpack_from('<I',adbd,28)[0]; phsize,phnum=struct.unpack_from('<HH',adbd,42)
assert all(struct.unpack_from('<I',adbd,phoff+j*phsize)[0] not in (2,3) for j in range(phnum))
(OUT/'recovery-debug-stock-policy').write_bytes(se['sepolicy'][1])
def wslpath(p): return '/mnt/'+p.drive[0].lower()+p.as_posix()[2:]
result=subprocess.run(['wsl','-d','kaios','--','/root/android81-leo/recovery-debug-policy',
                wslpath(OUT/'recovery-debug-stock-policy')],check=True,capture_output=True,text=True)
ids={name:int(value) for name,value in (line.split() for line in result.stdout.splitlines())}
assert set(ids)=={'adbd','shell'}
policy=se['sepolicy'][1]
magic,slen=struct.unpack_from('<II',policy)
assert magic==0xf97cff8c and policy[8:8+slen]==b'SE Linux'
version,config,symnum,oconnum=struct.unpack_from('<4I',policy,8+slen)
assert version>=23 and symnum==8
def bitmap(at):
    mapsize,high,count=struct.unpack_from('<III',policy,at)
    assert mapsize==64 and high%64==0
    nodes={}; pos=at+12
    for _ in range(count):
        start,bits=struct.unpack_from('<IQ',policy,pos); pos+=12
        assert start%64==0 and bits and start not in nodes
        nodes[start]=bits
    assert high==(max(nodes)+64 if nodes else 0)
    return nodes,pos
_,begin=bitmap(8+slen+16) # policy capabilities precede permissive_map.
nodes,finish=bitmap(begin); previous=nodes.copy()
for value in ids.values():
    start=value//64*64; nodes[start]=nodes.get(start,0)|(1<<(value-start))
encoded=struct.pack('<III',64,max(nodes)+64,len(nodes))
encoded+=b''.join(struct.pack('<IQ',start,bits) for start,bits in sorted(nodes.items()))
patched_policy=policy[:begin]+encoded+policy[finish:]
assert patched_policy[:begin]==policy[:begin] and patched_policy[begin+len(encoded):]==policy[finish:]
for start,bits in previous.items(): assert nodes[start]&bits==bits
(OUT/'recovery-debug-policy').write_bytes(patched_policy)
print('Vendor policy rule bytes preserved exactly; diagnostic domain IDs:',ids)
props=se['default.prop'][1]
assert props.count(b'ro.secure=1\n')==1 and props.count(b'ro.debuggable=0\n')==1
props=props.replace(b'ro.secure=1\n',b'ro.secure=0\n').replace(b'ro.debuggable=0\n',b'ro.debuggable=1\n')
rc=se['init.rc'][1]
command=b'service adbd /sbin/adbd --root_seclabel=u:r:su:s0 --device_banner=recovery'
assert rc.count(command)==1
rc=rc.replace(command,b'service adbd /sbin/adbd')
rc+=b'\n# Diagnostic marker and stock labels; no global permissive switch.\non init\n    restorecon_recursive /system\n    write /tmp/leo-debug-init root-adb-v1\n    chmod 0644 /tmp/leo-debug-init\n'
changes={'sbin/adbd':adbd,'default.prop':props,'sepolicy':(OUT/'recovery-debug-policy').read_bytes(),'init.rc':rc}
new=bytearray()
for n,(f,body,a,b) in e.items():
    if n not in changes:
        new.extend(c[a:b]); continue
    body=changes[n]; f=f.copy(); f[6]=len(body)
    new.extend(b'070701'+b''.join(f'{v:08x}'.encode() for v in f)+n.encode()+b'\0')
    new.extend(bytes((-len(new))%4)); new.extend(body); new.extend(bytes((-len(new))%4))
new.extend(c[trail:trailend]); new.extend(bytes((-len(new))%512))
packed=gzip.compress(bytes(new),9,mtime=1)
up=lambda x:(x+page-1)//page*page
end=off+up(len(packed)); assert end<len(source)
image=bytearray(source); image[off:max(end,off+up(r))]=bytes(max(end,off+up(r))-off)
image[off:off+len(packed)]=packed; struct.pack_into('<I',image,16,len(packed))
h=hashlib.sha1()
for b in (source[page:page+k],packed,b''):
    h.update(b);h.update(struct.pack('<I',len(b)))
image[576:596]=h.digest(); image=bytes(image)
assert image[page:page+k]==stock[page:page+k]
assert image[max(end,off+up(r)):]==source[max(end,off+up(r)):]
check=unpack(image)[5]
for n,(f,b,a,z) in e.items():
    if n not in changes: assert check[n][:2]==(f,b),n
    else: assert check[n][1]==changes[n],n
assert len(image)==33554432
(OUT/'recovery-debug.img').write_bytes(image)
report={'bytes':len(image),'md5':hashlib.md5(image).hexdigest(),'sha256':sha(image),
        'source_shell_sha256':sha(source),'root_adbd_sha256':sha(adbd),
        'root_adbd_source':'verified backup of working boot partition; static ARM ELF',
        'changed_from_shell_v4':list(changes),'permissive_domains_added':ids,
        'policy_rule_bytes_verified_unchanged':True,'policy_version':version,
        'global_selinux_disable':False,'ro.secure':'0','ro.debuggable':'1',
        'kernel_unchanged':True,'init_executable_unchanged':True,
        'other_cpio_entries_verified_unchanged':True,'system_written':False,
        'status':'offline verified; hardware boot pending'}
(OUT/'recovery-debug.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
