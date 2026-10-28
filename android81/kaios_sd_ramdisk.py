"""Prepare the embedded stock KaiOS root, with no internal OS mount fallback."""
import hashlib,stat,struct
import recovery_image

STOCK_BOOT_SHA='a23fcbcc993a0f5f1f03736eb59d7e431604d9c2d8900f100b927c06a1c81338'
REPLACEMENTS={
    '/dev/block/bootdevice/by-name/system':'/dev/block/loop0',
    '/dev/block/bootdevice/by-name/userdata':'/dev/block/loop1',
    '/dev/block/bootdevice/by-name/cache':'/dev/block/loop2',
}

def diagnostic_domains(policy):
    magic,length=struct.unpack_from('<II',policy)
    if magic!=0xf97cff8c or policy[8:8+length]!=b'SE Linux':raise ValueError('Unexpected SELinux policy')
    version,config,symbols,contexts=struct.unpack_from('<4I',policy,8+length)
    if version<23 or symbols!=8:raise ValueError('Unsupported policy layout')
    def bitmap(at):
        mapsize,high,count=struct.unpack_from('<III',policy,at);nodes={};at+=12
        if mapsize!=64 or high%64 or count>4096:raise ValueError('Invalid policy bitmap')
        for _ in range(count):
            start,bits=struct.unpack_from('<IQ',policy,at);at+=12
            if start%64 or not bits or start in nodes:raise ValueError('Invalid bitmap node')
            nodes[start]=bits
        if high!=(max(nodes)+64 if nodes else 0):raise ValueError('Invalid bitmap bound')
        return nodes,at
    _,begin=bitmap(8+length+16);nodes,end=bitmap(begin)
    # IDs read with libsepol from the exact pinned KaiOS boot policy.
    for value in (174,862):
        start=value//64*64;nodes[start]=nodes.get(start,0)|(1<<(value-start))
    encoded=struct.pack('<III',64,max(nodes)+64,len(nodes))
    encoded+=b''.join(struct.pack('<IQ',start,bits) for start,bits in sorted(nodes.items()))
    # Only the permissive bitmap changes. AV rules and all symbols are preserved.
    return policy[:begin]+encoded+policy[end:]

def patch(name,body):
    if name=='sepolicy':
        if hashlib.sha256(body).hexdigest()!='ef1a41a7085c886e5599bffc9965289e8543143d57e0c62137a08f663a7afb53':
            raise ValueError('Unrecognized KaiOS policy; do not guess domain IDs')
        return diagnostic_domains(body)
    if name.endswith(('.rc','.sh')) or name=='fstab.qcom':
        text=body.decode()
        for old,new in REPLACEMENTS.items():text=text.replace(old,new)
        if name=='fstab.qcom':
            lines=[]
            for line in text.splitlines():
                if 'voldmanaged=sdcard' in line:continue
                if '/dev/block/loop0' in line:line='/dev/block/loop0 /system ext4 ro,noload,barrier=1 wait'
                if '/dev/block/loop1' in line:line='/dev/block/loop1 /data ext4 nosuid,nodev,noatime,barrier=1,noauto_da_alloc wait'
                lines.append(line)
            text='\n'.join(lines)+'\n'
        if name=='init.b2g.rc':
            # OTA must not attempt to replace the internal Android partitions.
            text=text.replace('service updater-daemon /system/bin/updater-daemon.sh\n    class main',
                              'service updater-daemon /system/bin/updater-daemon.sh\n    disabled\n    class main')
        if name=='init.rc':
            # Match the working root-recovery adbd invocation. The rooted stock
            # defaults otherwise request a different root SELinux domain.
            # Preserve the service's existing adbd domain instead.
            text=text.replace('service adbd /sbin/adbd --root_seclabel=u:r:su:s0',
                              'service adbd /sbin/adbd')
        body=text.encode()
    if name=='default.prop':
        body=body.replace(b'persist.sys.usb.config=none',b'persist.sys.usb.config=adb')
        body+=b'\nro.leo.kaios_sd=1\n'
    return body

def embedded(stock):
    entries=recovery_image.unpack(stock)['entries'];result={}
    for name,(fields,body) in entries.items():
        body=patch(name,body)
        recovery_image.add(result,name,body,fields[1])
        result[name][0][2:4]=fields[2:4]
    for name,(fields,body) in result.items():
        if name.endswith(('.rc','.sh')) or name=='fstab.qcom':
            if any(old.encode() in body for old in REPLACEMENTS):raise ValueError('Internal OS mount remains: '+name)
    if b'formattable' in result['fstab.qcom'][1] or b'encryptable=footer' in result['fstab.qcom'][1]:
        raise ValueError('Automatic format/encryption remains')
    recovery_image.add(result,'leo-sd',b'',stat.S_IFDIR|0o700)
    return result
