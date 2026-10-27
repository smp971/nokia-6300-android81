"""Offline clean-install adaptation of an already verified Leo system copy.

No ADB, userdata extraction, credentials, or device writes. Called by the ROM
builder only with --fresh. The current phone keeps its paired APK/runtime.
"""
from pathlib import Path
import hashlib, re

ROOT=Path(__file__).resolve().parent
DEFAULTS={
    'persist.leo.adreno.cpu_wait':'0','persist.leo.adreno.hwui_finish':'1',
    'persist.leo.adreno.validated':'1','persist.leo.audio.autoload':'1',
    'persist.leo.audio.enabled':'1','persist.leo.audio.validated':'1',
    'persist.leo.camera.enabled':'1','persist.leo.connectivity.auto':'1',
    'persist.leo.connectivity.validated':'1','persist.leo.data.enabled':'1',
    'persist.leo.gps.enabled':'1','persist.leo.home':'visual',
    'persist.leo.ims.enabled':'1','persist.leo.keypad_ui':'1','persist.leo.radio.trace':'0'}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def verify_metadata(stat,mode):
    found=re.search(r'Type:\s+regular\s+Mode:\s+(0[0-7]+)',stat)
    if not found or int(found[1],8)!=mode:
        raise RuntimeError('Incorrect system inode mode')
    if not re.search(r'User:\s+0\s+Group:\s+0\s',stat):
        raise RuntimeError('Incorrect system inode owner')
    if 'u:object_r:system_file:s0' not in stat:
        raise RuntimeError('Incorrect system SELinux label')

def prepare(system,dest,linux,run):
    image=linux(system);work=dest/'clean-system';work.mkdir()
    context=work/'file-context';context.write_bytes(b'u:object_r:system_file:s0\0')
    records=[]
    def dump(target,name):
        local=work/name
        run('debugfs','-R','dump '+target+' "'+linux(local)+'"',image)
        if not local.is_file():raise RuntimeError('Missing source system file: '+target)
        return local
    def write(target,source,mode=0o644):
        if not source.is_file():raise RuntimeError('Build runtime first: '+str(source))
        commands=[];parent=Path(target).parent.as_posix();directories=[]
        while parent!='/':directories.append(parent);parent=Path(parent).parent.as_posix()
        for directory in reversed(directories):
            commands+=['mkdir '+directory,'set_inode_field '+directory+' mode 040755',
                       'ea_set -f "'+linux(context)+'" '+directory+' security.selinux']
        commands+=['rm '+target,'write "'+linux(source)+'" '+target,
                   'set_inode_field '+target+' mode 0'+format(0o100000|mode,'o'),
                   'set_inode_field '+target+' uid 0','set_inode_field '+target+' gid 0',
                   'ea_set -f "'+linux(context)+'" '+target+' security.selinux']
        recipe=work/'write.debugfs';recipe.write_text('\n'.join(commands)+'\n',newline='\n')
        run('debugfs','-w','-f',linux(recipe),image)
        readback=work/'readback';readback.unlink(missing_ok=True)
        run('debugfs','-R','dump '+target+' "'+linux(readback)+'"',image)
        if not readback.is_file() or digest(readback)!=digest(source):
            raise RuntimeError('Clean runtime readback failed: '+target)
        verify_metadata(run('debugfs','-R','stat '+target,image),mode)
        records.append({'destination':'/system'+target,'bytes':source.stat().st_size,
                        'sha256':digest(source),'mode':oct(mode)})
    # Updated APKs become factory packages. The IME used to be a user-only app.
    write('/priv-app/LeoVisual/LeoVisual.apk',ROOT/'output/leo-visual.apk')
    ime=ROOT/'output/leo-keypad-ime.apk'
    if digest(ime)!='c1a064b02dd49e6201330ec43b328e439594a35135793ec2ce21c673f1f6c554':
        raise RuntimeError('Unexpected IME build; audit and update its pinned hash')
    write('/app/LeoKeypad/LeoKeypad.apk',ime)
    write('/bin/leo-terminal-root',ROOT/'output/leo-terminal-root',0o755)
    write('/bin/leo-su',ROOT/'output/leo-terminal-root',0o755)
    write('/etc/leo-first-setup.sh',ROOT/'leo-first-setup.sh')
    write('/etc/leo-clean-audio-seed.sh',ROOT/'leo-clean-audio-seed.sh')
    rc=dump('/etc/init/leo-terminal-root.rc','root.rc')
    text=rc.read_text();text=text.split('on property:sys.boot_completed=1')[0]
    rc.write_text(text,newline='\n');write('/etc/init/leo-terminal-root.rc',rc)
    # No immutable copy of a developer installation UID may survive in system.
    run('debugfs','-w','-R','rm /etc/leo-terminal-root.uid',image)
    audio=dump('/etc/init/init.leo.audio.rc','audio.rc')
    text=audio.read_text()
    needle='    exec_start leo_audio_preload'
    if text.count(needle)!=1:raise RuntimeError('Unexpected audio init sequence')
    text=text.replace(needle,'    exec_start leo_audio_seed\n'+needle)
    text+='\nservice leo_audio_seed /system/bin/sh /system/etc/leo-clean-audio-seed.sh\n    user root\n    group root system audio\n    seclabel u:r:init:s0\n    disabled\n    oneshot\n'
    audio.write_text(text,newline='\n');write('/etc/init/init.leo.audio.rc',audio)
    prop=dump('/build.prop','build.prop')
    lines=[line for line in prop.read_text().splitlines() if line.partition('=')[0] not in DEFAULTS]
    prop.write_text('\n'.join(lines)+'\n# Leo audited hardware defaults; saved user overrides win.\n'+
                    ''.join(key+'='+value+'\n' for key,value in sorted(DEFAULTS.items())),newline='\n')
    write('/build.prop',prop)
    fm={'trial':ROOT/'output/leo-fm-trial','route':ROOT/'output/leo-fm-route',
        'trace.so':ROOT/'output/leo-fm-trace.so','audio.jar':ROOT/'output/leo-fm-audio.jar',
        'fm_qsoc_patches':ROOT.parent/'analysis/system/bin/fm_qsoc_patches',
        'lib/libqcci_legacy.so':ROOT.parent/'analysis/system/vendor/lib/libqcci_legacy.so'}
    for name,source in fm.items():write('/vendor/leo-fm/'+name,source,0o755 if name in ('trial','route','fm_qsoc_patches') else 0o644)
    return {'runtime':records,'property_defaults':DEFAULTS,
            'userdata_contents':'new empty filesystem; no live userdata copied',
            'first_boot_hardware_validated':False}
