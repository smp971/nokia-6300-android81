#!/bin/bash
# Run in WSL kaios. Build a symbol-table-only reader without changing AOSP files.
# Nokia's vendor AV rules are not accepted by the full AOSP 8.1 reader, so the
# image builder retains all vendor rule bytes and changes only permissive_map.
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
POLICY_SRC=/root/android81-leo/src/external/selinux/libsepol
python3 - <<'PY'
from pathlib import Path
source=Path('/root/android81-leo/src/external/selinux/libsepol/src/policydb.c').read_text()
anchor='\tif (policy_type == POLICY_KERN) {\n\t\tif (avtab_read(&p->te_avtab, fp, r_policyvers))'
assert source.count(anchor)==1
source=source.replace(anchor,'\t/* Diagnostic reader: stop after symbol tables. */\n\treturn 0;\n'+anchor)
Path('/root/android81-leo/diag-policydb.c').write_text(source)
PY
gcc -O2 -I"$POLICY_SRC/include" -I"$POLICY_SRC/src" \
  "$TASK_DIR/recovery-debug-policy.c" /root/android81-leo/diag-policydb.c \
  /root/android81-leo/src/out/host/linux-x86/obj/STATIC_LIBRARIES/libsepol_intermediates/libsepol.a \
  -o /root/android81-leo/recovery-debug-policy
