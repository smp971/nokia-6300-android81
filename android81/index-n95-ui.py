"""Index original N95 resources and searchable text in an extracted RM-159 tree."""

import argparse
import csv
import re
from collections import Counter
from pathlib import Path


LAYERS = ('core-xip', 'core-rofs', 'variant-rofs', 'uda-fat')
ASSETS = {'.mif', '.mbm', '.skn', '.jpg', '.jpeg', '.png', '.gif', '.bmp', '.svg', '.svgb'}
TEXT = {
    'avkon.r01', 'menu2.r01', 'idle.r01', 'activeidlesettings.r01', 'profileapp.r01',
    'profilesettingsview.r01', 'phone.r01', 'logs.r01', 'phonebook.r01', 'mce.r01',
    'multimediamenu.r01', 'mediagallery2.r01', 'musicplayer.r01', 'calendar.r01',
    'clock.r01', 'btui.r01', 'wlansnifferwizard.r01', 'wlansettingspluginrsc.r01',
}
ENCODINGS = (
    ('ASCII', re.compile(rb'[\x20-\x7e]{5,}'), 'ascii'),
    ('UTF-16LE', re.compile(rb'(?:[\x20-\x7e]\x00){5,}'), 'utf-16-le'),
)
ACTION = re.compile(rb'(?:AS_[a-zA-Z0-9_]+|viewactivate|scancode)')


def index(tree):
    totals = {}
    with (tree / 'ui-assets.tsv').open('w', encoding='utf-8', newline='') as assets, \
            (tree / 'ui-labels.tsv').open('w', encoding='utf-8', newline='') as labels:
        image_rows = csv.writer(assets, delimiter='\t')
        text_rows = csv.writer(labels, delimiter='\t')
        image_rows.writerow(('Layer', 'Path', 'Bytes'))
        text_rows.writerow(('Layer', 'Path', 'Offset', 'Encoding', 'Value'))
        for layer in LAYERS:
            folder = tree / layer
            counts = Counter()
            with (folder / 'inventory.tsv').open(encoding='utf-8', newline='') as inventory:
                for row in csv.DictReader(inventory, delimiter='\t'):
                    relative = Path(row['Path'])
                    status = row['Status']
                    counts[status] += 1
                    if status != 'extracted':
                        continue
                    path = folder / relative
                    if not path.is_file() or path.stat().st_size != int(row['Bytes']):
                        raise ValueError(f'Inventory does not match {path}')
                    if relative.suffix.casefold() in ASSETS:
                        image_rows.writerow((layer, row['Path'], row['Bytes']))
                        counts['visual assets'] += 1
                    name = relative.name.casefold()
                    if name in TEXT:
                        data = path.read_bytes()
                        for label, pattern, codec in ENCODINGS:
                            for match in pattern.finditer(data):
                                value = match.group().decode(codec).strip()
                                if len(value) >= 5 and any(c.isalpha() for c in value):
                                    text_rows.writerow((layer, row['Path'], hex(match.start()), label, value))
                                    counts['text fragments'] += 1
                    elif name.startswith(('appshell_grid.o', 'appshell_list.o')):
                        for match in ACTION.finditer(path.read_bytes()):
                            text_rows.writerow((layer, row['Path'], hex(match.start()), 'action', match.group().decode()))
                            counts['text fragments'] += 1
            totals[layer] = counts
    with (tree / 'summary.tsv').open('w', encoding='utf-8', newline='') as summary:
        writer = csv.writer(summary, delimiter='\t')
        keys = ['extracted', 'external reference', 'overlay marker', 'visual assets', 'text fragments']
        writer.writerow(('Layer', *keys))
        for layer, counts in totals.items():
            writer.writerow((layer, *(counts[key] for key in keys)))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('extracted', type=Path)
    index(parser.parse_args().extracted)
