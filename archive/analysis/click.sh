#!/bin/bash
# usage: click.sh X Y   (window-relative coordinates in the WebIDE window)
W=$(xwininfo -root -tree | grep 'WebIDE - Kaiosrt' | head -1 | awk '{print $1}')
xdotool windowactivate "$W" 2>/dev/null
xdotool mousemove --window "$W" "$1" "$2"
sleep 0.3
xdotool click 1
