"""Allow AOSP Email's drawable wrappers to reference its API-21 vector icons."""
from pathlib import Path
import argparse

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',default='/root/android81-leo/src')
args=parser.parse_args()
res=Path(args.source)/'packages/apps/UnifiedEmail/res/drawable'
path=res/'ic_next.xml';source=path.read_text()
old='<bitmap xmlns:android="http://schemas.android.com/apk/res/android"\n    android:src="@drawable/ic_next_24dp" />'
new='<inset xmlns:android="http://schemas.android.com/apk/res/android"\n    android:drawable="@drawable/ic_next_24dp" />'
if old in source:path.write_text(source.replace(old,new))
else:assert new in source,'Unexpected ic_next resource'
path=res/'compose_button.xml';source=path.read_text()
old='''    <item>
        <bitmap android:src="@drawable/ic_pencil_wht_24dp"
                android:gravity="center" />
    </item>'''
new='''    <item android:width="24dp" android:height="24dp" android:gravity="center"
          android:drawable="@drawable/ic_pencil_wht_24dp" />'''
if old in source:path.write_text(source.replace(old,new))
else:assert new in source,'Unexpected compose button resource'
print('Email vector wrappers corrected; rebuild Email')
