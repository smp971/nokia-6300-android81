#!/bin/bash
# usage: dlgkey.sh <png-name> <key> [<key> ...]  -- send keys to the Select Directory dialog, then capture it
D=$(xwininfo -root -tree | grep '"Select Directory"' | head -1 | awk '{print $1}')
[ -z "$D" ] && { echo "no dialog"; exit 1; }
name=$1; shift
xdotool windowactivate "$D"; sleep 0.4
for k in "$@"; do xdotool key --window "$D" "$k"; sleep 0.8; done
sleep 1
import -window "$D" /mnt/d/ROMS/nokia-6300-ta1286-dump/analysis/screens/$name.png 2>/dev/null && echo "saved $name"
