#!/bin/bash
set -euo pipefail
base=/root/android81-leo/media-fixtures
mkdir -p "$base"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i sine=frequency=660:duration=12 \
    -filter:a volume=0.8 -ar 44100 -ac 2 "$base/tone.wav"
for extension in mp3 flac aac wma; do
    ffmpeg -hide_banner -loglevel error -y -i "$base/tone.wav" "$base/tone.$extension"
done
