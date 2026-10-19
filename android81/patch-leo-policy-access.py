"""Permit the existing privileged DND grant command for Leo on the low-RAM Oreo build."""
from pathlib import Path
ROOT=Path(__file__).resolve().parent
source=Path('/root/android81-leo/src/frameworks/base/services/core/java/com/android/server/notification/NotificationManagerService.java')
text=source.read_text()
start=text.index('        public void setNotificationPolicyAccessGrantedForUser(')
end=text.index('        public Policy getNotificationPolicy(',start)
method=text[start:end]
marker='// Leo: shell/system may explicitly grant policy access to our keypad shell on low RAM.'
if marker not in method:
    old='                if (!mActivityManager.isLowRamDevice()) {'
    assert method.count(old)==1
    assert 'checkCallerIsSystemOrShell();' in method
    folder=ROOT/'output/leo-policy-source-before';folder.mkdir(exist_ok=True)
    backup=folder/source.name
    if not backup.exists():backup.write_text(text)
    method=method.replace(old,'                '+marker+'\n                if (!mActivityManager.isLowRamDevice() || "ro.leo.visual".equals(pkg)) {')
    source.write_text(text[:start]+method+text[end:])
print('Leo-only DND policy grant enabled; shell/system caller enforcement and low-RAM setting retained')
