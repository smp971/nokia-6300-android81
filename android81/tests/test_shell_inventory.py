"""The documentation inventory must fail closed when the native registry changes."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("shell_inventory", Path(__file__).parents[1] / "audit-leo-shell.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class CatalogTests(unittest.TestCase):
    def source(self, entries, hidden="id==1"):
        return "Entry[] APPS = entries(new Entry[]{\n" + entries + "\n}); boolean settingsOnly(int id){return " + hidden + ";}"

    def test_current_manifest_registry_and_private_home(self):
        current = audit.inventory()
        self.assertEqual(len(current["catalog"]), 50)
        self.assertEqual(current["catalog"][48]["label_ro"], "keySIP")
        self.assertEqual(current["catalog"][49]["label_ro"], "WebUI")
        self.assertEqual(sum(row["hidden_from_grid"] for row in current["catalog"]), 14)
        self.assertEqual(current["catalog"][39]["extra"], {"key": "podcast", "value": True})
        self.assertTrue(any(row["name"] == "InternalHome" and row["exported"] == "false" for row in current["components"]))

    def test_explicit_ids_sort_labels_and_preserve_string_extra(self):
        rows = audit.catalog_entries(self.source('new Entry(1, "Vidéo", VideoActivity.class, "kind", "video"),\nnew Entry(0, "Meteo", WeatherActivity.class)'))
        self.assertEqual(rows[1]["label_ro"], "Vidéo")
        self.assertEqual(rows[1]["extra"], {"key": "kind", "value": "video"})

    def test_duplicate_id_rejected(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            audit.catalog_entries(self.source('new Entry(0, "A", FilesActivity.class),\nnew Entry(0, "B", FilesActivity.class)'))

    def test_gap_rejected(self):
        with self.assertRaisesRegex(ValueError, "contiguous"):
            audit.catalog_entries(self.source('new Entry(1, "A", FilesActivity.class)'))

    def test_unknown_java_expression_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unsupported catalog"):
            audit.catalog_entries(self.source('new Entry(0, "A", FilesActivity.class, "flag", arbitrary())'))

    def test_unknown_hidden_expression_rejected(self):
        with self.assertRaisesRegex(ValueError, "condition"):
            audit.catalog_entries(self.source('new Entry(0, "A", FilesActivity.class)', "id>=0"))

    def test_hidden_id_outside_registry_rejected(self):
        with self.assertRaisesRegex(ValueError, "outside"):
            audit.catalog_entries(self.source('new Entry(0, "A", FilesActivity.class)', "id==4"))


if __name__ == "__main__":
    unittest.main()
