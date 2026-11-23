"""Prepare a DT-only matrix-keypad wake fix. This tool never flashes a device."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import tempfile

TARGET = "/soc/matrix_keypad@0"


def require(ok, message):
    if not ok:
        raise ValueError(message)


def fdt_snapshot(blob):
    require(len(blob) >= 40, "Truncated FDT header")
    h = struct.unpack_from(">10I", blob)
    magic, total, tree, strings, reservations, version, compatible, cpu, names, size = h
    require(magic == 0xD00DFEED and total == len(blob), "Invalid FDT size/magic")
    require(version >= 17 and compatible <= 17, "Unsupported FDT version")
    require(tree + size <= total and strings + names <= total, "FDT bounds")
    end = reservations
    while True:
        require(end + 16 <= total, "Unterminated FDT reservations")
        pair = struct.unpack_from(">QQ", blob, end)
        end += 16
        if pair == (0, 0):
            break
    reserve = blob[reservations:end]
    stack, nodes, properties = [], set(), {}
    pos, finished = tree, False
    while pos + 4 <= tree + size:
        token = struct.unpack_from(">I", blob, pos)[0]
        pos += 4
        if token == 1:
            stop = blob.find(b"\0", pos, tree + size)
            require(stop >= pos, "Unterminated FDT node")
            stack.append(blob[pos:stop].decode("ascii"))
            path = "/" + "/".join(stack[1:])
            require(path not in nodes, "Duplicate FDT node")
            nodes.add(path)
            pos = (stop + 4) & ~3
        elif token == 2:
            require(stack, "Unbalanced FDT node")
            stack.pop()
        elif token == 3:
            require(stack and pos + 8 <= tree + size, "Invalid FDT property")
            length, name = struct.unpack_from(">II", blob, pos)
            pos += 8
            require(name < names and pos + length <= tree + size, "FDT property bounds")
            stop = blob.find(b"\0", strings + name, strings + names)
            require(stop >= strings + name, "Unterminated FDT property name")
            key = ("/" + "/".join(stack[1:]), blob[strings + name:stop].decode("ascii"))
            require(key not in properties, "Duplicate FDT property")
            properties[key] = blob[pos:pos + length]
            pos = (pos + length + 3) & ~3
        elif token == 4:
            pass
        elif token == 9:
            require(not stack, "Unclosed FDT node")
            finished = True
            break
        else:
            raise ValueError("Unknown FDT token")
    require(finished, "Missing FDT end")
    return nodes, properties, reserve, (version, compatible, cpu)


def boot_parts(image):
    require(len(image) == 33554432 and image[:8] == b"ANDROID!", "Expected 32 MiB Android boot")
    k, ka, r, ra, s, sa, tags, page, dt, unused = struct.unpack_from("<10I", image, 8)
    require(page == 2048 and s == dt == unused == 0, "Unsupported boot layout")
    align = lambda n: (n + page - 1) // page * page
    require(k >= 48 and page + align(k) + align(r) <= len(image), "Boot bounds")
    kernel = image[page:page + k]
    require(struct.unpack_from("<I", kernel, 0x24)[0] == 0x016F2818, "Expected ARM zImage")
    zsize = struct.unpack_from("<I", kernel, 0x2c)[0] - struct.unpack_from("<I", kernel, 0x28)[0]
    require(48 <= zsize < len(kernel), "Missing appended DTBs")
    ramdisk = image[page + align(k):page + align(k) + r]
    require(not any(image[page + align(k) + align(r):]), "Unexpected boot trailer")
    require(image[576:596] == boot_digest(kernel, ramdisk), "Original boot digest mismatch")
    return kernel[:zsize], kernel[zsize:], ramdisk, page


def boot_digest(kernel, ramdisk):
    digest = hashlib.sha1()
    for part in (kernel, ramdisk, b""):
        digest.update(part)
        digest.update(struct.pack("<I", len(part)))
    return digest.digest()


def prepare(image, edit):
    code, tables, ramdisk, page = boot_parts(image)
    result, records, offset = bytearray(), [], 0
    while offset < len(tables):
        require(offset + 40 <= len(tables), "Truncated appended DTB")
        length = struct.unpack_from(">I", tables, offset + 4)[0]
        require(40 <= length <= len(tables) - offset, "Appended DTB bounds")
        old = tables[offset:offset + length]
        before = fdt_snapshot(old)
        updated = old
        has_target = TARGET in before[0]
        if has_target:
            require(b"gpio-matrix-keypad" in before[1].get((TARGET, "compatible"), b"").split(b"\0"),
                    "Unexpected matrix keypad driver")
            if (TARGET, "wakeup-source") not in before[1]:
                updated = edit(old)
        after = fdt_snapshot(updated)
        expected = dict(before[1])
        if has_target:
            expected[(TARGET, "wakeup-source")] = before[1].get((TARGET, "wakeup-source"), b"")
        require(after[0] == before[0] and after[1] == expected and after[2:] == before[2:],
                "DT editor changed more than the keypad wake property")
        records.append({"index": len(records), "matrix_keypad": has_target,
                        "changed": old != updated, "before_sha256": hashlib.sha256(old).hexdigest(),
                        "after_sha256": hashlib.sha256(updated).hexdigest()})
        result.extend(updated)
        offset += length
    require(any(row["changed"] for row in records), "No keypad wake fix was needed")
    kernel = code + result
    align = lambda n: (n + page - 1) // page * page
    start = page + align(len(kernel))
    require(start + align(len(ramdisk)) <= len(image), "Patched boot exceeds partition")
    output = bytearray(image[:page] + bytes(len(image) - page))
    output[page:page + len(kernel)] = kernel
    output[start:start + len(ramdisk)] = ramdisk
    struct.pack_into("<I", output, 8, len(kernel))
    output[576:596] = boot_digest(kernel, ramdisk)
    require(output[12:576] == image[12:576] and output[596:page] == image[596:page],
            "Boot addresses/cmdline/header changed")
    new_code, _, new_ramdisk, _ = boot_parts(output)
    require(new_code == code and new_ramdisk == ramdisk, "Kernel code or ramdisk changed")
    metadata = {"previous_boot_sha256": hashlib.sha256(image).hexdigest(),
                "boot_sha256": hashlib.sha256(output).hexdigest(), "bytes": len(output),
                "kernel_code_unchanged": True, "ramdisk_unchanged": True,
                "header_addresses_and_cmdline_unchanged": True,
                "only_property_change": TARGET + "/wakeup-source", "dtbs": records,
                "flashed": False, "rebooted": False}
    return bytes(output), metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--fdtput", type=Path, required=True)
    args = parser.parse_args()
    require(args.input.resolve() != args.output.resolve(), "Refusing to overwrite backup")
    image = args.input.read_bytes()
    require(hashlib.sha256(image).hexdigest() == args.expected_sha256, "Unexpected input boot hash")
    with tempfile.TemporaryDirectory(prefix="keypad-dtb-") as temp:
        path = Path(temp) / "keypad.dtb"
        def edit(blob):
            path.write_bytes(blob)
            subprocess.run([str(args.fdtput.resolve()), str(path), TARGET, "wakeup-source"], check=True)
            return path.read_bytes()
        output, metadata = prepare(image, edit)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(output)
    args.output.with_suffix(".json").write_text(json.dumps(metadata, indent=2) + "\n")
    print(json.dumps({k: v for k, v in metadata.items() if k != "dtbs"}, indent=2))
    print("DTBs changed:", sum(row["changed"] for row in metadata["dtbs"]), "/", len(metadata["dtbs"]))


if __name__ == "__main__":
    main()
