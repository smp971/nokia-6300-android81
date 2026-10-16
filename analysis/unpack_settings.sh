#!/bin/bash
P=/root/kaios-simulator/kaiosrt-v2.5-ubuntu-20190925163557-n378/kaiosrt/gaia/profile/webapps
mkdir -p /root/settings-work && cd /root/settings-work && rm -rf src && mkdir src && cd src
unzip -q $P/settings.gaiamobile.org/application.zip && ls | head -12
echo "--- occurrences (file:count):"; grep -rc "mozMobileConnections" --include=*.js . | grep -v ":0$"
echo "--- panel.js 955-970:"; sed -n '955,970p' js/panels/root/panel.js | cut -c1-160
