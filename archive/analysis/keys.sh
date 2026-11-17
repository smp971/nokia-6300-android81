#!/bin/bash
# usage: keys.sh key1 key2 ... -> sends keys to the WebIDE window, 1s apart
W=$(xwininfo -root -tree | grep 'WebIDE - Kaiosrt' | head -1 | awk '{print $1}')
for k in "$@"; do xdotool windowfocus "$W" 2>/dev/null; xdotool key --window "$W" "$k"; sleep 1; done
