#!/bin/bash
set -euo pipefail
source /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/environment.sh
java -version
javac -version
python --version
python3 --version
make --version | head -n 1
git --version
cd /root/android81-leo/src
python3 /root/android81-leo/repo-launcher version
test "$(python -c 'import sys; print(sys.version_info[0])')" = 2
test "$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d . -f 1-2)" = 1.8
printf 'Host prerequisites checked; Android build still requires a complete source sync.\n'
