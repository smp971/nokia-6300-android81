import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('bundle_installer', ROOT / 'install-app-bundle.py')
bundle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bundle)


class BundleChecks(unittest.TestCase):
    def fixture(self, folder):
        files = {}
        for name in ['leo-visual.apk', 'leo-keypad-ime.apk', 'install-app-bundle.py', 'README.txt']:
            content = ('local fixture: ' + name).encode()
            (folder / name).write_bytes(content)
            files[name] = {'bytes': len(content), 'sha256': hashlib.sha256(content).hexdigest()}
        data = {'format': 'leo-app-bundle', 'schema': 1, 'version': '0.1.0-dev', 'files': files}
        (folder / 'manifest.json').write_text(json.dumps(data))
        return data

    def test_valid_bundle(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            self.fixture(folder)
            self.assertEqual(bundle.verify(folder)['version'], '0.1.0-dev')

    def test_changed_apk_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            self.fixture(folder)
            (folder / 'leo-visual.apk').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'mismatch'):
                bundle.verify(folder)

    def test_unexpected_paths_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            data = self.fixture(folder)
            data['files']['../other.apk'] = data['files'].pop('leo-visual.apk')
            (folder / 'manifest.json').write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, 'Unexpected'):
                bundle.verify(folder)

    def test_unknown_format_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            data = self.fixture(folder)
            data['schema'] = 99
            (folder / 'manifest.json').write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, 'Unsupported'):
                bundle.verify(folder)

    def test_install_requires_serial_before_adb(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            self.fixture(folder)
            result = subprocess.run([sys.executable, str(ROOT / 'install-app-bundle.py'),
                                     '--bundle', str(folder), '--install', '--adb', 'nonexistent-fixture-adb'],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn('requires --serial', result.stderr)


if __name__ == '__main__':
    unittest.main()
