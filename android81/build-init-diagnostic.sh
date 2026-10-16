#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
bash "$TASK_DIR/stage-device.sh"
python3 - <<'PY'
from pathlib import Path
p=Path('/root/android81-leo/src/system/core/init/util.cpp')
s=p.read_text()
anchor='void panic() {\n'
patch='''    // LEO diagnostic only: preserve early ADB instead of hiding the error in a reboot.
    if (getenv("LEO_DIAG_HOLD_ON_FAILURE")) {
        LOG(ERROR) << "LEO_DIAG: init panic requested; holding for early ADB";
        for (;;) pause();
    }
'''
if 'LEO_DIAG: init panic requested' not in s:
    assert s.count(anchor)==1
    Path('/root/android81-leo/init-util-before-leo-diag.cpp').write_text(s)
    p.write_text(s.replace(anchor,anchor+patch))
p=Path('/root/android81-leo/src/system/core/init/init.cpp')
s=p.read_text()
anchor='static bool selinux_is_enforcing(void)\n{\n'
patch='''    // LEO diagnostic only: keep stock boot command line and allow early ADB.
    if (ALLOW_PERMISSIVE_SELINUX && getenv("LEO_DIAG_HOLD_ON_FAILURE")) return false;
'''
if '// LEO diagnostic only: keep stock boot command line' not in s:
    assert s.count(anchor)==1
    Path('/root/android81-leo/init-main-before-leo-diag.cpp').write_text(s)
    p.write_text(s.replace(anchor,anchor+patch))
p=Path('/root/android81-leo/src/system/core/adb/daemon/main.cpp')
s=p.read_text()
anchor='static bool should_drop_privileges() {\n#if defined(ALLOW_ADBD_ROOT)\n'
patch='    // LEO diagnostic only: root ADB before Android properties exist.\n    if (getenv("LEO_EARLY_ADB")) return false;\n'
if '// LEO diagnostic only: root ADB before Android properties exist.' not in s:
    assert s.count(anchor)==1
    Path('/root/android81-leo/adbd-main-before-leo-diag.cpp').write_text(s)
    p.write_text(s.replace(anchor,anchor+patch))
PY
cd /root/android81-leo/src
set +u # Legacy Android envsetup reads optional unset variables.
source build/envsetup.sh
lunch aosp_leo_diag-eng
make -j2 init leo_diag_bootstrap adbd
cp out/target/product/leo/root/init "$TASK_DIR/output/init-android81-hold"
cp out/target/product/leo/system/bin/leo_diag_bootstrap "$TASK_DIR/output/leo-diag-bootstrap"
cp out/target/product/leo/system/bin/adbd "$TASK_DIR/output/adbd-android81-early"
