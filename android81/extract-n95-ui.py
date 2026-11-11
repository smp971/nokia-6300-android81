"""Extract local Nokia N95 RM-159 ROM files and inventory the firmware layers."""

import argparse
import csv
import hashlib
import re
import struct
from pathlib import Path


RECORD_FIELDS = {0x17: 6, 0x27: 37, 0x28: 37, 0x2e: 17}
MAX_IMAGE = 128 * 1024 * 1024


def u16(data, offset):
    return struct.unpack_from('<H', data, offset)[0]


def u32(data, offset):
    return struct.unpack_from('<I', data, offset)[0]


def read_fpsx(path):
    if not 6 <= path.stat().st_size <= MAX_IMAGE:
        raise ValueError(f'Unexpected firmware file size: {path.name}')
    data = path.read_bytes()
    if data[0] != 0xb2:
        raise ValueError(f'Unexpected firmware signature: {path.name}')
    offset = 5 + struct.unpack_from('>I', data, 1)[0]
    records = []
    while offset < len(data):
        if offset + 5 > len(data):
            raise ValueError('Truncated record header')
        kind, header_size = data[offset + 2:offset + 4]
        field = RECORD_FIELDS.get(kind)
        if field is None or header_size < field + 8:
            raise ValueError(f'Unexpected record type at {offset:#x}: {kind:#x}')
        header = offset + 4
        payload = header + header_size + 1
        length, address = struct.unpack_from('>II', data, header + field)
        if payload + length > len(data):
            raise ValueError('Truncated record payload')
        records.append((kind, data[header], address, payload, length))
        offset = payload + length
    return data, records


def assemble(data, records, kind, chip, start, size):
    if not 0 < size <= MAX_IMAGE:
        raise ValueError(f'Unexpected image size: {size}')
    image = bytearray(size)
    for record_type, device, address, payload, length in records:
        if record_type != kind or device != chip:
            continue
        lower = max(start, address)
        upper = min(start + size, address + length)
        if lower < upper:
            image[lower - start:upper - start] = data[payload + lower - address:payload + upper - address]
    return image


def find_rofs(data, records):
    for kind, chip, address, payload, length in records:
        if kind != 0x17:
            continue
        for match in re.finditer(rb'ROF[Sx]\x30\xff\x00\x02', data[payload:payload + length]):
            header = payload + match.start()
            size = u32(data, header + 36)
            if 48 < size <= MAX_IMAGE:
                return assemble(data, records, kind, chip, address + match.start(), size)
    raise ValueError('ROFS header not found')


def safe_name(raw):
    name = raw.decode('utf-16-le')
    if (not name or name in ('.', '..') or name.endswith((' ', '.')) or
            any(c in '\\/:*?"<>|' or ord(c) < 32 for c in name)):
        raise ValueError(f'Invalid filename: {name!r}')
    return name


def rofs_files(image):
    tree = u32(image, 8)
    base = tree - 48
    visited = set()

    def entries(start, size):
        end = start + size
        if not 0 <= start <= end <= len(image):
            raise ValueError('ROFS entries outside image')
        while start + 30 <= end:
            span = u16(image, start)
            if span in (0, 0xffff):
                return
            name_offset, chars = image[start + 18], image[start + 29]
            if not 30 <= name_offset <= name_offset + chars * 2 <= span or start + span > end:
                raise ValueError(f'Invalid ROFS entry at {start:#x}')
            name_start = start + name_offset
            yield safe_name(image[name_start:name_start + chars * 2]), u32(image, start + 20), u32(image, start + 24)
            start = (start + span + 3) & ~3

    def walk(path, address):
        offset = address - base
        if offset in visited or not 0 <= offset <= len(image) - 12 or len(path) >= 32:
            raise ValueError('Invalid ROFS directory')
        visited.add(offset)
        first = image[offset + 3]
        for name, _, child in entries(offset + first, max(0, u16(image, offset) - first + 2)):
            yield from walk(path + (name,), child)
        file_table, table_size = struct.unpack_from('<II', image, offset + 4)
        if file_table:
            for name, size, data in entries(file_table - base, table_size + 2):
                position = data - base
                if data in (0, 0xffffffff) and size:
                    yield path + (name,), size, None, 'overlay marker'
                elif not 0 <= position <= position + size <= len(image) and size:
                    yield path + (name,), size, None, 'external reference'
                else:
                    yield path + (name,), size, image[position:position + size] if size else b'', 'extracted'

    return walk((), tree)


def extract_rofs(path, folder):
    data, records = read_fpsx(path)
    image = find_rofs(data, records)
    return write_files(folder, rofs_files(image))


def bytepair_page(data):
    count = data[0]
    offset = 1
    pairs = {}
    marker = None
    if count:
        marker = data[offset]
        offset += 1
        if count < 32:
            for _ in range(count):
                token, left, right = data[offset:offset + 3]
                pairs[token] = (left, right)
                offset += 3
        else:
            mask = data[offset:offset + 32]
            offset += 32
            for token in range(256):
                if mask[token // 8] & (1 << (token % 8)):
                    pairs[token] = tuple(data[offset:offset + 2])
                    offset += 2
            if len(pairs) != count:
                raise ValueError('Invalid byte-pair table')
    expanded = {}

    def expand(token, visiting):
        if token not in pairs:
            return bytes((token,))
        if token not in expanded:
            if token in visiting or len(visiting) > 64:
                raise ValueError('Cyclic byte-pair table')
            left, right = pairs[token]
            branch = visiting | {token}
            value = expand(left, branch) + expand(right, branch)
            if len(value) > 4096:
                raise ValueError('Oversized byte-pair token')
            expanded[token] = value
        return expanded[token]

    result = bytearray()
    while offset < len(data):
        token = data[offset]
        offset += 1
        if token == marker:
            result.append(data[offset])
            offset += 1
        else:
            result.extend(expand(token, set()))
        if len(result) > 4096:
            raise ValueError('Oversized ROM page')
    if len(result) != 4096:
        raise ValueError(f'Incomplete ROM page: {len(result)} bytes')
    return result


def unpack_xip(source, records):
    # RM-159 core: 0x400-byte BB5 certificate wrapper, then a 0xc00-byte bootstrap.
    start = 0xa41000
    header = assemble(source, records, 0x17, 1, start, 512)
    base, _, root = struct.unpack_from('<III', header, 140)
    compression, packed, size = struct.unpack_from('<III', header, 236)
    first_page, _, page_table = struct.unpack_from('<III', header, 328)
    if not (base == 0x80000000 and base < root < base + size and compression == 0
            and 0 < page_table < first_page <= packed < size <= MAX_IMAGE
            and first_page % 4096 == size % 4096 == 0):
        raise ValueError('Unexpected RM-159 ROM header')
    packed_image = assemble(source, records, 0x17, 1, start, packed)
    unpacked = bytearray(packed_image[:first_page])
    for index in range(first_page // 4096, size // 4096):
        offset, length, algorithm, flags = struct.unpack_from('<IHBB', packed_image, page_table + 8 * index)
        if not flags & 1 or offset + length > len(packed_image):
            raise ValueError(f'Invalid ROM page {index}')
        if length == 0:
            unpacked.extend(b'\xff' * 4096)
        elif algorithm == 0 and length == 4096:
            unpacked.extend(packed_image[offset:offset + length])
        elif algorithm == 1:
            unpacked.extend(bytepair_page(packed_image[offset:offset + length]))
        else:
            raise ValueError(f'Unsupported ROM page {index}: {algorithm}/{length}')
    return unpacked, base, root


def xip_files(image, base, root):
    root_table = root - base
    if u32(image, root_table) != 1:
        raise ValueError('Unexpected ROM root count')
    visited = set()

    def walk(path, offset):
        if offset in visited or not 0 <= offset <= len(image) - 4 or len(path) >= 32:
            raise ValueError('Invalid ROM directory')
        visited.add(offset)
        end = offset + u32(image, offset)
        position = offset + 4
        if not position <= end <= len(image):
            raise ValueError('ROM directory outside image')
        while position + 10 <= end:
            size, address, attributes, chars = struct.unpack_from('<IIBB', image, position)
            name_end = position + 10 + chars * 2
            if name_end > end + 4:
                raise ValueError(f'Truncated ROM name in {path} at {position:#x}, end {end:#x}, length {chars}')
            name = safe_name(image[position + 10:name_end])
            position = (name_end + 3) & ~3
            child = address - base
            if attributes & 0x10:
                yield from walk(path + (name,), child)
            elif 0 <= child <= child + size <= len(image):
                yield path + (name,), size, image[child:child + size], 'extracted'
            else:
                yield path + (name,), size, None, 'external reference'

    return walk((), u32(image, root_table + 8) - base)


def fat16_files(image):
    sector_bytes, cluster_sectors, reserved, fat_count, root_count = struct.unpack_from('<HBHBH', image, 11)
    fat_sectors = u16(image, 22)
    if (sector_bytes, cluster_sectors, fat_count) != (512, 8, 1) or not reserved or not root_count or not fat_sectors:
        raise ValueError('Unexpected RM-159 UDA FAT16 geometry')
    fat_start = reserved * sector_bytes
    fat_end = fat_start + fat_sectors * sector_bytes
    root_end = fat_end + root_count * 32
    cluster_bytes = sector_bytes * cluster_sectors
    visited = set()

    def chain(first):
        result = bytearray()
        seen = set()
        while 2 <= first < 0xfff8:
            if first in seen or fat_start + first * 2 + 2 > fat_end:
                raise ValueError('Invalid FAT cluster chain')
            seen.add(first)
            position = root_end + (first - 2) * cluster_bytes
            if position + cluster_bytes > len(image):
                raise ValueError('FAT cluster outside image')
            result.extend(image[position:position + cluster_bytes])
            first = u16(image, fat_start + first * 2)
        return result

    def walk(path, table):
        long_parts = {}
        for offset in range(0, len(table), 32):
            entry = table[offset:offset + 32]
            if len(entry) < 32 or entry[0] == 0:
                return
            if entry[0] == 0xe5:
                long_parts.clear()
                continue
            attributes = entry[11]
            if attributes == 0x0f:
                long_parts[entry[0] & 0x1f] = entry[1:11] + entry[14:26] + entry[28:32]
                continue
            if attributes & 8 or entry[0] == 0x2e:
                long_parts.clear()
                continue
            if long_parts:
                raw = b''.join(long_parts[key] for key in sorted(long_parts))
                raw = b''.join(raw[i:i + 2] for i in range(0, len(raw), 2) if raw[i:i + 2] not in (b'\x00\x00', b'\xff\xff'))
            else:
                stem, suffix = entry[:8].rstrip(b' '), entry[8:11].rstrip(b' ')
                raw = (stem + (b'.' + suffix if suffix else b'')).decode('cp437').encode('utf-16-le')
            name = safe_name(raw)
            long_parts.clear()
            cluster = u16(entry, 26)
            size = u32(entry, 28)
            if attributes & 0x10:
                if cluster in visited or len(path) >= 32:
                    raise ValueError('Invalid FAT directory chain')
                visited.add(cluster)
                yield from walk(path + (name,), chain(cluster))
            else:
                data = chain(cluster) if size else b''
                if len(data) < size:
                    raise ValueError('Truncated FAT file')
                yield path + (name,), size, data[:size], 'extracted'

    return walk((), image[fat_end:root_end])


def write_files(folder, files):
    folder.mkdir()
    count = 0
    seen = set()
    with (folder / 'inventory.tsv').open('w', newline='', encoding='utf-8') as manifest:
        writer = csv.writer(manifest, delimiter='\t')
        writer.writerow(('Path', 'Bytes', 'Status'))
        for parts, size, data, status in files:
            relative = '/'.join(parts)
            normalized = relative.casefold()
            if normalized in seen:
                raise ValueError(f'Duplicate path: {relative}')
            seen.add(normalized)
            writer.writerow((relative, size, status))
            if data is None:
                continue
            target = folder.joinpath(*parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('xb') as output:
                output.write(data)
            count += 1
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('firmware', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    for layer, filename in (('core-rofs', 'RM-159_31.0.017_PR.C0R'), ('variant-rofs', 'RM-159_31.0.017_PR.v05')):
        path = args.firmware / filename
        with path.open('rb') as source:
            digest = hashlib.file_digest(source, 'sha256').hexdigest()
        print(f'{layer}: {extract_rofs(path, args.output / layer)} files, SHA-256 {digest}', flush=True)
    source, records = read_fpsx(args.firmware / 'RM-159_31.0.017_PR.C0R')
    xip, base, root = unpack_xip(source, records)
    print(f'core-xip: {write_files(args.output / "core-xip", xip_files(xip, base, root))} files', flush=True)
    del source, xip
    data, records = read_fpsx(args.firmware / 'RM159_31.0.002_039_U01_uda.fpsx')
    if {kind for kind, *_ in records} != {0x2e} or len({chip for _, chip, *_ in records}) != 1:
        raise ValueError('Unexpected UDA record layout')
    size = max(address + length for _, _, address, _, length in records)
    image = assemble(data, records, 0x2e, records[0][1], 0, size)
    if image[510:512] != b'\x55\xaa':
        raise ValueError('UDA FAT boot signature missing')
    print(f'uda-fat: {write_files(args.output / "uda-fat", fat16_files(image))} files', flush=True)


if __name__ == '__main__':
    main()
