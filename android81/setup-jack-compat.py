"""Create build-scoped legacy TLS settings for Jack's loopback connection."""
from pathlib import Path
import os
import re
import signal

source=Path('/etc/java-8-openjdk/security/java.security').read_text()
joined=source.replace('\\\n','')
match=re.search(r'^jdk.tls.disabledAlgorithms=(.*)$',joined,re.M)
assert match
algorithms=[v.strip() for v in match.group(1).split(',')]
algorithms=[v for v in algorithms if v not in ('TLSv1','TLSv1.1')]
out=Path('/root/android81-leo/jack-java.security')
out.write_text('jdk.tls.disabledAlgorithms='+', '.join(algorithms)+'\n')
print('Created build-local Java security overlay:',out)
for proc in Path('/proc').iterdir():
    if not proc.name.isdigit():
        continue
    try:
        cmd=(proc/'cmdline').read_bytes().split(b'\0')
        if b'com.android.jack.launcher.ServerLauncher' in cmd:
            os.kill(int(proc.name),signal.SIGTERM)
            print('Stopped old Jack server for restart:',proc.name)
    except (OSError,ProcessLookupError):
        pass
