"""Owned eight-second silent PCM fixtures for DailyTests. No device operations."""
from pathlib import Path
import wave
import sys
import struct
import zlib
import subprocess

output = Path(__file__).resolve().parent/'output'
output.mkdir(exist_ok=True)
for name in ('a', 'b'):
    with wave.open(str(output/('leo-queue-'+name+'.wav')), 'wb') as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(16000)
        stream.writeframes(b'\0\0'*(16000*8))
if '--cover-mp3' in sys.argv:
    # Original pixel artwork; no downloaded or copyrighted cover image.
    rows = bytearray()
    for y in range(128):
        rows.append(0)
        for x in range(128):
            ring = 25**2 < (x-64)**2+(y-64)**2 < 35**2
            rows.extend((245, 184, 99) if ring else (35+x//2, 26+y//3, 97+y//2))
    def chunk(name, data):
        return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data)&0xffffffff)
    cover=output/'leo-fixture-cover.png'
    cover.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',128,128,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b''))
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(output/'leo-queue-a.wav'),'-i',str(cover),'-map','0:a','-map','1:v','-c:a','libmp3lame','-q:a','9','-c:v','copy','-id3v2_version','3','-metadata:s:v','title=Album cover','-metadata:s:v','comment=Cover (front)',str(output/'leo-queue-a.mp3')],check=True)
