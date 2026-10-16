#!/bin/bash
set -eo pipefail
source /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/environment.sh
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_diag-eng
make -j2 bootimage systemimage
