"""Offline layout/call audit of the retained audio machine module; no ADB."""
from pathlib import Path
import json
from elftools.elf.elffile import ELFFile

root = Path(__file__).resolve().parent.parent
out = root / 'android81/output/audio-abi'
out.mkdir(parents=True, exist_ok=True)
with (out / 'leo_audio_abi.o').open('rb') as source:
    probe = ELFFile(source)
    sizes = {symbol.name[6:]: symbol['st_size'] for symbol in probe.get_section_by_name('.symtab').iter_symbols()
             if symbol.name.startswith('size__')}
    offsets = {symbol.name[8:]: symbol['st_size'] - 1 for symbol in probe.get_section_by_name('.symtab').iter_symbols()
               if symbol.name.startswith('offset__')}

with (root / 'analysis/system/lib/modules/audio_machine_int_msm8909.ko').open('rb') as source:
    original = ELFFile(source)
    table = original.get_section_by_name('.symtab')
    symbols = {symbol.name: symbol for symbol in table.iter_symbols()}
    checks = {}
    for label, symbol_name in [('snd_soc_card', 'bear_card'),
                               ('platform_driver', 'msm8952_asoc_machine_driver'),
                               ('snd_soc_ops', 'msm8952_mi2s_be_ops'),
                               ('snd_soc_dai_link', 'msm8952_quin_dai_link')]:
        stock_size = symbols[symbol_name]['st_size']
        checks[label] = {'original': stock_size, 'rebuilt': sizes[label],
                         'matches': stock_size == sizes[label]}
    card = symbols['bear_card']
    links = symbols['msm8952_dai']
    data = original.get_section(card['st_shndx']).data()
    old_link_offset = None
    for relocation in original.get_section_by_name('.rel.data').iter_relocations():
        position = relocation['r_offset']
        if card['st_value'] <= position < card['st_value'] + card['st_size']:
            target = table.get_symbol(relocation['r_info_sym'])
            if target['st_shndx'] == links['st_shndx'] and int.from_bytes(data[position:position+4], 'little') == links['st_value']:
                old_link_offset = position - card['st_value']
    assert old_link_offset is not None
    checks['snd_soc_card.dai_link_offset'] = {
        'original': old_link_offset, 'rebuilt': offsets['snd_soc_card__dai_link'],
        'matches': old_link_offset == offsets['snd_soc_card__dai_link']}
    entry = symbols['msm8952_asoc_machine_probe']
    calls = []
    for relocation in original.get_section_by_name('.rel.text').iter_relocations():
        if entry['st_value'] <= relocation['r_offset'] < entry['st_value'] + entry['st_size']:
            target = table.get_symbol(relocation['r_info_sym'])
            if target['st_shndx'] == 'SHN_UNDEF':
                calls.append({'offset_in_probe': hex(relocation['r_offset']-entry['st_value']),
                              'symbol': target.name})
    match = symbols['__mod_of__msm8952_asoc_machine_of_match_device_table']
    contents = original.get_section(match['st_shndx']).data()[match['st_value']:match['st_value']+match['st_size']]
    compatibles = [contents[o+64:o+192].split(b'\0', 1)[0].decode()
                   for o in range(0, len(contents), 196) if contents[o+64]]
report = {'offline_only': True, 'limited_layout_checks': checks,
          'all_checked_layout_facts_match': all(item['matches'] for item in checks.values()),
          'module_compatibles': compatibles, 'probe_imports': calls,
          'full_abi_compatibility_proven': False,
          'crashing_function_identified': False}
(out / 'machine-module-audit.json').write_text(json.dumps(report, indent=2))
print(json.dumps({key: value for key, value in report.items() if key != 'probe_imports'}, indent=2))
