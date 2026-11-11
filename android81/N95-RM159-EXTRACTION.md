# Nokia N95 RM-159 firmware extraction

Run these commands with Python 3.11 or later from the repository root, using the directory that contains the RM-159 images and a new output directory.

```powershell
python -X utf8 android81/extract-n95-ui.py "<RM-159 firmware folder>" "<new output directory>"
python -X utf8 android81/index-n95-ui.py "<output directory>"
```

The exporter reconstructs the paged ROM and reads both ROFS layers and the FAT16 UDA. Each layer includes its original directory structure and a byte-checked inventory. The index lists visual assets and searchable fragments of selected interface resources with byte offsets.

| Layer | Source | Files extracted |
| --- | --- | ---: |
| Core XIP ROM | `RM-159_31.0.017_PR.C0R` | 2,698 |
| Core ROFS | `RM-159_31.0.017_PR.C0R` | 1,447 |
| Variant ROFS | `RM-159_31.0.017_PR.v05` | 4,427 |
| UDA FAT16 | `RM159_31.0.002_039_U01_uda.fpsx` | 1,117 |

The variant inventory also records 1,447 inherited entries. The original resources and binaries remain in the local export. Compiled Avkon resources and their bitmap/vector containers are available there for further analysis. The text index contains fragments from selected files and action identifiers from compiled AppShell definitions.

Format references: [Nokia N95 user guide](https://s.productreview.com.au/products/manuals/32992_55e9025caec3a.pdf), [EKA2L1 loader source](https://github.com/EKA2L1/EKA2L1/tree/393a785b4f6d126ea69bb9541467eeda68e862c0/src/emu/loader/src).
