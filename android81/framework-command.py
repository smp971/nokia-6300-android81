"""Run a diagnostic command with Android init's Java environment."""
from pathlib import Path
import shlex
import subprocess
import sys
import tarfile

with tarfile.open(Path(__file__).parent/'output/framework-root.tar') as archive:
    environment = archive.extractfile('./init.environ.rc').read().decode()
variables = []
for line in environment.splitlines():
    fields = line.split()
    if len(fields) == 3 and fields[0] == 'export':
        variables.append(fields[1]+'='+fields[2])
command = ['/sbin/leo-framework-exec', '/system/bin/env', *variables, *sys.argv[1:]]
raise SystemExit(subprocess.call(['D:/ROMS/platform-tools/adb.exe', '-s', '695d',
                                 'shell', shlex.join(command)]))
