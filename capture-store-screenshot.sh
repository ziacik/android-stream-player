#!/bin/sh
set -eu

name="${1:-home}"
target="${2:-192.168.0.200:5555}"
remote="/sdcard/kino-${name}.png"
out="store/uptodown/screenshots/${name}.png"

mkdir -p "$(dirname "$out")"
adb -s "$target" shell screencap -p "$remote"
adb -s "$target" pull "$remote" "$out"
adb -s "$target" shell rm -f "$remote"
echo "$out"
