import hashlib
import importlib.util
import json
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('apn_catalog', ROOT/'build-apn-catalog.py')
catalog = importlib.util.module_from_spec(spec)
spec.loader.exec_module(catalog)


class ApnCatalogueTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path = ROOT/'device/nokia/leo/apns-conf.xml'
        cls.rows = list(ET.parse(cls.path).getroot())

    def test_source_and_output_integrity(self):
        report = json.loads(self.path.with_suffix('.source.json').read_text())
        self.assertEqual(hashlib.sha256(self.path.read_bytes()).hexdigest(), report['output_sha256'])
        self.assertEqual(len(self.rows), report['profiles'])
        self.assertEqual(report['source_sha256'], catalog.SHA256)

    def test_all_eu_member_states_have_profiles(self):
        present = {catalog.COUNTRIES[r.get('mcc')] for r in self.rows}
        eu = set('AT BE BG HR CY CZ DK EE FI FR DE GR HU IE IT LV LT LU MT NL PL PT RO SK SI ES SE'.split())
        self.assertFalse(eu-present)
        self.assertTrue(present <= catalog.SCOPE)

    def test_romanian_internet_and_ims_are_distinct(self):
        ro = [r for r in self.rows if r.get('mcc') == '226']
        for mnc, apn in [('01','live.vodafone.com'),('03','broadband'),('05','internet'),('06','broadband'),('10','net')]:
            self.assertTrue(any(r.get('mnc') == mnc and r.get('apn') == apn and 'default' in r.get('type','').split(',') for r in ro))
        for mnc in ['01','05','10']:
            self.assertTrue(any(r.get('mnc') == mnc and r.get('type') == 'ims' and r.get('user_visible') == 'false' for r in ro))

    def test_oreo_schema_and_radio_constraints(self):
        self.assertEqual(ET.parse(self.path).getroot().get('version'), '8')
        for row in self.rows:
            self.assertFalse(set(row.attrib)-catalog.SUPPORTED)
            self.assertRegex(row.get('mcc'), r'^\d{3}$')
            self.assertRegex(row.get('mnc'), r'^\d{2,3}$')
            for key in ['protocol','roaming_protocol']:
                if key in row.attrib:
                    self.assertIn(row.get(key), ['IP','IPV6','IPV4V6','PPP'])
            if row.get('bearer_bitmask'):
                self.assertTrue(all(1 <= int(v) <= 19 for v in row.get('bearer_bitmask').split('|')))

    def test_mvno_matching_is_not_widened(self):
        for row in self.rows:
            self.assertEqual(bool(row.get('mvno_type')), bool(row.get('mvno_match_data')))
        for apn in ['internet.digimobil.es','digi.mobile']:
            match = [r for r in self.rows if r.get('mcc') == '226' and r.get('apn') == apn]
            self.assertTrue(match)
            self.assertTrue(all(r.get('mvno_type') == 'gid' and r.get('mvno_match_data') for r in match))

    def test_builder_rejects_unpinned_source(self):
        with self.assertRaisesRegex(ValueError, 'SHA-256'):
            catalog.build(b'<apns version="8"/>')


if __name__ == '__main__':
    unittest.main()
