"""Allow shell/system to grant only Leo's notification listener on low-RAM Oreo."""
from pathlib import Path
root=Path(__file__).resolve().parent
path=Path('/root/android81-leo/src/frameworks/base/services/core/java/com/android/server/notification/NotificationManagerService.java')
text=path.read_text(encoding='utf-8')
start=text.index('        public void setNotificationListenerAccessGrantedForUser(')
end=text.index('        public void setNotificationAssistantAccessGrantedForUser(',start)
method=text[start:end]
marker='// Leo: retain low-RAM limits except our explicitly granted keypad notification center.'
if marker not in method:
    old='                if (!mActivityManager.isLowRamDevice()) {'
    assert method.count(old)==1 and 'checkCallerIsSystemOrShell();' in method
    backup=root/'output/leo-listener-source-before';backup.mkdir(exist_ok=True)
    if not (backup/path.name).exists():(backup/path.name).write_text(text,encoding='utf-8')
    method=method.replace(old,'                '+marker+'\n                if (!mActivityManager.isLowRamDevice() ||\n                        ("ro.leo.visual".equals(listener.getPackageName()) &&\n                        "ro.leo.visual.LeoNotificationService".equals(listener.getClassName()))) {')
    path.write_text(text[:start]+method+text[end:],encoding='utf-8')
print('Leo-only listener opt-in enabled; system/shell enforcement and low-RAM profile retained')
