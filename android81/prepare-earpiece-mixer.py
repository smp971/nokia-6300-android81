"""Derive a narrow receiver-boost patch from the local, verified stock mixer XML.

The proprietary XML stays in ignored output; only this transformation is tracked.
"""
from pathlib import Path
import hashlib,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
source=root.parent/'analysis/system/etc/mixer_paths.xml'
data=source.read_bytes()
assert hashlib.sha256(data).hexdigest()=='7bb48dace20a52686ca2d01cb98d6fd472f3709a429fb06bb76c6503e84ad6f4'
text=data.decode();tree=ET.fromstring(text)
assert not tree.findall("./ctl[@name='EAR PA Boost']")
default='    <ctl name="EAR PA Gain" value="POS_1P5_DB" />'
assert text.count(default)==1
text=text.replace(default,default+'\n    <ctl name="EAR PA Boost" value="DISABLE" />')
start=text.index('    <path name="handset">');end=text.index('</path>',start)
path=text[start:end]
anchor='        <ctl name="EAR_S" value="Switch" />'
assert path.count(anchor)==1
text=text[:start]+path.replace(anchor,'        <ctl name="EAR PA Boost" value="ENABLE" />\n'+anchor)+text[end:]
patched=ET.fromstring(text)
assert len(patched.findall("./path[@name='handset']/ctl[@name='EAR PA Boost'][@value='ENABLE']"))==1
out=root/'output/mixer_paths-earpiece.xml';out.write_text(text,newline='')
print('Prepared receiver-boost route and disabled default: '+hashlib.sha256(out.read_bytes()).hexdigest())
