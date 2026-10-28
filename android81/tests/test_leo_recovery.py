import gzip, importlib.util, random, struct, sys, tempfile, unittest, zipfile, zlib
from pathlib import Path

ROOT=Path(__file__).parents[1];sys.path.insert(0,str(ROOT))
import recovery_image as image
import kaios_sd_ramdisk as kaios

class KaiOSMountIsolation(unittest.TestCase):
    def test_diagnostic_policy_preserves_rules_and_only_adds_two_domains(self):
        header=struct.pack('<II',0xf97cff8c,8)+b'SE Linux'+struct.pack('<4I',30,0,8,8)
        empty=struct.pack('<III',64,0,0);rules=b'UNCHANGED SYMBOLS AND AV RULES'
        patched=kaios.diagnostic_domains(header+empty+empty+rules)
        expected=struct.pack('<III',64,896,2)+struct.pack('<IQ',128,1<<46)+struct.pack('<IQ',832,1<<30)
        self.assertEqual(patched,header+empty+expected+rules)
        with self.assertRaises(ValueError):kaios.patch('sepolicy',header+empty+empty+rules)
    def test_os_mounts_have_no_internal_fallback_or_auto_format(self):
        original=b'/dev/block/bootdevice/by-name/system /system ext4 ro wait\n/dev/block/bootdevice/by-name/userdata /data ext4 defaults wait,check,formattable,encryptable=footer\n/devices/mmc /storage/sdcard vfat defaults voldmanaged=sdcard:auto,encryptable=footer\n'
        result=kaios.patch('fstab.qcom',original).decode()
        self.assertIn('/dev/block/loop0 /system ext4 ro,noload',result)
        self.assertIn('/dev/block/loop1 /data ext4',result)
        for unsafe in ('by-name/','formattable','encryptable','voldmanaged'):self.assertNotIn(unsafe,result)
    def test_direct_cache_and_charger_mounts_are_redirected(self):
        original=b'wait /dev/block/bootdevice/by-name/cache\nmount ext4 /dev/block/bootdevice/by-name/cache /cache\non charger\n mount ext4 /dev/block/bootdevice/by-name/system /system ro\n'
        result=kaios.patch('init.target.rc',original)
        self.assertIn(b'/dev/block/loop2 /cache',result)
        self.assertIn(b'/dev/block/loop0 /system',result)
        self.assertNotIn(b'by-name/',result)
    def test_sd_boot_does_not_autostart_ota_daemon(self):
        original=b'service updater-daemon /system/bin/updater-daemon.sh\n    class main\n'
        self.assertIn(b'\n    disabled\n',kaios.patch('init.b2g.rc',original))
    def test_adbd_does_not_require_su_domain_but_keeps_service_label(self):
        original=b'service adbd /sbin/adbd --root_seclabel=u:r:su:s0\n    disabled\n    seclabel u:r:adbd:s0\n'
        result=kaios.patch('init.rc',original)
        self.assertNotIn(b'--root_seclabel',result)
        self.assertIn(b'seclabel u:r:adbd:s0',result)
spec=importlib.util.spec_from_file_location('kaios_sd',ROOT/'prepare-kaios-sd.py')
sd=importlib.util.module_from_spec(spec);spec.loader.exec_module(sd)
deploy_spec=importlib.util.spec_from_file_location('deploy_sd',ROOT/'deploy-kaios-sd.py')
deploy_sd=importlib.util.module_from_spec(deploy_spec);deploy_spec.loader.exec_module(deploy_sd)
build_spec=importlib.util.spec_from_file_location('build_recovery',ROOT/'build-leo-recovery.py')
build_recovery=importlib.util.module_from_spec(build_spec);build_spec.loader.exec_module(build_recovery)

class PinnedZipMetadata(unittest.TestCase):
    def test_zip64_local_header_points_to_actual_deflate_stream(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'zip64.zip';data=b'bounded ZIP64 recovery test'*400
            with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED) as archive:
                with archive.open('images/userdata.img','w',force_zip64=True) as dest:dest.write(data)
            offset,compressed,uncompressed,crc,method=build_recovery.zip_descriptor(path,'images/userdata.img',len(data))
            self.assertEqual(struct.unpack_from('<II',path.read_bytes(),18),(0xffffffff,0xffffffff))
            self.assertEqual(zlib.decompress(path.read_bytes()[offset:offset+compressed],-15),data)
            self.assertEqual(uncompressed,len(data));self.assertEqual(crc,zlib.crc32(data));self.assertEqual(method,8)
            with self.assertRaises(ValueError):build_recovery.zip_descriptor(path,'images/userdata.img',len(data)+1)

class SDDeploymentGuards(unittest.TestCase):
    def test_native_recovery_block_alias_is_accepted_by_device_identity(self):
        self.assertTrue(deploy_sd.external_sd_mounted('20 1 179:65 / /sdcard rw,nosuid - vfat /dev/leo-recovery-card rw'))
    def test_internal_readonly_and_bind_subdirectory_are_refused(self):
        for device,root,options in [('259:7','/','rw'),('179:65','/','ro'),('179:65','/folder','rw')]:
            self.assertFalse(deploy_sd.external_sd_mounted(f'20 1 {device} {root} /sdcard {options} - ext4 /dev/card rw'))

class RecoveryImageGuards(unittest.TestCase):
    def source(self):
        entries={}
        image.add(entries,'init',b'original init',0o100755)
        image.add(entries,'init.rc',b'service recovery /sbin/recovery\n',0o100644)
        raw=gzip.compress(image.cpio(entries),mtime=1)
        data=bytearray(33554432);data[:8]=b'ANDROID!'
        for at,value in [(8,4096),(16,len(raw)),(36,2048)]:struct.pack_into('<I',data,at,value)
        data[2048:6144]=b'K'*4096;data[6144:6144+len(raw)]=raw
        return bytes(data),entries
    def expanded(self,entries):
        result=entries.copy();image.add(result,'sbin/recovery',random.Random(73).randbytes(12000),0o100755)
        return result
    def test_roundtrip_preserves_kernel_init_and_new_file_modes(self):
        original,entries=self.source();new=self.expanded(entries)
        result=image.repack(original,new,b'')
        self.assertEqual(image.unpack(result)['entries'],new)
        self.assertEqual(result[2048:6144],original[2048:6144])
        self.assertEqual(len(result),33554432)
    def test_unidentified_nonzero_tail_cannot_be_overwritten(self):
        original,entries=self.source();dirty=bytearray(original);dirty[8192]=123
        with self.assertRaisesRegex(ValueError,'unidentified tail'):
            image.repack(bytes(dirty),self.expanded(entries),b'')
    def test_identified_stale_bytes_and_cleared_gaps_are_allowed(self):
        original,entries=self.source();dirty=bytearray(original);dirty[8192]=123
        known=bytearray(dirty);known[8193]=99 # previously cleared byte remains free
        image.repack(bytes(dirty),self.expanded(entries),bytes(known))
    def test_duplicate_addition_refused(self):
        _,entries=self.source()
        with self.assertRaises(ValueError):image.add(entries,'init',b'bad',0o100755)
    def test_traversal_refused(self):
        original,entries=self.source();image.add(entries,'../bad',b'bad',0o100755)
        raw=gzip.compress(image.cpio(entries),mtime=1);data=bytearray(original)
        struct.pack_into('<I',data,16,len(raw));data[6144:6144+len(raw)]=raw
        with self.assertRaisesRegex(ValueError,'Unsafe'):image.unpack(bytes(data))
    def test_bad_header_refused(self):
        with self.assertRaises(ValueError):image.unpack(bytes(33554432))

class KaiOSMountPlan(unittest.TestCase):
    SOURCE='''/dev/block/bootdevice/by-name/system /system ext4 ro,discard wait
/dev/block/bootdevice/by-name/userdata /data ext4 nosuid,nodev,discard wait,check,formattable,encryptable=footer
/devices/sd /storage/sdcard vfat nosuid,nodev wait,voldmanaged=sdcard:auto,encryptable=footer
'''
    def test_internal_os_targets_and_format_fallback_are_removed(self):
        result=sd.mount_plan(self.SOURCE)
        self.assertIn('/dev/block/loop0 /system',result)
        self.assertIn('/dev/block/loop1 /data',result)
        for unwanted in ('by-name/system','by-name/userdata','formattable','encryptable=footer','voldmanaged=sdcard'):
            self.assertNotIn(unwanted,result)
    def test_unrecognized_fstab_layout_refused(self):
        with self.assertRaises(ValueError):sd.mount_plan('/dev/x /data ext4 only-four-fields\n')

if __name__=='__main__':unittest.main()
