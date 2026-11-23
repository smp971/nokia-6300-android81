"""Boot safety boundaries: only the matrix wake property may change."""
import hashlib
import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location("wake_boot", Path(__file__).parents[1] / "patch-keypad-wake-boot.py")
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)
installer_spec = importlib.util.spec_from_file_location("wake_install", Path(__file__).parents[1] / "install-keypad-wake-boot.py")
installer = importlib.util.module_from_spec(installer_spec)
installer_spec.loader.exec_module(installer)


def dtb(wake=False, other=False, matrix=True):
    names = b"compatible\0linux,keymap_0\0wakeup-source\0model\0"
    tree = bytearray()
    def word(n):
        tree.extend(struct.pack(">I", n))
    def node(name):
        word(1); tree.extend(name.encode() + b"\0"); tree.extend(bytes(-len(tree) % 4))
    def prop(name, value):
        word(3); word(len(value)); word(names.index(name.encode() + b"\0"))
        tree.extend(value); tree.extend(bytes(-len(tree) % 4))
    node(""); prop("model", b"unchanged board\0")
    if matrix:
        node("soc"); node("matrix_keypad@0")
        prop("compatible", b"gpio-matrix-keypad\0")
        prop("linux,keymap_0", b"other map" if other else b"\0\x02\0\x8b")
        if wake:
            prop("wakeup-source", b"")
        word(2); word(2)
    word(2); word(9)
    reserve = struct.pack(">QQQQ", 0x80000000, 4096, 0, 0)
    start = 40 + len(reserve)
    header = struct.pack(">10I", 0xD00DFEED, start + len(tree) + len(names),
                         start, start + len(tree), 40, 17, 16, 0, len(names), len(tree))
    return header + reserve + tree + names


def boot(tables=None):
    image = bytearray(33554432)
    image[:8] = b"ANDROID!"
    code = bytearray(64)
    struct.pack_into("<III", code, 0x24, 0x016F2818, 0, 64)
    kernel = bytes(code) + (tables if tables is not None else dtb())
    ramdisk = b"unchanged ramdisk fixture"
    struct.pack_into("<10I", image, 8, len(kernel), 0x80008000, len(ramdisk),
                     0x81000000, 0, 0, 0x80000100, 2048, 0, 0)
    image[64:79] = b"unchanged args\0"
    image[2048:2048 + len(kernel)] = kernel
    start = 2048 + (len(kernel) + 2047) // 2048 * 2048
    image[start:start + len(ramdisk)] = ramdisk
    digest = hashlib.sha1()
    for data in (kernel, ramdisk, b""):
        digest.update(data); digest.update(struct.pack("<I", len(data)))
    image[576:596] = digest.digest()
    return bytes(image)


class KeypadWakeBootTests(unittest.TestCase):
    def test_keeps_kernel_ramdisk_addresses_cmdline_and_reservations(self):
        original = boot()
        changed, report = patch.prepare(original, lambda blob: dtb(wake=True))
        before = patch.boot_parts(original); after = patch.boot_parts(changed)
        self.assertEqual(before[0], after[0])
        self.assertEqual(before[2:], after[2:])
        self.assertEqual(original[12:576], changed[12:576])
        self.assertEqual(original[596:2048], changed[596:2048])
        self.assertEqual(len(changed), 33554432)
        self.assertEqual(patch.fdt_snapshot(before[1])[2:], patch.fdt_snapshot(after[1])[2:])
        self.assertFalse(report["flashed"])
        self.assertFalse(report["rebooted"])

    def test_leaves_other_board_dtbs_byte_identical(self):
        other = dtb(matrix=False)
        changed, report = patch.prepare(boot(dtb() + other), lambda blob: dtb(wake=True))
        self.assertTrue(patch.boot_parts(changed)[1].endswith(other))
        self.assertEqual([row["changed"] for row in report["dtbs"]], [True, False])

    def test_rejects_keymap_change_hidden_in_dtb_editor(self):
        with self.assertRaisesRegex(ValueError, "more than"):
            patch.prepare(boot(), lambda blob: dtb(wake=True, other=True))

    def test_rejects_already_enabled_image(self):
        with self.assertRaisesRegex(ValueError, "No keypad wake fix"):
            patch.prepare(boot(dtb(wake=True)), lambda blob: self.fail("Editor should not run"))

    def test_rejects_boot_digest_corruption(self):
        image = bytearray(boot()); image[2048 + 4] ^= 1
        with self.assertRaisesRegex(ValueError, "digest"):
            patch.prepare(image, lambda blob: dtb(wake=True))

    def test_rejects_unrecognized_trailer(self):
        image = bytearray(boot()); image[-1] = 1
        with self.assertRaisesRegex(ValueError, "trailer"):
            patch.prepare(image, lambda blob: dtb(wake=True))

    def test_rejects_wrong_partition_size(self):
        with self.assertRaisesRegex(ValueError, "32 MiB"):
            patch.prepare(b"ANDROID!" + bytes(2048), lambda blob: dtb(wake=True))

    def test_rejects_truncated_device_tree(self):
        with self.assertRaisesRegex(ValueError, "size/magic"):
            patch.fdt_snapshot(dtb()[:-1])

    def test_installer_independently_accepts_reviewed_dtb_only_image(self):
        original = boot()
        changed, _ = patch.prepare(original, lambda blob: dtb(wake=True))
        self.assertEqual(installer.validate(original, changed), 1)

    def test_installer_rejects_kernel_code_change_even_with_valid_digest(self):
        original = boot()
        changed, _ = patch.prepare(original, lambda blob: dtb(wake=True))
        changed = bytearray(changed); changed[2048 + 4] ^= 1
        ksize, rsize = struct.unpack_from("<I", changed, 8)[0], struct.unpack_from("<I", changed, 16)[0]
        ram_start = 2048 + (ksize + 2047) // 2048 * 2048
        changed[576:596] = patch.boot_digest(changed[2048:2048 + ksize], changed[ram_start:ram_start + rsize])
        with self.assertRaisesRegex(ValueError, "Kernel code"):
            installer.validate(original, changed)

    def test_installer_rejects_changed_boot_arguments(self):
        original = boot()
        changed, _ = patch.prepare(original, lambda blob: dtb(wake=True))
        changed = bytearray(changed); changed[64] ^= 1
        with self.assertRaisesRegex(ValueError, "header"):
            installer.validate(original, changed)


if __name__ == "__main__":
    unittest.main()
