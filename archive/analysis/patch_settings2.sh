#!/bin/bash
set -e
P=/root/kaios-simulator/kaiosrt-v2.5-ubuntu-20190925163557-n378/kaiosrt/gaia/profile/webapps/settings.gaiamobile.org
cd /root/settings-work/src
python3 - <<'PY'
p='js/sim_shim.js'
s=open(p).read()
add='''
  if (!navigator.mozIccManager) {
    var icc = { iccIds: [], addEventListener: noop, removeEventListener: noop,
                getIccById: function () { return null; } };
    try { Object.defineProperty(navigator, 'mozIccManager',
          { value: icc, configurable: true }); } catch (e) {}
  }
})();'''
if 'mozIccManager' not in s:
    s=s.rstrip()
    assert s.endswith('})();')
    s=s[:-5]+add.lstrip('\n')+'\n'
    open(p,'w').write(s)
PY
tail -9 js/sim_shim.js
rm -f /root/settings-work/new.zip && zip -qr -X /root/settings-work/new.zip . && cp /root/settings-work/new.zip $P/application.zip && echo repacked
