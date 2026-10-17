"""Initialize DispSync in Oreo's legacy HWC1 SurfaceFlinger, matching the HWC2 path."""
from pathlib import Path
p=Path('/root/android81-leo/src/frameworks/native/services/surfaceflinger/SurfaceFlinger_hwc1.cpp')
s=p.read_text()
anchor='''    dispSyncPresentTimeOffset = getInt64< ISurfaceFlingerConfigs,
            &ISurfaceFlingerConfigs::presentTimeOffsetFromVSyncNs>(0);'''
line='    mPrimaryDispSync.init(hasSyncFramework, dispSyncPresentTimeOffset);'
assert s.count(anchor)==1
if line not in s:
 s=s.replace(anchor,anchor+'\n\n    // HWC1 also needs the scheduler thread introduced by DispSync::init.\n'+line)
 p.write_text(s)
assert s.count(line)==1
print('HWC1 DispSync initialization present')
