"""Collect one matching Oreo services runtime and its boot-image prerequisites."""
from pathlib import Path
import hashlib,json,shutil
ROOT=Path(__file__).resolve().parent
PRODUCT=Path('/root/android81-leo/src/out/target/product/leo/system/framework')
DEST=ROOT/'output/netpolicy-runtime'; DEST.mkdir(parents=True,exist_ok=True)
for name in ('services.jar','services.art','services.odex','services.vdex'):
    source=PRODUCT/name if name.endswith('.jar') else PRODUCT/'oat/arm'/name
    shutil.copy2(source,DEST/name)
boot={str(path.relative_to(PRODUCT.parent.parent)):hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted((PRODUCT/'arm').glob('boot*')) if path.is_file() and path.suffix in ('.art','.oat','.vdex')}
assert len(boot)>=3
(DEST/'boot-prerequisites.json').write_text(json.dumps(boot,indent=2))
print('Collected services jar/art/odex/vdex with',len(boot),'boot prerequisites')
