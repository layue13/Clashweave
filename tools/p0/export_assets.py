"""Rewritten rigid placeholder geometry. OBJ game Y-up; resource units are blocks."""
from pathlib import Path
import struct
import zlib

root = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/clashweave'


def box_mesh(boxes, filename):
    vertices, faces = [], []
    uvs = ['vt 0.125 0.5', 'vt 0.375 0.5', 'vt 0.625 0.5', 'vt 0.875 0.5']
    for low, high, color in boxes:
        start = len(vertices) + 1
        x, y, z = low
        X, Y, Z = high
        vertices += [(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
        for indices in [(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]:
            a,b,c,d = [start+i for i in indices]
            faces += [f'f {a}/{color} {b}/{color} {c}/{color}', f'f {a}/{color} {c}/{color} {d}/{color}']
    folder = root / 'models'
    folder.mkdir(parents=True, exist_ok=True)
    (folder/filename).write_text('# units=block grip=origin blade=+Y\n' + '\n'.join(
        ['v %.5f %.5f %.5f' % xyz for xyz in vertices] + uvs + faces) + '\n', encoding='utf-8')


box_mesh([
    ((-.060,.14,-.035),(.060,1.10,.035),1),
    ((-.020,1.10,-.020),(.020,1.17,.020),1),
    ((-.075,.10,-.070),(.075,.14,.070),2),
    ((-.050,-.18,-.055),(.050,.10,.055),3),
    ((.050,.14,-.036),(.062,1.10,.036),4),
], 'katana.obj')
box_mesh([
    ((-.065,0,-.060),(.065,1.25,.060),3),
    ((-.075,0,-.070),(.075,.045,.070),2),
    ((-.075,1.205,-.070),(.075,1.25,.070),2),
], 'sheath.obj')

colors = [(205,220,232,255),(232,178,45,255),(48,58,86,255),(184,55,52,255)]
raw = b''.join(b'\0' + b''.join(bytes(c) for c in colors) for _ in range(4))


def chunk(kind, data):
    return struct.pack('>I',len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind+data)&0xffffffff)


png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR',struct.pack('>IIBBBBB',4,4,8,6,0,0,0)) + chunk(b'IDAT',zlib.compress(raw)) + chunk(b'IEND',b'')
(root/'textures/models').mkdir(parents=True,exist_ok=True)
(root/'textures/models/katana.png').write_bytes(png)
(root/'textures/items').mkdir(parents=True,exist_ok=True)
(root/'textures/items/katana.png').write_bytes(png)
(root/'lang').mkdir(parents=True,exist_ok=True)
(root/'lang/en_US.lang').write_text('item.clashweave.katana.name=Clashweave Katana\n',encoding='utf-8')
print('exported blade width=.12 thickness=.07; sheath width=.13 thickness=.12 length=1.25')
