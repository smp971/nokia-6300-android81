#!/bin/bash
# usage: shot.sh name  -> captures the WebIDE window
W=$(xwininfo -root -tree | grep 'WebIDE - Kaiosrt' | head -1 | awk '{print $1}')
[ -z "$W" ] && { echo "no window"; exit 1; }
import -window "$W" /mnt/d/ROMS/nokia-6300-ta1286-dump/analysis/screens/$1.png && echo "saved $1 ($W)"
