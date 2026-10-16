"""Request exact, non-replacing host mappings on modern Linux kernels."""
from pathlib import Path

path = Path('/root/android81-leo/src/art/runtime/mem_map.cc')
source = path.read_text()
marker = '// LEO_HOST_EXACT_MMAP: modern Linux may align file hints to huge pages.'
needle = '  DCHECK_ALIGNED(length, kPageSize);\n  if (low_4gb) {'
replacement = '''  DCHECK_ALIGNED(length, kPageSize);
#if defined(__linux__) && !defined(ANDROID_OS)
  // LEO_HOST_EXACT_MMAP: modern Linux may align file hints to huge pages.
  // Unlike MAP_FIXED, NOREPLACE fails on collisions and never overwrites memory.
  if (addr != nullptr && (flags & MAP_FIXED) == 0) {
    flags |= 0x100000;  // MAP_FIXED_NOREPLACE, Linux >= 4.17.
  }
#endif
  if (low_4gb) {'''
if marker not in source:
    assert source.count(needle) == 1
    backup = path.with_name('mem_map.cc.leo-host-original')
    if not backup.exists():
        backup.write_text(source)
    path.write_text(source.replace(needle, replacement))
