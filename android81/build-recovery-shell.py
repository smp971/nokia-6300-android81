"""Append a verified static BusyBox shell to the working Nokia recovery CPIO.

Kernel, init executable, policy and properties remain unchanged. init.rc gains
restorecon_recursive /system and a recovery-only permissive diagnostic action.
Old partition tail is identified against the pre-existing 8110 image; only bytes
occupied by the expanded declared ramdisk may replace that stale tail.
"""
from pathlib import Path
import gzip, hashlib, json, struct

HERE=Path(__file__).resolve().parent
OUT=HERE/'output'
old=(HERE/'backup-live/recovery-device.img').read_bytes()
assert hashlib.sha256(old).hexdigest()==json.loads((HERE/'backup-live/checksums.json').read_text())['recovery']['sha256']
k,ka,r,ra,s,sa,tags,page,hv,osv=struct.unpack_from('<10I',old,8)
assert s==0 and hv==0 and page==2048
up=lambda n:(n+page-1)//page*page
off=page+up(k)
cpio=gzip.decompress(old[off:off+r])
names=set(); entries={}; spans={}; i=0; max_ino=0
while True:
    assert cpio[i:i+6]==b'070701'
    f=[int(cpio[i+6+8*j:i+14+8*j],16) for j in range(13)]
    n=cpio[i+110:i+110+f[11]-1].decode()
    start=(i+110+f[11]+3)&~3
    end=(start+f[6]+3)&~3
    if n=='TRAILER!!!':
        trailer_start=i; trailer=cpio[i:end]; break
    names.add(n); entries[n]=(f,cpio[start:start+f[6]])
    spans[n]=(i,end)
    max_ino=max(max_ino,f[0]); i=end
shell=(OUT/'recovery-shell-candidate').read_bytes()
assert shell[:6]==b'\x7fELF\x01\x01'
assert struct.unpack_from('<H',shell,18)[0]==40
phoff=struct.unpack_from('<I',shell,28)[0]
phsize,phnum=struct.unpack_from('<HH',shell,42)
assert all(struct.unpack_from('<I',shell,phoff+j*phsize)[0] not in (2,3) for j in range(phnum)), 'Shell must be static, without PT_DYNAMIC/PT_INTERP'
added=[]; extra=bytearray()
def append(name,body,mode):
    assert name not in names
    names.add(name)
    f=[max_ino+len(added)+1,mode,0,0,1,0,len(body),0,0,0,0,len(name.encode())+1,0]
    extra.extend(b'070701'+b''.join(f'{v:08x}'.encode() for v in f)+name.encode()+b'\0')
    extra.extend(bytes((-len(extra))%4))
    extra.extend(body); extra.extend(bytes((-len(extra))%4))
    added.append(name)
for n in ('system','system/bin'):
    if n not in names:
        append(n,b'',0o40755)
# Stock /sbin is root:root 0750; recovery adbd runs as uid 2000.
# Keep its permissions unchanged and put the shell under traversable /system/bin.
append('system/bin/sh',shell,0o100755)
for name in ('busybox','id','cat','ls','uname','dmesg','ps','grep','head','tail','md5sum'):
    append('system/bin/'+name,b'/system/bin/sh',0o120777)
# A symlink named sh does not label its BusyBox target as shell_exec.
# Use a regular sh and ask stock init to apply stock file_contexts before adbd.
rc=entries['init.rc'][1]+b'\n# Recovery-only diagnostic: distinguish SELinux denial from exec failure.\non init\n    restorecon_recursive /system\n    write /sys/fs/selinux/enforce 0\n'
a,b=spans['init.rc']; f=entries['init.rc'][0].copy(); f[6]=len(rc)
replacement=b'070701'+b''.join(f'{v:08x}'.encode() for v in f)+b'init.rc\0'
replacement+=bytes((-len(replacement))%4)
replacement+=rc; replacement+=bytes((-len(replacement))%4)
prefix=cpio[:a]+replacement+cpio[b:trailer_start]
new_cpio=prefix+extra+trailer
new_cpio+=bytes((-len(new_cpio))%512)
assert new_cpio[:a]==cpio[:a]
packed=gzip.compress(new_cpio,9,mtime=1)
end=off+up(len(packed)); old_end=off+up(r)
assert end<=len(old)
stale=Path('D:/ROMS/edl-tools/recovery-8110.img').read_bytes()
assert old[old_end:len(stale)]==stale[old_end:] and not any(old[len(stale):])
header=bytearray(old[:page]); struct.pack_into('<I',header,16,len(packed))
sha=hashlib.sha1()
for payload in (old[page:page+k],packed,b''):
    sha.update(payload); sha.update(struct.pack('<I',len(payload)))
header[576:596]=sha.digest()
image=bytearray(old); image[:page]=header
image[off:max(end,old_end)]=bytes(max(end,old_end)-off)
image[off:off+len(packed)]=packed
assert image[page:page+k]==old[page:page+k]
assert image[max(end,old_end):]==old[max(end,old_end):]
image=bytes(image)
(OUT/'recovery-shell.img').write_bytes(image)
report={'bytes':len(image),'md5':hashlib.md5(image).hexdigest(),'sha256':hashlib.sha256(image).hexdigest(),
        'kernel_unchanged':True,'init_executable_unchanged':True,
        'existing_cpio_entries_changed':['init.rc'],
        'selinux_policy_unchanged':True,'stock_properties_unchanged':True,
        'init_rc_added_action':'on init: restorecon_recursive /system; write /sys/fs/selinux/enforce 0',
        'recovery_only_permissive_diagnostic':True,
        'existing_cpio_prefix_sha256':hashlib.sha256(cpio[:trailer_start]).hexdigest(),
        'static_shell_sha256':hashlib.sha256(shell).hexdigest(),'added_paths':added,
        'gzip_old_bytes':r,'gzip_new_bytes':len(packed),
        'stale_tail_identified_as_8110_image':True,'tail_preserved_after_new_ramdisk':True,
        'system_partition_written':False,'status':'offline verified; static shell executed successfully under stock KaiOS; recovery boot untested'}
(OUT/'recovery-shell.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
