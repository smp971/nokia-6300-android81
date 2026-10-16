#!/bin/bash
set -e
P=/root/kaios-simulator/kaiosrt-v2.5-ubuntu-20190925163557-n378/kaiosrt/gaia/profile/webapps/settings.gaiamobile.org
cd /root/settings-work/src
[ -f $P/application.zip.orig ] || cp $P/application.zip $P/application.zip.orig
cat > js/sim_shim.js <<'JS'
/* Simulator shim: no modem/SIM in the KaiOS simulator, so give Settings a fake, disconnected one. */
(function () {
  'use strict';
  var noop = function () {};
  var net = { connected: false, emergencyCallsOnly: false, roaming: false,
              state: 'notSearching', type: null, network: null,
              relSignalStrength: null, signalStrength: null, cell: null };
  var conn = {
    voice: net, data: net, iccId: null, iccInfo: null, imsHandler: null,
    radioState: 'disabled', lastKnownNetwork: null, lastKnownHomeNetwork: null,
    networkSelectionMode: 'automatic', dataFetchingEnabled: false,
    addEventListener: noop, removeEventListener: noop,
    getNetworks: function () { return { onsuccess: null, onerror: null, result: [] }; },
    getSupportedNetworkTypes: function () { return { onsuccess: null, onerror: null, result: [] }; },
    setPreferredNetworkType: function () { return {}; },
    setRadioEnabled: function () { return {}; },
    getPreferredNetworkType: function () { return {}; }
  };
  var list = [conn];
  if (!navigator.mozMobileConnections) {
    try { Object.defineProperty(navigator, 'mozMobileConnections',
          { value: list, configurable: true }); } catch (e) {}
  }
  if (!navigator.mozMobileConnection && !navigator.mozMobileConnections) {
    try { Object.defineProperty(navigator, 'mozMobileConnection',
          { value: conn, configurable: true }); } catch (e) {}
  }
})();
JS
grep -q sim_shim.js index.html || sed -i '0,/<script/s//<script defer src="js\/sim_shim.js"><\/script>\n    <script/' index.html
grep -n "sim_shim" index.html | head -3
grep -n -m3 "<script" index.html | cut -c1-120
rm -f /root/settings-work/new.zip && zip -qr -X /root/settings-work/new.zip . && cp /root/settings-work/new.zip $P/application.zip
ls -l $P/application.zip $P/application.zip.orig
