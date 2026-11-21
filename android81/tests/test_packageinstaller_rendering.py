import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location("installer_rendering", Path(__file__).parents[1] / "packageinstaller_rendering.py")
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)


def pool(values, utf8=False):
    data, offsets = b"", []
    def length(value):
        return bytes([value]) if value < 128 else bytes([128 | (value >> 8), value & 255])
    for value in values:
        offsets.append(len(data))
        encoded = value.encode("utf-8" if utf8 else "utf-16-le")
        units = len(value.encode("utf-16-le")) // 2
        data += length(units) + length(len(encoded)) + encoded + b"\0" if utf8 else struct.pack("<H", units) + encoded + b"\0\0"
    data += b"\0" * (-len(data) % 4)
    start = 28 + 4 * len(values)
    return struct.pack("<HHIIIIII", 1, 28, start + len(data), len(values), 0, 256 if utf8 else 0, start, 0) + struct.pack("<" + "I" * len(values), *offsets) + data


def node(kind, body, comment=patch.NONE):
    return struct.pack("<HHIII", kind, 16, 16 + len(body), 1, comment) + body


def document(parts):
    data = b"".join(parts)
    return struct.pack("<HHI", 3, 8, len(data) + 8) + data


def fixture(utf8=False, application=None, activity=None):
    resource_names = ["theme", "name", "allowBackup", "supportsRtl"]
    ids = [0x01010000, 0x01010003, 0x01010280, 0x010103AF]
    if application is not None or activity is not None:
        resource_names += ["hardwareAccelerated"]
        ids += [patch.HARDWARE]
    names = resource_names + [patch.ANDROID, "android", "manifest", "package", "org.fixture", "coreApp", "application", "activity", "payload🌤" + "x" * 150]
    index = {name: at for at, name in enumerate(names)}
    namespace = index[patch.ANDROID]
    def attr(name, kind, value, android=True, raw=patch.NONE):
        return struct.pack("<IIIHBBI", namespace if android else patch.NONE, index[name], raw, 8, 0, kind, value)
    def start(name, attrs, style=0):
        return node(0x102, struct.pack("<IIHHHHHH", patch.NONE, index[name], 20, 20, len(attrs), 0, 0, style) + b"".join(attrs), len(names) - 1)
    def end(name):
        return node(0x103, struct.pack("<II", patch.NONE, index[name]))
    app_attrs = [attr("theme", 1, 0x7F100123), attr("name", 3, index["org.fixture"], raw=index["org.fixture"]), attr("allowBackup", 0x12, 0), attr("supportsRtl", 0x12, 0xFFFFFFFF)]
    if application is not None:
        app_attrs.append(attr("hardwareAccelerated", 0x12, 0xFFFFFFFF if application else 0))
    app_attrs.sort(key=lambda value: ids[struct.unpack_from("<I", value, 4)[0]])
    style = next(i + 1 for i, value in enumerate(app_attrs) if struct.unpack_from("<I", value, 4)[0] == index["supportsRtl"])
    parts = [pool(names, utf8), struct.pack("<HHI", 0x180, 8, 8 + 4 * len(ids)) + struct.pack("<" + "I" * len(ids), *ids),
             node(0x100, struct.pack("<II", index["android"], namespace)),
             start("manifest", [attr("package", 3, index["org.fixture"], False, index["org.fixture"]), attr("coreApp", 0x12, 0xFFFFFFFF, False)]),
             start("application", app_attrs, style)]
    if activity is not None:
        parts += [start("activity", [attr("hardwareAccelerated", 0x12, 0xFFFFFFFF if activity else 0)]), end("activity")]
    parts += [node(0x104, struct.pack("<IHBBI", len(names) - 1, 8, 0, 3, len(names) - 1), len(names) - 1), end("application"), end("manifest"),
              node(0x101, struct.pack("<II", index["android"], namespace))]
    return document(parts)


def read(blob):
    parts = patch.chunks(blob)
    names = patch.strings(next(value for kind, value in parts if kind == 1))
    mapping = next(value for kind, value in parts if kind == 0x180)
    ids = struct.unpack_from("<" + "I" * ((len(mapping) - 8) // 4), mapping, 8)
    elements = {}
    for kind, value in parts:
        if kind != 0x102:
            continue
        name = names[struct.unpack_from("<I", value, 20)[0]]
        _, attrs = patch.attributes(value)
        decoded = {}
        for item in attrs:
            ns, key, raw = struct.unpack_from("<III", item)
            data = struct.unpack_from("<I", item, 16)[0]
            decoded[names[key]] = (names[ns] if ns != patch.NONE else None, names[raw] if raw != patch.NONE else None, item[15], names[data] if item[15] == 3 else data)
            if names[key] in {"package", "coreApp"}:
                assert key >= len(ids), "Unmapped manifest names must not acquire resource IDs"
        elements[name] = (decoded, attrs, value)
    return parts, names, ids, elements


def title_fixture(style=0x010102F8):
    names = ["id", "textAppearance", patch.ANDROID, "android", "LinearLayout", "TextView"]
    ids = [0x010100D0, 0x01010034]
    def element(name, attrs):
        return node(0x102, struct.pack("<IIHHHHHH", patch.NONE, names.index(name), 20, 20, len(attrs), 0, 0, 0) + b"".join(attrs))
    def text(identity):
        attrs = [struct.pack("<IIIHBBI", 2, 1, patch.NONE, 8, 0, 2, style), struct.pack("<IIIHBBI", 2, 0, patch.NONE, 8, 0, 1, identity)]
        return [element("TextView", attrs), node(0x103, struct.pack("<II", patch.NONE, 5))]
    return document([pool(names), struct.pack("<HHIII", 0x180, 8, 16, *ids),
        node(0x100, struct.pack("<II", 3, 2)), element("LinearLayout", []),
        *text(0x7F0A0024), *text(0x7F0A0025), node(0x103, struct.pack("<II", patch.NONE, 4)), node(0x101, struct.pack("<II", 3, 2))])


class RenderingPatchTests(unittest.TestCase):
    def test_utf8_and_utf16_preserve_semantics_and_unmapped_names(self):
        for utf8 in (False, True):
            with self.subTest(utf8=utf8):
                original = fixture(utf8)
                before, _, old_ids, old = read(original)
                after, names, ids, new = read(patch.binary_manifest(original))
                self.assertEqual(ids, (*old_ids, patch.HARDWARE))
                self.assertEqual(old["manifest"][0], new["manifest"][0])
                attrs = dict(new["application"][0])
                self.assertEqual(attrs.pop("hardwareAccelerated"), (patch.ANDROID, None, 0x12, 0))
                self.assertEqual(old["application"][0], attrs)
                self.assertEqual(struct.unpack_from("<H", new["application"][2], 34)[0], 5)
                cdata = next(value for kind, value in after if kind == 0x104)
                self.assertEqual(names[struct.unpack_from("<I", cdata, 16)[0]], "payload🌤" + "x" * 150)
                self.assertEqual(names[struct.unpack_from("<I", cdata, 24)[0]], "payload🌤" + "x" * 150)
                namespace = next(value for kind, value in after if kind == 0x100)
                self.assertEqual(names[struct.unpack_from("<I", namespace, 20)[0]], patch.ANDROID)

    def test_binary_change_is_idempotent(self):
        changed = patch.binary_manifest(fixture())
        self.assertEqual(patch.binary_manifest(changed), changed)

    def test_existing_true_changes_only_the_application_node(self):
        source = fixture(application=True)
        before = patch.chunks(source)
        after = patch.chunks(patch.binary_manifest(source))
        self.assertEqual(sum(old != new for old, new in zip(before, after)), 1)
        self.assertEqual(read(patch.binary_manifest(source))[3]["application"][0]["hardwareAccelerated"][-1], 0)

    def test_activity_override_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "re-enables"):
            patch.binary_manifest(fixture(activity=True))

    def test_styled_manifest_is_rejected(self):
        data = bytearray(fixture())
        struct.pack_into("<I", data, 8 + 12, 1)
        with self.assertRaises(ValueError):
            patch.binary_manifest(bytes(data))

    def test_broken_chunk_is_rejected(self):
        data = bytearray(fixture())
        struct.pack_into("<I", data, 8 + 4, 0)
        with self.assertRaises(ValueError):
            patch.binary_manifest(bytes(data))

    def test_source_manifest_preserves_permissions_and_is_idempotent(self):
        source = '<manifest><uses-permission android:name="permission"/><application android:label="App"><activity android:name="A"/></application></manifest>'
        changed = patch.source_manifest(source)
        self.assertIn('<uses-permission android:name="permission"/>', changed)
        self.assertEqual(changed.replace(' android:hardwareAccelerated="false"', ''), source)
        self.assertEqual(patch.source_manifest(changed), changed)

    def test_source_application_override_and_invalid_count(self):
        self.assertIn('hardwareAccelerated="false"', patch.source_manifest('<application android:hardwareAccelerated="true"/>'))
        with self.assertRaises(ValueError):
            patch.source_manifest('<application/><application/>')
        with self.assertRaisesRegex(ValueError, "re-enables"):
            patch.source_manifest('<application><activity android:hardwareAccelerated="true"/></application>')

    def test_source_title_only_changes_the_app_name(self):
        source = '<TextView android:id="@+id/app_name" android:textAppearance="?android:attr/titleTextStyle"/><TextView android:id="@id/other" android:textAppearance="?android:attr/titleTextStyle"/>'
        changed = patch.source_layout(source)
        self.assertEqual(changed.count('titleTextStyle'), 1)
        self.assertEqual(changed.count('textAppearanceLarge'), 1)
        self.assertEqual(patch.source_layout(changed), changed)
        with self.assertRaises(ValueError):
            patch.source_layout('<TextView android:id="@id/other"/>')

    def test_binary_title_preserves_every_other_node_and_attribute(self):
        original = title_fixture()
        changed = patch.binary_title_style(original, 0x7F0A0024)
        before, after = patch.chunks(original), patch.chunks(changed)
        different = [(a, b) for a, b in zip(before, after) if a != b]
        self.assertEqual(len(different), 1)
        self.assertEqual(len(original), len(changed))
        self.assertLessEqual(sum(a != b for a, b in zip(original, changed)), 4)
        _, attrs = patch.attributes(different[0][1][1])
        self.assertEqual(struct.unpack_from("<I", attrs[0], 16)[0], 0x01010040)
        self.assertEqual(patch.binary_title_style(changed, 0x7F0A0024), changed)

    def test_binary_title_refuses_wrong_id_or_unknown_style(self):
        with self.assertRaises(ValueError):
            patch.binary_title_style(title_fixture(), 0x7F0A9999)
        with self.assertRaises(ValueError):
            patch.binary_title_style(title_fixture(0x01010038), 0x7F0A0024)
