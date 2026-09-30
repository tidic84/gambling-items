"""Draw an editable pixel-art recreation; export layered ORA and Aseprite."""
from pathlib import Path
from io import BytesIO
import struct as s
import zlib
import zipfile
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[1] / 'artwork/retro-pixel-project'
OUT.mkdir(parents=True, exist_ok=True)
W = 96
P = dict(navy='#102344', ink='#17171e', wine='#781e2a', red='#972d36',
         darkred='#571d2b', gold='#d59a36', lightgold='#f3c563', ochre='#a9692c',
         cream='#fff0cd', shade='#e5d4aa', seven='#b42c2b', bright='#db4636',
         steel='#919395', lightsteel='#bbbdbb', darksteel='#525660', slot='#292b32')
layers = []
def layer(name):
    im = Image.new('RGBA', (W,W))
    layers.append((name, im))
    return ImageDraw.Draw(im)
def rect(box, c):
    d.rectangle(box, fill=P[c])

d = layer('01 Fond bleu nuit'); rect((0,0,95,95),'navy')
d = layer('02 Levier')
rect((69,43,77,56),'ink'); rect((73,34,80,51),'ink')
rect((75,35,78,49),'steel'); rect((75,35,75,48),'lightsteel')
rect((71,45,74,53),'steel'); rect((71,45,71,52),'lightsteel')
rect((74,25,83,26),'ink'); rect((72,27,85,35),'ink'); rect((74,36,83,37),'ink')
rect((74,27,83,35),'seven'); rect((74,27,78,30),'bright')
rect((76,27,78,28),'cream'); rect((81,32,83,35),'wine')

d = layer('03 Silhouette et pieds')
rect((18,29,70,78),'ink'); rect((16,70,72,80),'ink')
rect((18,79,24,82),'ink'); rect((64,79,70,82),'ink')
for x1,y,x2 in [(21,26,67),(24,23,64),(27,20,61),(30,17,58),(33,14,55)]:
    rect((x1,y,x2,31),'ink')

d = layer('04 Caisse bordeaux')
rect((20,31,68,75),'wine'); rect((20,31,21,70),'red')
rect((66,31,68,75),'darkred'); rect((22,57,65,58),'red')
for x1,y,x2 in [(23,28,65),(26,25,62),(29,22,59),(32,19,56),(35,16,53)]:
    rect((x1,y,x2,31),'wine')

d = layer('05 Bordures dorees')
for x1,y,x2 in [(20,30,68),(23,27,65),(26,24,62),(29,21,59),(32,18,56),(35,16,53)]:
    rect((x1,y,x1+2,y+2),'gold')
    rect((x2-2,y,x2,y+2),'gold')
rect((35,16,53,17),'gold')
rect((20,32,21,57),'gold'); rect((67,32,68,57),'ochre')
rect((20,57,68,59),'gold'); rect((22,57,66,57),'lightgold')
rect((18,73,21,77),'gold'); rect((67,73,70,77),'gold')
rect((18,77,70,78),'gold'); rect((20,77,68,77),'lightgold')

d = layer('06 Trois voyants')
for x,y in [(32,23),(42,21),(52,23)]:
    rect((x,y,x+4,y+5),'ochre'); rect((x,y,x+3,y+4),'gold')
    rect((x+1,y+1,x+2,y+3),'cream')

d = layer('07 Cadre des rouleaux'); rect((23,34,65,56),'ink')
d = layer('08 Rouleaux creme')
for x in (25,39,53):
    rect((x,36,x+11,54),'cream'); rect((x,53,x+11,54),'shade')

glyph = ['11111','11111','00011','00110','00110','01100','01100']
for i,x in enumerate((26,40,54),1):
    d = layer(f'0{8+i} Symbole 7 - rouleau {i}')
    for y,row in enumerate(glyph):
        for col,v in enumerate(row):
            if v == '1': rect((x+2*col,40+2*y,x+2*col+1,41+2*y),'seven')
    rect((x,40,x+9,40),'bright')

d = layer('12 Trappe metallique')
rect((28,63,60,73),'ink'); rect((30,65,58,71),'steel')
rect((30,65,58,65),'lightsteel'); rect((30,65,30,71),'lightsteel')
rect((32,67,56,69),'slot'); rect((32,67,56,67),'ink')
rect((30,71,58,71),'darksteel')

merged = Image.new('RGBA',(W,W))
transparent = Image.new('RGBA',(W,W))
for i,(_,im) in enumerate(layers):
    merged = Image.alpha_composite(merged,im)
    if i: transparent = Image.alpha_composite(transparent,im)
merged.save(OUT/'retro-icon-96.png')
merged.resize((960,960),Image.Resampling.NEAREST).save(OUT/'retro-icon-preview.png')
transparent.save(OUT/'retro-icon-transparent.png')
def png(im):
    f = BytesIO(); im.save(f,format='PNG'); return f.getvalue()
root = ET.Element('image',w=str(W),h=str(W),name='Gambling Items - Retro',version='0.0.3')
stack = ET.SubElement(root,'stack')
with zipfile.ZipFile(OUT/'gambling-items-retro.ora','w') as z:
    z.writestr('mimetype','image/openraster',compress_type=zipfile.ZIP_STORED)
    for i,(name,im) in reversed(list(enumerate(layers))):
        src=f'data/layer{i:02}.png'
        ET.SubElement(stack,'layer',name=name,src=src,x='0',y='0',opacity='1.0',visibility='visible',**{'composite-op':'svg:src-over'})
        z.writestr(src,png(im))
    z.writestr('stack.xml',ET.tostring(root,encoding='utf-8',xml_declaration=True))
    z.writestr('mergedimage.png',png(merged))
    z.writestr('Thumbnails/thumbnail.png',png(merged))

def pack(fmt,*v): return s.pack('<'+fmt,*v)
def chunk(kind,data): return pack('IH',len(data)+6,kind)+data
def string(text):
    b=text.encode('utf-8'); return pack('H',len(b))+b
chunks=[]
for name,_ in layers:
    chunks.append(chunk(0x2004,pack('6HB',3,0,0,0,0,0,255)+bytes(3)+string(name)))
for i,(_,im) in enumerate(layers):
    chunks.append(chunk(0x2005,pack('HhhBHh',i,0,0,255,2,0)+bytes(5)+pack('HH',W,W)+zlib.compress(im.tobytes())))
colors=[tuple(bytes.fromhex(c[1:])) for c in P.values()]
pal=pack('III',len(colors),0,len(colors)-1)+bytes(8)
for rgb in colors: pal+=pack('HBBBB',0,*rgb,255)
chunks.append(chunk(0x2019,pal))
data=b''.join(chunks)
frame=pack('IHHH',16+len(data),0xf1fa,len(chunks),100)+bytes(2)+pack('I',0)+data
header=bytearray(128)
s.pack_into('<I5HI',header,0,128+len(frame),0xa5e0,1,W,W,32,1)
s.pack_into('<H',header,18,100)
s.pack_into('<HBBhhHH',header,32,len(colors),1,1,0,0,1,1)
(OUT/'gambling-items-retro.aseprite').write_bytes(header+frame)
with (OUT/'retro-palette.gpl').open('w',encoding='utf-8') as f:
    f.write('GIMP Palette\nName: Gambling Items Retro\nColumns: 4\n#\n')
    for (name,_),rgb in zip(P.items(),colors): f.write(f'{rgb[0]} {rgb[1]} {rgb[2]} {name}\n')
print(f'Created {len(layers)} layers, {W}x{W}, {len(colors)} colors: {OUT}')
