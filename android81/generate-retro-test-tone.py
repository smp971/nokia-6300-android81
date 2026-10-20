"""Generate the owned WAV fixture for RetroTests. No device/audio operations."""
from pathlib import Path
import math,struct,wave
target=Path(__file__).resolve().parent/'output/leo-spectrum-test.wav'
target.parent.mkdir(exist_ok=True)
with wave.open(str(target),'wb') as output:
    output.setnchannels(1);output.setsampwidth(2);output.setframerate(22050)
    output.writeframes(b''.join(struct.pack('<h',int(5000*(math.sin(2*math.pi*330*i/22050)+.35*math.sin(2*math.pi*990*i/22050)))) for i in range(22050*15)))
print('Owned 15-second WAV test fixture generated under ignored output/.')
