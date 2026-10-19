"""Run a bounded diagnostic command with Android init's Java environment.

An optional leading --timeout SECONDS overrides the 25-second limit. Only this
invocation's local ADB client is terminated on timeout; the shared server stays up.
"""
from pathlib import Path
import os
import shlex
import subprocess
import sys
import tarfile

arguments=sys.argv[1:]
timeout=25.0
if arguments[:1]==['--timeout']:
    try:
        timeout=float(arguments[1]);arguments=arguments[2:]
        if not 0<timeout<=3600:raise ValueError()
    except (IndexError,ValueError):raise SystemExit('--timeout requires 0 < seconds <= 3600')
if not arguments:raise SystemExit('Usage: framework-command.py [--timeout SECONDS] COMMAND [ARG ...]')

with tarfile.open(Path(__file__).parent/'output/framework-root.tar') as archive:
    environment = archive.extractfile('./init.environ.rc').read().decode()
variables = []
for line in environment.splitlines():
    fields = line.split()
    if len(fields) == 3 and fields[0] == 'export':
        variables.append(fields[1]+'='+fields[2])
command = ['/sbin/leo-framework-exec', '/system/bin/env', *variables, *arguments]
process=subprocess.Popen(['D:/ROMS/platform-tools/adb.exe','-s','695d','shell',shlex.join(command)],
                         start_new_session=os.name!='nt')
def stop_client():
    if process.poll() is not None:return
    if os.name=='nt':
        try:subprocess.run(['taskkill.exe','/PID',str(process.pid),'/T','/F'],capture_output=True,timeout=5)
        except (OSError,subprocess.TimeoutExpired):pass
    else:
        import signal
        try:os.killpg(process.pid,signal.SIGKILL)
        except ProcessLookupError:pass
    if process.poll() is None:process.kill()
    process.wait(timeout=5)
try:status=process.wait(timeout=timeout)
except subprocess.TimeoutExpired:
    stop_client();print('ADB command exceeded '+str(timeout)+' seconds; local client stopped.',file=sys.stderr);status=124
except BaseException:
    stop_client();raise
raise SystemExit(status)
