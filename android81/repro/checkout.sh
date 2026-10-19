#!/bin/bash
# Obtain exact upstream sources. This does not build, install or flash a phone.
set -euo pipefail
PROJECT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
SOURCE=/root/android81-leo/src
command -v repo >/dev/null
mkdir -p "$SOURCE"
cd "$SOURCE"
if [ -d .repo ]; then
    echo 'Existing AOSP checkout retained; use repo sync deliberately, then apply.py.'
    exit 0
fi
repo init -u "$PROJECT" -b main -m android81/repro/aosp-manifest.xml --depth=1 --no-clone-bundle
repo sync -c -j4 --no-clone-bundle --fail-fast
python3 "$PROJECT/android81/repro/apply.py" --source "$SOURCE"
echo 'Sources checked out and patched. See REBUILD.md for device prerequisites.'
