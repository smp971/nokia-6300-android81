import copy, importlib.util, json, struct, subprocess, tempfile, unittest, zipfile
from pathlib import Path
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('recovery_installer',Path(__file__).parents[1]/'install-recovery-bundle.py')
rom=importlib.util.module_from_spec(spec);spec.loader.exec_module(rom)

class RecoveryGuards(unittest.TestCase):
    def manifest(self):
        files={name:{'bytes':100,'sha256':'a'*64} for name in rom.ALLOWED}
        files['images/boot.img']['bytes']=33554432
        files['images/system.img']['bytes']=838860800
        return {'format':'leo-private-recovery-bundle','schema':1,'serial':'695d','private':True,'files':files}
    def test_expected_payload(self):
        self.assertEqual(rom.validate_manifest(self.manifest())['serial'],'695d')
    def test_userdata_cannot_be_a_target(self):
        meta=self.manifest();meta['files']['images/userdata.img']={'bytes':100,'sha256':'b'*64}
        with self.assertRaises(ValueError):rom.validate_manifest(meta)
    def test_wrong_device_rejected(self):
        meta=self.manifest();meta['serial']='another-device'
        with self.assertRaises(ValueError):rom.validate_manifest(meta)
    def test_traversal_rejected(self):
        meta=self.manifest();meta['files']['../boot.img']=meta['files'].pop('images/boot.img')
        with self.assertRaises(ValueError):rom.validate_manifest(meta)
    def test_wrong_partition_size_rejected(self):
        meta=self.manifest();meta['files']['images/system.img']['bytes']-=512
        with self.assertRaises(ValueError):rom.validate_manifest(meta)
    def test_invalid_hash_rejected(self):
        meta=self.manifest();meta['files']['runtime/busybox']['sha256']='bad'
        with self.assertRaises(ValueError):rom.validate_manifest(meta)
    def test_duplicate_zip_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'bad.zip'
            with zipfile.ZipFile(path,'w') as archive:
                archive.writestr('manifest.json','{}')
                with self.assertWarns(UserWarning):archive.writestr('manifest.json','{}')
            with self.assertRaisesRegex(ValueError,'Duplicate'):rom.verify(path)
    def test_dynamic_helper_rejected(self):
        elf=bytearray(84);elf[:6]=b'\x7fELF\x01\x01';struct.pack_into('<H',elf,18,40)
        struct.pack_into('<I',elf,28,52);struct.pack_into('<HH',elf,42,32,1);struct.pack_into('<I',elf,52,3)
        with self.assertRaisesRegex(ValueError,'static'):rom.static_arm(elf)
    def test_oversized_boot_payload_rejected(self):
        boot=bytearray(64);boot[:8]=b'ANDROID!'
        for at,value in [(8,33554432),(16,1024),(36,2048)]:struct.pack_into('<I',boot,at,value)
        with self.assertRaises(ValueError):rom.image_header('boot',boot,33554432)
    def test_invalid_filesystem_rejected(self):
        with self.assertRaises(ValueError):rom.image_header('system',bytes(4096),838860800)
    def test_running_android_cannot_be_flashed(self):
        def adb(args,**kwargs):
            command=' '.join(args)
            if 'pidof system_server' in command:output=b'514'
            elif '/proc/cmdline' in command:output=b'androidboot.serialno=695d '
            elif 'id;' in command or command.endswith("'id'") or command.endswith(' id'):output=b'uid=0(root)'
            else:output=b''
            if 'shell' in args:output+=b'\nLEO_RC_0\n'
            return subprocess.CompletedProcess(args,0,stdout=output,stderr=b'')
        with patch.object(rom.subprocess,'run',side_effect=adb) as calls:
            with self.assertRaisesRegex(RuntimeError,'Active Android'):
                rom.recovery_preflight(['adb','-s','695d'],self.manifest(),Path('fixture-helper'))
            self.assertFalse(any(' of=' in ' '.join(call.args[0]) for call in calls.call_args_list))

if __name__=='__main__':unittest.main()
