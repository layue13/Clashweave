"""Deterministic static calibration geometry; no production assets are changed."""
from pathlib import Path
import struct, zlib

ROOT=Path(__file__).resolve().parent
OUT=ROOT/'resources/assets/cwgrip'
OUT.mkdir(parents=True,exist_ok=True)
COLORS=[(48,31,21),(229,176,27),(211,226,235),(53,67,91),(222,36,38),(36,214,98)]

def export(name,boxes):
    lines=['# S4b format 1, blocks, Y up, grip/saya-mouth origin']
    for i in range(len(COLORS)): lines.append(f'vt {(i+.5)/len(COLORS)} 0.5')
    vi=1
    for label,x0,x1,y0,y1,z0,z1,color in boxes:
        lines.append('g '+label)
        vertices=[(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0),
                  (x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)]
        lines.extend('v %.5f %.5f %.5f'%p for p in vertices)
        for face in [(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]:
            for tri in [(face[0],face[1],face[2]),(face[0],face[2],face[3])]:
                lines.append('f '+' '.join(f'{vi+j}/{color+1}' for j in tri))
        vi+=8
    (OUT/(name+'.obj')).write_text('\n'.join(lines)+'\n')

export('blade',[
    ('handle',-.025,.025,-.16,.12,-.027,.027,0),
    ('pommel',-.03,.03,-.18,-.15,-.033,.033,1),
    ('guard',-.075,.075,.12,.14,-.06,.06,1),
    ('blade',-.036,.036,.14,1.12,-.010,.010,2),
    ('cutting_edge',.032,.040,.16,1.12,-.013,.013,4),
    ('grip_marker',-.029,.029,-.010,.010,-.031,.031,5)])
export('sheath',[
    ('body',-.05,.05,0,1.02,-.040,.040,3),
    ('mouth',-.058,.058,0,.07,-.048,.048,1),
    ('tip',-.055,.055,.97,1.04,-.045,.045,1)])
def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
data=b'\x00'+b''.join(bytes((*rgb,255)) for rgb in COLORS)
(OUT/'palette.png').write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',len(COLORS),1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(data))+chunk(b'IEND',b''))
print('S4B_ASSET_EXPORT boxes=9 units=blocks grip_marker=green cutting_edge=red')
