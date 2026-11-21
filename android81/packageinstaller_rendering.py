"""Scoped rendering changes preserving unrelated attributes and string references.

Binary structs follow AOSP ResourceTypes.h: ResStringPool_header,
ResXMLTree_attrExt and ResXMLTree_attribute. Styled string pools are deliberately
rejected: Android manifests in this fallback do not need them.
"""
import re
import struct

ANDROID = "http://schemas.android.com/apk/res/android"
HARDWARE = 0x010102D3
NONE = 0xFFFFFFFF
HEADER_LAYOUTS = tuple("install_" + name + ".xml" for name in
                       ("confirm", "confirm_perm", "confirm_perm_update", "failed", "installing", "success"))


def source_manifest(text):
    for activity in re.finditer(r"<activity(?:-alias)?\b[^>]*>", text):
        if 'android:hardwareAccelerated="true"' in activity.group():
            raise ValueError("An activity explicitly re-enables acceleration")
    matches = list(re.finditer(r"<application\b[^>]*>", text))
    if len(matches) != 1:
        raise ValueError("Expected exactly one application declaration")
    match = matches[0]
    declaration = match.group()
    attribute = re.compile(r'\bandroid:hardwareAccelerated="[^"]*"')
    if len(attribute.findall(declaration)) > 1:
        raise ValueError("Duplicate rendering attribute")
    changed = attribute.sub('android:hardwareAccelerated="false"', declaration) if attribute.search(declaration) else declaration.replace("<application", '<application android:hardwareAccelerated="false"', 1)
    return text[:match.start()] + changed + text[match.end():]


def source_layout(text):
    matches = [match for match in re.finditer(r"<TextView\b[^>]*>", text)
               if re.search(r'android:id="@\+?id/app_name"', match.group())]
    if len(matches) != 1:
        raise ValueError("Expected one app_name TextView")
    match = matches[0]
    old = 'android:textAppearance="?android:attr/titleTextStyle"'
    new = 'android:textAppearance="?android:attr/textAppearanceLarge"'
    declaration = match.group()
    if old not in declaration and new not in declaration:
        raise ValueError("Unexpected app-title text appearance")
    return text[:match.start()] + declaration.replace(old, new) + text[match.end():]


def chunks(blob):
    if len(blob) < 8 or struct.unpack_from("<HHI", blob) != (3, 8, len(blob)):
        raise ValueError("Invalid binary XML header")
    offset = 8
    result = []
    while offset < len(blob):
        if offset + 8 > len(blob):
            raise ValueError("Truncated chunk")
        kind, header, size = struct.unpack_from("<HHI", blob, offset)
        if header < 8 or size < header or size % 4 or offset + size > len(blob):
            raise ValueError("Invalid chunk bounds")
        result.append((kind, blob[offset:offset + size]))
        offset += size
    return result


def strings(pool):
    kind, header, size = struct.unpack_from("<HHI", pool)
    if kind != 1 or header != 28 or size != len(pool):
        raise ValueError("Unsupported string pool")
    count, styles, flags, start, style_start = struct.unpack_from("<IIIII", pool, 8)
    if styles or style_start or start < 28 + count * 4 or start > size:
        raise ValueError("Unsupported styled/broken manifest string pool")
    values = []
    def length(at, utf8):
        if utf8:
            first = pool[at]
            return (((first & 127) << 8) | pool[at + 1], at + 2) if first & 128 else (first, at + 1)
        first = struct.unpack_from("<H", pool, at)[0]
        return (((first & 32767) << 16) | struct.unpack_from("<H", pool, at + 2)[0], at + 4) if first & 32768 else (first, at + 2)
    for index in range(count):
        at = start + struct.unpack_from("<I", pool, 28 + index * 4)[0]
        if at >= size:
            raise ValueError("String outside pool")
        units, at = length(at, bool(flags & 256))
        if flags & 256:
            byte_count, at = length(at, True)
            end, terminator = at + byte_count, b"\0"
            encoding = "utf-8"
        else:
            end, terminator = at + units * 2, b"\0\0"
            encoding = "utf-16-le"
        if pool[end:end + len(terminator)] != terminator:
            raise ValueError("Unterminated manifest string")
        values.append(pool[at:end].decode(encoding))
    return values


def insert_string(pool, value, index):
    existing = strings(pool)
    count, _, flags, start, _ = struct.unpack_from("<IIIII", pool, 8)
    if len(value) > 127 or not value.isascii():
        raise ValueError("Only the short ASCII attribute name is appended")
    data = pool[start:]
    encoded = bytes([len(value), len(value)]) + value.encode("ascii") + b"\0" if flags & 256 else struct.pack("<H", len(value)) + value.encode("utf-16-le") + b"\0\0"
    if not 0 <= index <= count:
        raise ValueError("Invalid string insertion")
    new_start = start + 4
    body = data + encoded
    body += b"\0" * (-len(body) % 4)
    header = bytearray(pool[:28])
    struct.pack_into("<I", header, 4, new_start + len(body))
    struct.pack_into("<IIIII", header, 8, count + 1, 0, flags & ~1, new_start, 0)
    offsets = pool[28:28 + count * 4]
    result = bytes(header) + offsets[:index * 4] + struct.pack("<I", len(data)) + offsets[index * 4:] + pool[28 + count * 4:start] + body
    if strings(result) != existing[:index] + [value] + existing[index:]:
        raise ValueError("String-pool preservation failed")
    return result, index


def reindex_node(node, insertion, count):
    kind = struct.unpack_from("<H", node)[0]
    if kind not in {0x100, 0x101, 0x102, 0x103, 0x104}:
        raise ValueError("Unsupported XML node")
    result = bytearray(node)
    def remap(at):
        value = struct.unpack_from("<I", result, at)[0]
        if value == NONE:
            return
        if value >= count:
            raise ValueError("Node string index outside pool")
        if value >= insertion:
            struct.pack_into("<I", result, at, value + 1)
    remap(12)  # Every XML node's comment.
    if kind in {0x100, 0x101, 0x102, 0x103}:
        remap(16)
        remap(20)
    if kind == 0x102:
        at, attrs = attributes(node)
        for i in range(len(attrs)):
            base = at + i * 20
            for field in (0, 4, 8):
                remap(base + field)
            if result[base + 15] == 3:
                remap(base + 16)
    elif kind == 0x104:
        remap(16)
        if result[23] == 3:
            remap(24)
    return bytes(result)


def attributes(node):
    kind, header, size = struct.unpack_from("<HHI", node)
    if kind != 0x102 or header != 16 or size != len(node):
        raise ValueError("Unsupported start element")
    start, width, count = struct.unpack_from("<HHH", node, 24)
    at = 16 + start
    if width != 20 or start < 20 or at + count * width > size:
        raise ValueError("Invalid attribute array")
    return at, [node[at + i * 20:at + (i + 1) * 20] for i in range(count)]


def binary_manifest(blob):
    parts = chunks(blob)
    pools = [i for i, (kind, _) in enumerate(parts) if kind == 1]
    maps = [i for i, (kind, _) in enumerate(parts) if kind == 0x180]
    if len(pools) != 1 or len(maps) != 1:
        raise ValueError("Expected one string pool and resource map")
    pool_index, map_index = pools[0], maps[0]
    pool = parts[pool_index][1]
    names = strings(pool)
    resource_map = parts[map_index][1]
    if struct.unpack_from("<H", resource_map, 2)[0] != 8 or (len(resource_map) - 8) % 4:
        raise ValueError("Invalid resource map")
    ids = list(struct.unpack_from("<" + "I" * ((len(resource_map) - 8) // 4), resource_map, 8))
    if len(ids) > len(names) or ANDROID not in names:
        raise ValueError("Invalid Android namespace/resource indices")
    namespace = names.index(ANDROID)
    app = []
    for index, (kind, node) in enumerate(parts):
        if kind != 0x102:
            continue
        _, name = struct.unpack_from("<II", node, 16)
        _, attrs = attributes(node)
        if names[name] == "application":
            app.append((index, node, attrs))
        elif names[name] in {"activity", "activity-alias"}:
            for item in attrs:
                ns, key = struct.unpack_from("<II", item)
                if ns == namespace and key < len(ids) and ids[key] == HARDWARE and struct.unpack_from("<I", item, 16)[0]:
                    raise ValueError("An activity explicitly re-enables acceleration")
    if len(app) != 1:
        raise ValueError("Expected one application node")
    index, node, attrs = app[0]
    matches = [i for i, item in enumerate(attrs) if struct.unpack_from("<I", item)[0] == namespace and struct.unpack_from("<I", item, 4)[0] < len(ids) and ids[struct.unpack_from("<I", item, 4)[0]] == HARDWARE]
    if len(matches) > 1:
        raise ValueError("Duplicate rendering attribute")
    at, _ = attributes(node)
    changed = bytearray(node)
    if matches:
        position = at + matches[0] * 20
        if node[position + 15] == 0x12 and struct.unpack_from("<I", node, position + 16)[0] == 0:
            return blob
        struct.pack_into("<I", changed, position + 8, NONE)
        struct.pack_into("<HBBI", changed, position + 12, 8, 0, 0x12, 0)
    else:
        # Resource-name strings must stay before unmapped strings. Extending the
        # map with zero holes changes aapt's interpretation of package/coreApp.
        new_name = len(ids)
        new_pool, _ = insert_string(pool, "hardwareAccelerated", new_name)
        for part_index, (kind, part) in enumerate(parts):
            if 0x100 <= kind < 0x180:
                parts[part_index] = (kind, reindex_node(part, new_name, len(names)))
        namespace += int(namespace >= new_name)
        node = parts[index][1]
        at, attrs = attributes(node)
        ids.append(HARDWARE)
        parts[pool_index] = (1, new_pool)
        new_map = struct.pack("<HHI", 0x180, 8, 8 + 4 * len(ids)) + struct.pack("<" + "I" * len(ids), *ids)
        parts[map_index] = (0x180, new_map)
        def resource(item):
            key = struct.unpack_from("<I", item, 4)[0]
            return ids[key] if key < len(ids) else 0
        insert = next((i for i, item in enumerate(attrs) if resource(item) > HARDWARE), len(attrs))
        added = struct.pack("<IIIHBBI", namespace, new_name, NONE, 8, 0, 0x12, 0)
        attrs.insert(insert, added)
        changed = bytearray(node[:at] + b"".join(attrs) + node[at + (len(attrs) - 1) * 20:])
        struct.pack_into("<I", changed, 4, len(changed))
        struct.pack_into("<H", changed, 28, len(attrs))
        for field in (30, 32, 34):
            value = struct.unpack_from("<H", changed, field)[0]
            if value and value >= insert + 1:
                struct.pack_into("<H", changed, field, value + 1)
    parts[index] = (0x102, bytes(changed))
    result = b"".join(part for _, part in parts)
    return struct.pack("<HHI", 3, 8, len(result) + 8) + result


def binary_title_style(blob, app_name_id):
    parts = chunks(blob)
    pool = next(part for kind, part in parts if kind == 1)
    names = strings(pool)
    resource_map = next(part for kind, part in parts if kind == 0x180)
    ids = struct.unpack_from("<" + "I" * ((len(resource_map) - 8) // 4), resource_map, 8)
    namespace = names.index(ANDROID)
    found = 0
    for index, (kind, node) in enumerate(parts):
        if kind != 0x102 or names[struct.unpack_from("<I", node, 20)[0]] != "TextView":
            continue
        at, attrs = attributes(node)
        named = {ids[struct.unpack_from("<I", item, 4)[0]]: (position, item)
                 for position, item in enumerate(attrs)
                 if struct.unpack_from("<I", item)[0] == namespace and struct.unpack_from("<I", item, 4)[0] < len(ids)}
        if 0x010100D0 not in named or struct.unpack_from("<I", named[0x010100D0][1], 16)[0] != app_name_id:
            continue
        found += 1
        if 0x01010034 not in named:
            raise ValueError("App title has no text appearance")
        position, appearance = named[0x01010034]
        data = struct.unpack_from("<I", appearance, 16)[0]
        if appearance[15] != 2 or data not in {0x010102F8, 0x01010040}:
            raise ValueError("Unexpected compiled app-title style")
        updated = bytearray(node)
        struct.pack_into("<I", updated, at + position * 20 + 16, 0x01010040)
        parts[index] = (kind, bytes(updated))
    if found != 1:
        raise ValueError("Expected one compiled app_name TextView")
    body = b"".join(part for _, part in parts)
    return struct.pack("<HHI", 3, 8, len(body) + 8) + body
