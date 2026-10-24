"""Offline adversarial checks; uses synthetic payloads and never calls ADB."""
import importlib.util,json,tempfile,unittest,zipfile
from pathlib import Path
spec=importlib.util.spec_from_file_location('bundle',Path(__file__).with_name('runtime-bundle.py'))
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
class BundleTests(unittest.TestCase):
 def fixture(self,folder,mutation=None,extra=None):
  installer=b'fixture installer';rows={};payload={}
  for n,target in enumerate(('ro.leo.visual','ro.leo.keypad','/system/vendor/leo-gps/exec')):
   key='payload/%04d'%n;raw=('fixture '+target).encode();payload[key]=raw
   rows[key]=dict(target=target,kind='app' if n<2 else 'system',mode=0o644,bytes=len(raw),sha256=b.digest(raw))
  m=dict(format='leo-runtime-update',schema=1,version='fixture',installer_sha256=b.digest(installer),files=rows)
  if mutation:mutation(m,payload)
  archive=folder/'fixture.zip'
  with zipfile.ZipFile(archive,'w') as z:
   z.writestr('manifest.json',json.dumps(m));z.writestr('runtime-bundle.py',installer)
   for key,raw in payload.items():z.writestr(key,raw)
   if extra:z.writestr(*extra)
  return archive
 def run_case(self,mutation=None,extra=None,valid=False):
  with tempfile.TemporaryDirectory() as tmp:
   archive=self.fixture(Path(tmp),mutation,extra)
   if valid:self.assertEqual(3,len(b.verify(archive)['files']))
   else:
    with self.assertRaises(ValueError):b.verify(archive)
 def test_valid(self):self.run_case(valid=True)
 def test_tamper(self):self.run_case(lambda m,p:p.update({'payload/0000':b'changed'}))
 def test_traversal(self):self.run_case(lambda m,p:m['files']['payload/0002'].update(target='/system/vendor/leo-gps/../../build.prop'))
 def test_extra_file(self):self.run_case(extra=('../outside',b'x'))
 def test_duplicate_target(self):self.run_case(lambda m,p:m['files']['payload/0001'].update(target='ro.leo.visual'))
 def test_setuid_refused(self):self.run_case(lambda m,p:m['files']['payload/0002'].update(mode=0o4755))
 def test_data_refused(self):self.run_case(lambda m,p:m['files']['payload/0002'].update(target='/data/private'))
 def test_double_slash(self):self.assertFalse(b.allowed('/system/vendor/leo-gps//exec'))
 def test_modes(self):
  self.assertTrue(b.allowed('/system/etc/init/leo-gps.rc'))
  self.assertFalse(b.allowed('/system/etc/init/unrelated.rc'))
  self.assertFalse(b.allowed('/system/vendor/leo-gps-x/exec'))
if __name__=='__main__':unittest.main()
