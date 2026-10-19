"""Generate Leo's small, original 240x320 boot animation; no external artwork/fonts."""
from pathlib import Path
from PIL import Image,ImageDraw
import io,math,zipfile

out=Path(__file__).resolve().parent/'output';out.mkdir(exist_ok=True)
width,height,scale,fps=240,320,3,6
def frame(opacity=1.0,phase=0):
    image=Image.new('RGB',(width*scale,height*scale),(0,0,0));draw=ImageDraw.Draw(image)
    def box(points,fill,width=1):
        draw.line([(int(x*scale),int(y*scale)) for x,y in points],fill=fill,width=width*scale,joint='curve')
    white=tuple(int(c*opacity) for c in (244,239,255));accent=tuple(int(c*opacity) for c in (184,148,235))
    box([(57,128),(57,176),(89,176)],white,6)
    box([(136,128),(104,128),(104,176),(136,176)],white,6);box([(104,152),(130,152)],white,6)
    draw.rounded_rectangle((149*scale,125*scale,183*scale,179*scale),radius=16*scale,outline=white,width=6*scale)
    box([(98,197),(142,197)],accent,2)
    for i,x in enumerate((108,120,132)):
        light=.2+.8*max(0,math.cos(phase*math.pi/3-i*math.pi*2/3))**2
        color=tuple(int(c*opacity*light) for c in (184,148,235))
        draw.ellipse(((x-2)*scale,220*scale,(x+2)*scale,224*scale),fill=color)
    return image.resize((width,height),Image.Resampling.LANCZOS)
intro=[frame(v/4,0) for v in range(1,5)];loop=[frame(1,n) for n in range(6)]
with zipfile.ZipFile(out/'bootanimation.zip','w',compression=zipfile.ZIP_STORED) as archive:
    archive.writestr('desc.txt',f'{width} {height} {fps}\np 1 0 part0\np 0 0 part1\n')
    for name,frames in [('part0',intro),('part1',loop)]:
        for n,image in enumerate(frames):
            data=io.BytesIO();image.save(data,'PNG',optimize=True)
            entry=zipfile.ZipInfo(f'{name}/{n:04d}.png',date_time=(2026,1,1,0,0,0));entry.compress_type=zipfile.ZIP_STORED
            archive.writestr(entry,data.getvalue())
loop[0].save(out/'leo-bootanimation-preview.png')
loop[0].save(out/'leo-bootanimation-preview.gif',save_all=True,append_images=loop[1:],duration=round(1000/fps),loop=0)
print('Generated bootanimation.zip:',(out/'bootanimation.zip').stat().st_size,'bytes; 10 frames, 6 fps, 240x320')
