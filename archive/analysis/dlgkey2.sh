#!/bin/bash
# usage: dlgkey2.sh <png-name> <key>...  -- focus the dialog, click into the path field, send keys globally
D=$(xwininfo -root -tree | grep '"Select Directory"' | head -1 | awk '{print $1}')
[ -z "$D" ] && { echo "no dialog"; exit 1; }
name=$1; shift
xdotool windowactivate "$D"; xdotool windowfocus "$D"; sleep 0.5
xdotool mousemove --window "$D" 600 22; sleep 0.2; xdotool click 1; sleep 0.3
for k in "$@"; do xdotool key "$k"; sleep 1; done
sleep 1.5
import -window "$D" /mnt/d/ROMS/nokia-6300-ta1286-dump/analysis/screens/$name.png 2>/dev/null && echo "saved $name"
