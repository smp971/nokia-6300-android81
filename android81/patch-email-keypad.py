"""Add keypad controls to the existing AOSP account setup and compose activities."""
from pathlib import Path
import re,shutil
root=Path(__file__).resolve().parent;src=Path('/root/android81-leo/src')
helper=src/'packages/apps/UnifiedEmail/src/com/android/mail/utils/LeoEmailKeys.java'
shutil.copyfile(root/'email-keypad/LeoEmailKeys.java',helper)
for relative,compose in [('packages/apps/Email/src/com/android/email/activity/setup/AccountSetupActivity.java',False),('packages/apps/UnifiedEmail/src/com/android/mail/compose/ComposeActivity.java',True)]:
    path=src/relative;text=path.read_text()
    if 'LeoEmailKeys.dispatch' in text:continue
    declaration=re.search(r'public class '+path.stem+r'\b[^\{]+\{',text);assert declaration
    method='\n    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {\n        if (com.android.mail.utils.LeoEmailKeys.dispatch(this, event, '+str(compose).lower()+')) return true;\n        return super.dispatchKeyEvent(event);\n    }\n'
    text=text[:declaration.end()]+method+text[declaration.end():];path.write_text(text)
print('Email setup/compose keypad hooks staged')
