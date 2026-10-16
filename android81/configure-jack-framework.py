"""WSL: retain a backup and limit the legacy framework compiler's concurrency."""
from pathlib import Path
import re,shutil
config=Path('/root/.jack-server/config.properties')
if not config.exists():
    raise SystemExit('Install the dedicated Jack server before the full framework build.')
text=config.read_text()
assert len(re.findall(r'^jack.server.max-service=\d+$',text,re.M))==1
updated=re.sub(r'^jack.server.max-service=\d+$','jack.server.max-service=2',text,flags=re.M)
if updated!=text:
    running=[]
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():continue
        try:
            if b'com.android.jack.launcher.ServerLauncher' in (proc/'cmdline').read_bytes():running.append(proc.name)
        except (FileNotFoundError,PermissionError):pass
    if running:raise SystemExit('Stop the existing Jack server before changing concurrency: '+','.join(running))
    backup=Path('/root/android81-leo/jack-config-before-framework.properties')
    if not backup.exists():shutil.copy2(config,backup)
    config.write_text(updated)
print('Jack framework concurrency limit: 2; heap is configured by environment.sh')
