"""Opt-in CPU wait on native fences when the Leo legacy EGL server wait fails."""
from pathlib import Path
import argparse
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--undo',action='store_true');args=parser.parse_args()
p=Path('/root/android81-leo/src/frameworks/native/libs/gui/SyncFeatures.cpp')
s=p.read_text()
marker='// LEO_CPU_NATIVE_FENCE_WAIT: preserve kernel synchronization, avoid broken EGL wait.'
if args.undo:
    if marker in s:
        block='    '+marker+'\n    if (property_get_bool("persist.leo.adreno.cpu_wait", false)) return false;\n'
        assert s.count(block)==1
        s=s.replace(block,'').replace('\n#include <cutils/properties.h>','')
        p.write_text(s)
    print('Experimental CPU fence wait removed from AOSP source')
    raise SystemExit(0)
if marker not in s:
    anchor='bool SyncFeatures::useWaitSync() const {\n'
    assert s.count(anchor)==1
    s=s.replace('#include <private/gui/SyncFeatures.h>','#include <private/gui/SyncFeatures.h>\n#include <cutils/properties.h>')
    s=s.replace(anchor,anchor+'    '+marker+'\n    if (property_get_bool("persist.leo.adreno.cpu_wait", false)) return false;\n')
    p.write_text(s)
print('Leo opt-in CPU native-fence wait present; native fence production unchanged')
