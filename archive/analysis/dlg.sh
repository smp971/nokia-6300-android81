#!/bin/bash
# usage: dlg.sh <png-name> [text-to-type]  -- operates on the "Select Directory" dialog
D=$(xwininfo -root -tree | grep '"Select Directory"' | head -1 | awk '{print $1}')
[ -z "$D" ] && { echo "no dialog"; exit 1; }
if [ -n "$2" ]; then
  xdotool windowactivate "$D"; sleep 0.5
  xdotool key --window "$D" ctrl+l; sleep 0.5
  xdotool type --window "$D" --delay 40 "$2"; sleep 0.8
fi
import -window "$D" /mnt/d/ROMS/nokia-6300-ta1286-dump/analysis/screens/$1.png && echo "saved $1 ($D)"
