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

    def fresh(self):
        meta=self.manifest();meta['schema']=2
        meta['files']['images/userdata.img']={'bytes':2566897152,'sha256':'b'*64}
        return meta

    def test_clean_schema_exact_partition_size(self):
        self.assertEqual(rom.validate_manifest(self.fresh())['schema'],2)
        meta=self.fresh();meta['files']['images/userdata.img']['bytes']-=512
        with self.assertRaises(ValueError):rom.validate_manifest(meta)

    def test_wipe_must_be_explicit_and_only_for_clean_bundle(self):
        for meta,wipe in [(self.fresh(),False),(self.manifest(),True)]:
            with self.assertRaises(ValueError):rom.check_install_flags(meta,'695d','695d',wipe)
        rom.check_install_flags(self.fresh(),'695d','695d',True)

    def test_boot_is_written_last(self):
        self.assertEqual(rom.flash_order(self.fresh()),['system','userdata','boot'])
        self.assertEqual(rom.flash_order(self.manifest()),['system','boot'])

    def test_mounted_userdata_refuses_clean_install(self):
        mounts='25 1 259:7 / /data rw - ext4 /dev/block/mmcblk0p39 rw\n'
        with self.assertRaisesRegex(RuntimeError,'mounted'):rom.assert_unmounted(mounts,self.fresh())
        rom.assert_unmounted(mounts,self.manifest())

    def test_original_backups_required(self):
        with self.assertRaisesRegex(ValueError,'stock-backups'):rom.verify_stock_backups(None)
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError,'Missing original'):rom.verify_stock_backups(Path(directory))

    def test_unrelated_partitions_never_allowed(self):
        for name in ('modem','persist','recovery','modemst1'):
            meta=self.fresh();meta['files']['images/'+name+'.img']={'bytes':100,'sha256':'a'*64}
            with self.assertRaises(ValueError):rom.validate_manifest(meta)

entry_spec=importlib.util.spec_from_file_location('root_entry',Path(__file__).parents[1]/'install-root-recovery.py')
entry=importlib.util.module_from_spec(entry_spec);entry_spec.loader.exec_module(entry)

class RecoveryEntryGuards(unittest.TestCase):
    def run_entry(self,*flags):
        from types import SimpleNamespace
        with patch('sys.argv',['install-root-recovery.py','fixture.img',*flags]), \
             patch.object(entry.Path,'stat',return_value=SimpleNamespace(st_size=entry.SIZE)), \
             patch.object(entry,'digest',return_value=entry.RESCUE_SHA):
            entry.main()

    def test_default_verification_does_not_contact_phone(self):
        with patch.object(entry.subprocess,'run') as adb:
            self.run_entry()
            adb.assert_not_called()

    def test_wrong_serial_refused_before_adb(self):
        with patch.object(entry.subprocess,'run') as adb:
            with self.assertRaisesRegex(ValueError,'serial'):
                self.run_entry('--install','--serial','samsung','--confirm-target','samsung')
            adb.assert_not_called()

    def test_nonstock_phone_refused_before_push_or_dd(self):
        def adb(args,**kwargs):
            command=' '.join(args);output=''
            if ' id;' in command:output='uid=0(root)'
            elif '/proc/cmdline' in command:output='androidboot.serialno=695d'
            elif '/uevent' in command:
                part,number,major,minor=next(row for row in [('boot',29,179,29),('system',30,179,30),('recovery',33,259,1)] if f'mmcblk0p{row[1]}/uevent' in command)
                output=f'PARTNAME={part}\nMAJOR={major}\nMINOR={minor}'
            elif '/size' in command:output=str((838860800 if 'mmcblk0p30' in command else entry.SIZE)//512)
            elif ' stat ' in command:
                output=next(value for part,value in [('boot','b3:1d'),('system','b3:1e'),('recovery','103:1')] if '/dev/leo-rescue-'+part in command)
            elif 'sha256sum' in command:output='0'*64+'  /dev/leo-rescue-boot'
            return subprocess.CompletedProcess(args,0,stdout=(output+'\nLEO_RESCUE_RC_0\n').encode(),stderr=b'')
        with patch.object(entry.subprocess,'run',side_effect=adb) as calls:
            with self.assertRaisesRegex(RuntimeError,'Stock boot/system'):
                self.run_entry('--install','--serial','695d','--confirm-target','695d')
            self.assertFalse(any('push' in call.args[0] or ' dd ' in ' '.join(call.args[0]) for call in calls.call_args_list))

clean_spec=importlib.util.spec_from_file_location('clean_system',Path(__file__).parents[1]/'prepare-clean-system.py')
clean=importlib.util.module_from_spec(clean_spec);clean_spec.loader.exec_module(clean)

class SystemMetadataGuards(unittest.TestCase):
    GOOD='Inode: 1568 Type: regular Mode: 0755 Flags: 0x80000\nUser: 0 Group: 0 Project: 0\nsecurity.selinux = "u:object_r:system_file:s0"'
    def test_expected_executable_metadata(self):
        clean.verify_metadata(self.GOOD,0o755)
    def test_broad_permissions_rejected(self):
        with self.assertRaisesRegex(RuntimeError,'mode'):
            clean.verify_metadata(self.GOOD.replace('0755','0777'),0o755)
    def test_wrong_owner_rejected(self):
        with self.assertRaisesRegex(RuntimeError,'owner'):
            clean.verify_metadata(self.GOOD.replace('User: 0','User: 10000'),0o755)
    def test_wrong_context_rejected(self):
        with self.assertRaisesRegex(RuntimeError,'SELinux'):
            clean.verify_metadata(self.GOOD.replace('system_file','unlabeled'),0o755)

if __name__=='__main__':unittest.main()
