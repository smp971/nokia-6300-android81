"""Expose LTE choices on the LTE-capable Nokia 6300 target."""
from pathlib import Path

path = Path('/root/android81-leo/src/packages/services/Telephony/res/values/config.xml')
source = path.read_text()
old = '<bool name="config_enabled_lte" translatable="false">false</bool>'
new = '<bool name="config_enabled_lte" translatable="false">true</bool>'
assert source.count(old) + source.count(new) == 1
path.write_text(source.replace(old, new))
