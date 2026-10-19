"""Keep Connected devices usable when bootstrap owns USB and no Android USB service exists."""
from pathlib import Path
ROOT=Path(__file__).resolve().parent
base=Path('/root/android81-leo/src/packages/apps/Settings/src/com/android/settings/connecteddevice')
backup=ROOT/'output/settings-usb-source-before';backup.mkdir(exist_ok=True)
def patch(name,changes,marker):
    path=base/name;text=path.read_text(encoding='utf-8')
    if marker in text:return
    before=text
    for old,new in changes:
        assert text.count(old)==1,(name,old)
        text=text.replace(old,new)
    saved=backup/name
    if not saved.exists():saved.write_text(before, encoding='utf-8')
    path.write_text(text, encoding='utf-8')
marker='// LEO_OPTIONAL_USB: do not fetch an unpublished USB service on this eng build.'
patch('ConnectedDeviceDashboardFragment.java',[
 ('        mUsbPrefController = new UsbModePreferenceController(context, new UsbBackend(context));',
  '        '+marker+'\n        mUsbPrefController = new UsbModePreferenceController(context,\n                android.os.ServiceManager.checkService(Context.USB_SERVICE) == null\n                        ? null : new UsbBackend(context));')],marker)
marker='// LEO_OPTIONAL_USB: the row is unavailable without its Android backend.'
patch('UsbModePreferenceController.java',[
 ('    public boolean isAvailable() {\n        return true;','    public boolean isAvailable() {\n        '+marker+'\n        return mUsbBackend != null;'),
 ('        mUsbReceiver.register();','        if (mUsbBackend != null) mUsbReceiver.register();'),
 ('    private void updataSummary(Preference preference) {\n        updataSummary(preference, mUsbBackend.getCurrentMode());','    private void updataSummary(Preference preference) {\n        if (mUsbBackend == null) return;\n        updataSummary(preference, mUsbBackend.getCurrentMode());')],marker)
print('Connected devices handles missing USB backend; Bluetooth controls retained')
