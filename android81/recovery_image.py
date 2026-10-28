"""Strict, side-effect-free legacy Android boot/newc image helpers."""
import gzip, hashlib, stat, struct

def unpack(image):
    if len(image)!=33554432 or image[:8]!=b'ANDROID!':raise ValueError('Unexpected recovery partition/header')
    kernel,ramdisk,second,page=(struct.unpack_from('<I',image,at)[0] for at in (8,16,24,36))
    if page!=2048 or not kernel or not ramdisk or second:raise ValueError('Unsupported recovery geometry')
    offset=page+(kernel+page-1)//page*page
    if offset+ramdisk>len(image):raise ValueError('Ramdisk outside image')
    archive=gzip.decompress(image[offset:offset+ramdisk]);entries={};at=0
    while True:
        if archive[at:at+6]!=b'070701':raise ValueError('Unsupported CPIO archive')
        fields=[int(archive[at+6+8*i:at+14+8*i],16) for i in range(13)]
        size=fields[11]
        if size<1 or size>4096:raise ValueError('Invalid CPIO name size')
        raw=archive[at+110:at+110+size]
        if len(raw)!=size or raw[-1]!=0:raise ValueError('Truncated CPIO name')
        name=raw[:-1].decode('utf-8');start=(at+110+size+3)&~3
        end=start+fields[6]
        if end>len(archive):raise ValueError('Truncated CPIO body')
        if name=='TRAILER!!!':break
        if name.startswith('/') or '..' in name.split('/') or name in entries:raise ValueError('Unsafe/duplicate CPIO path')
        entries[name]=(fields,archive[start:end]);at=(end+3)&~3
    return {'kernel':kernel,'ramdisk':ramdisk,'page':page,'offset':offset,'entries':entries}

def cpio(entries):
    result=bytearray();ino=max((fields[0] for fields,_ in entries.values()),default=0)+1
    all_entries=list(entries.items())+[('TRAILER!!!',([ino,0,0,0,1,0,0,0,0,0,0,11,0],b''))]
    for name,(fields,body) in all_entries:
        fields=fields.copy();raw=name.encode()+b'\0';fields[6]=len(body);fields[11]=len(raw)
        result+=b'070701'+b''.join(f'{value:08x}'.encode() for value in fields)+raw
        result+=bytes((-len(result))%4);result+=body;result+=bytes((-len(result))%4)
    result+=bytes((-len(result))%512);return bytes(result)

def add(entries,name,body,mode):
    if name in entries:raise ValueError('Refusing duplicate addition: '+name)
    ino=max((fields[0] for fields,_ in entries.values()),default=0)+1
    fields=[ino,mode,0,0,1,1,len(body),0,0,0,0,len(name.encode())+1,0]
    entries[name]=(fields,body)

def repack(base,entries,known_tail):
    old=unpack(base);page=old['page'];offset=old['offset']
    packed=gzip.compress(cpio(entries),compresslevel=9,mtime=1)
    align=lambda size:(size+page-1)//page*page
    old_end=offset+align(old['ramdisk']);new_end=offset+align(len(packed))
    if max(old_end,new_end)>=len(base):raise ValueError('Recovery payload exceeds partition')
    if new_end>old_end:
        # Previous bounded repacks may have cleared part of the stale tail.
        # Zero bytes are free; EVERY displaced nonzero byte must still match
        # the independently identified old 8110 residue at its exact offset.
        for pos in range(old_end,new_end):
            if base[pos] and (pos>=len(known_tail) or base[pos]!=known_tail[pos]):
                raise ValueError('Expansion would overwrite an unidentified tail at '+str(pos))
    result=bytearray(base);result[offset:max(old_end,new_end)]=bytes(max(old_end,new_end)-offset)
    result[offset:offset+len(packed)]=packed;struct.pack_into('<I',result,16,len(packed))
    digest=hashlib.sha1()
    for body in (base[page:page+old['kernel']],packed,b''):
        digest.update(body);digest.update(struct.pack('<I',len(body)))
    result[576:596]=digest.digest();result=bytes(result)
    check=unpack(result)
    if check['entries']!=entries:raise ValueError('CPIO readback mismatch')
    if result[page:page+old['kernel']]!=base[page:page+old['kernel']]:raise ValueError('Kernel changed')
    if result[max(old_end,new_end):]!=base[max(old_end,new_end):]:raise ValueError('Unoccupied tail changed')
    return result
