"""Apply the Leo audio-backed vibrator adapter to the local AOSP source tree."""
from pathlib import Path
import subprocess,os,sys
script=r"""
from pathlib import Path
p=Path('/root/android81-leo/src/hardware/interfaces/vibrator/1.0/default/Vibrator.cpp')
s=p.read_text()
if '// Leo speaker-haptic adapter' not in s:
 s=s.replace('#include <hardware/hardware.h>','#include <hardware/hardware.h>\n#include <media/AudioSystem.h>\n#include <utils/String8.h>\n#include <cutils/properties.h>\n#include <algorithm>')
 s=s.replace('int32_t ret = mDevice->vibrator_on(mDevice, timeout_ms);', '''// Leo speaker-haptic adapter: the stock PMIC path is not the physical actuator.
    int32_t ret;
    if (property_get_bool("vendor.audio.haptic_audio", false)) {
        char command[96];
        snprintf(command, sizeof(command), "vibrate_enabled=true;vibrate_duration=%u",
                 std::max(1u, std::min(timeout_ms, 15000u)));
        ret = AudioSystem::setParameters(String8(command));
    } else {
        ret = mDevice->vibrator_on(mDevice, timeout_ms);
    }''')
 s=s.replace('int32_t ret = mDevice->vibrator_off(mDevice);','''int32_t ret = property_get_bool("vendor.audio.haptic_audio", false)
        ? AudioSystem::setParameters(String8("vibrate_enabled=false"))
        : mDevice->vibrator_off(mDevice);''')
 p.write_text(s)
p=Path('/root/android81-leo/src/hardware/interfaces/vibrator/1.0/default/Android.bp')
s=p.read_text()
if '"libaudioclient"' not in s:s=s.replace('"libhardware",','"libhardware",\n        "libaudioclient",\n        "libcutils",')
p.write_text(s)
"""
subprocess.run((['wsl','-d','kaios','--','python3'] if os.name=='nt' else [sys.executable])+['-c',script],check=True)
