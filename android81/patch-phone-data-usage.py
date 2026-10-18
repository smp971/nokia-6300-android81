"""Keep mobile network settings available without kernel traffic accounting."""
from pathlib import Path

root = Path('/root/android81-leo/src/packages/services/Telephony')
path = root / 'src/com/android/phone/DataUsagePreference.java'
source = path.read_text()
marker = '// LEO_MISSING_TRAFFIC_STATS'
needle = '        setSummary(activity.getString(R.string.data_usage_template,'
if marker not in source:
    assert source.count(needle) == 1
    source = source.replace(needle, '''        // LEO_MISSING_TRAFFIC_STATS: the controller may return null on this kernel.
        setEnabled(usageInfo != null);
        if (usageInfo == null) {
            setSummary(R.string.leo_data_usage_unavailable);
            return;
        }
''' + needle)
    path.write_text(source)
strings = root / 'res/values/strings.xml'
source = strings.read_text()
if 'name="leo_data_usage_unavailable"' not in source:
    source = source.replace('</resources>', '    <string name="leo_data_usage_unavailable">Data usage unavailable</string>\n</resources>')
    strings.write_text(source)
