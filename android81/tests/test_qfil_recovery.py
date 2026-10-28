import importlib.util, struct, unittest, zlib
from pathlib import Path
import xml.etree.ElementTree as ET

spec=importlib.util.spec_from_file_location('qfil',Path(__file__).parents[1]/'build-qfil-recovery.py')
qfil=importlib.util.module_from_spec(spec);spec.loader.exec_module(qfil)

def gpt():
    data=bytearray(34*512);h=memoryview(data)[512:1024]
    h[:8]=b'EFI PART';struct.pack_into('<II',h,8,0x10000,92)
    struct.pack_into('<Q',h,24,1);struct.pack_into('<QII',h,72,2,40,128)
    for name,(index,first,count) in qfil.GEOMETRY.items():
        at=1024+(index-1)*128;data[at:at+16]=b'\x01'*16
        struct.pack_into('<QQ',data,at+32,first,first+count-1)
        encoded=name.encode('utf-16le');data[at+56:at+56+len(encoded)]=encoded
    struct.pack_into('<I',h,88,zlib.crc32(data[1024:1024+40*128]))
    struct.pack_into('<I',h,16,zlib.crc32(h[:92]))
    return data

class QfilRecoveryGuards(unittest.TestCase):
    def test_only_recovery_is_emitted(self):
        rows=ET.fromstring(qfil.rawprogram(qfil.read_gpt(gpt())['recovery'])).findall('program')
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0].get('label'),'recovery')
        self.assertEqual(int(rows[0].get('start_sector')),2488416)
        self.assertEqual(int(rows[0].get('num_partition_sectors'))*512,33554432)
    def test_other_write_targets_are_refused(self):
        for name,value in qfil.GEOMETRY.items():
            if name!='recovery':
                with self.assertRaises(ValueError):qfil.rawprogram(value)
    def test_corrupt_header_and_array_are_refused(self):
        for at in (530,1100):
            data=gpt();data[at]^=1
            with self.assertRaises(ValueError):qfil.read_gpt(data)
    def test_rechecks_geometry_even_with_valid_crcs(self):
        data=gpt();struct.pack_into('<Q',data,1024+32*128+32,2488417)
        struct.pack_into('<I',data,512+88,zlib.crc32(data[1024:1024+40*128]))
        struct.pack_into('<I',data,512+16,0)
        struct.pack_into('<I',data,512+16,zlib.crc32(data[512:604]))
        with self.assertRaisesRegex(ValueError,'geometry'):qfil.read_gpt(data)

if __name__=='__main__':unittest.main()
