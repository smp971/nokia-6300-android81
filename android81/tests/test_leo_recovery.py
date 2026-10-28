import gzip, importlib.util, random, struct, sys, unittest
from pathlib import Path

ROOT=Path(__file__).parents[1];sys.path.insert(0,str(ROOT))
import recovery_image as image
spec=importlib.util.spec_from_file_location('kaios_sd',ROOT/'prepare-kaios-sd.py')
sd=importlib.util.module_from_spec(spec);spec.loader.exec_module(sd)

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
