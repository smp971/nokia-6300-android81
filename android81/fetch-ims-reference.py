"""Fetch a pinned legacy Qualcomm client into ignored output for compatibility study.

Downloaded vendor sources are not part of this repository or the device image.
"""
from pathlib import Path
import concurrent.futures,hashlib,json,urllib.request
revision='f242858a8cbb2c9ad9b5b2e53d75380ae775727a'
repo='bcyj/android_tools_leeco_msm8996'
prefix='telephony-apps/ims'
root=Path(__file__).resolve().parent/'output/ims-reference/client'
root.mkdir(parents=True,exist_ok=True)
def get(url):
    request=urllib.request.Request(url,headers={'User-Agent':'Leo-IMS-compatibility-study'})
    return urllib.request.urlopen(request,timeout=30).read()
entries=json.loads(get(f'https://api.github.com/repos/{repo}/contents/{prefix}?ref={revision}'))
files=[]
for entry in entries:
    if entry['type']=='file' and entry['name'] in ('AndroidManifest.xml','Android.mk','ims.xml'):
        files.append(entry['name'])
    if entry['type']=='dir' and entry['name'] in ('src','res'):
        tree=json.loads(get(f'https://api.github.com/repos/{repo}/git/trees/{entry["sha"]}?recursive=1'))
        assert not tree.get('truncated')
        files.extend(entry['name']+'/'+x['path'] for x in tree['tree'] if x['type']=='blob' and x['path'].endswith(('.java','.proto','.xml')))
def fetch(name):
    assert '..' not in Path(name).parts
    dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True)
    data=get(f'https://raw.githubusercontent.com/{repo}/{revision}/{prefix}/{name}')
    dest.write_bytes(data);return name,hashlib.sha256(data).hexdigest()
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:hashes=dict(pool.map(fetch,files))
(root/'source-manifest.json').write_text(json.dumps({'repository':repo,'revision':revision,'sha256':hashes},indent=2))
print('Fetched',len(hashes),'reference source files into ignored output.')
