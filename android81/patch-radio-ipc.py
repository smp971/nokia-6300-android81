"""Experimental private blob adaptation: use IPC router without legacy QMUX.

Only the known local ARM32 blob is accepted. Never modify the stock extraction
or install this experiment into /system. Modem services were independently
discovered through qcci_ipc_router_ops before attempting this adaptation.
"""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parent.parent
source = ROOT/'analysis/system/vendor/lib/libqmi_cci.so'
data = bytearray(source.read_bytes())
assert hashlib.sha256(data).hexdigest() == 'a00ca0007b7d7fe922e1c33adec0e58fb83e1440f07a02f971dfa907fc410352'
# qmi_fw_cci_init: keep IPC-router registration, skip legacy QMUX bootstrap.
assert data[0x1f72:0x1f76] == bytes.fromhex('fff7bfff')
data[0x1f72:0x1f76] = bytes.fromhex('00bf00bf')
# qmi_cci_qmux_xport_register: prevent RIL from re-registering legacy QMUX.
assert data[0x47d8:0x47dc] == bytes.fromhex('08b5a0f1')
data[0x47d8:0x47dc] = bytes.fromhex('00207047')  # movs r0,#0; bx lr
out = ROOT/'android81/output/radio-ipc/lib'
out.mkdir(parents=True, exist_ok=True)
(out/'libqmi_cci.so').write_bytes(data)
(out.parent/'patch.json').write_text(json.dumps({
    'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'patched_sha256': hashlib.sha256(data).hexdigest(),
    'status': 'experimental IPC-router-only private library; not installed',
    'offsets': ['0x1f72', '0x47d8'],
}, indent=2))
print(out/'libqmi_cci.so')
