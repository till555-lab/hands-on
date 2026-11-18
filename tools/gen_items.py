EAR = [
"................",
".......oo.......",
"......ohho......",
".....ohhffo.....",
".....ohfppfo....",
"....ohfppqpfo...",
"....ohfppqpfo...",
"....ohfppqpfso..",
"...ohfppppqpfso.",
"...ohfppppqpfso.",
"...ohffppppffso.",
"...ohfffffffsso.",
"....oosssssssoo.",
".....oooooooo...",
"................",
"................",
]

E7 = [
"...oo..",
"..ohho.",
".ohhfso",
".ohfpso",
".ohfpqo",
".ohfpqo",
".ohfpqo",
".ohffso",
".osffso",
"..osso.",
"...oo..",
]
DOUBLE = ["................"]*2 + ["." + r + "." + r for r in E7] + ["................"]*3

TAIL = [
"................",
"..........oooo..",
"........ooottto.",
".......ottttttto",
".......otttttto.",
"......ohffffsso.",
".....ohhfffffsso",
"....ohhfffffssso",
"...ohhfffffffsso",
"..ohhfffffffsso.",
"..ohfffffffssso.",
"..ohffffffsso...",
"...ohffffsso....",
"....oohhsso.....",
".....oooo.......",
"................",
]

PAW = [
"................",
"................",
"....oooooooo....",
"..ooohhhhhhfooo.",
".ohhhhhhhffffso.",
".ohhhsffsffsfso.",
".ohhhsffsffsfso.",
".ohhfpffpffpfso.",
".ohhfffffffffso.",
".ohhffpppppffso.",
".ohffppppppqfso.",
".ohfffpppqqffso.",
".osfffffffffsso.",
"..osssssssssso..",
"...oooooooooo...",
"................",
]
SPRITES = {"ears":EAR,"double_ears":DOUBLE,"tail":TAIL,"paws":PAW}
PAL = {
 "white": {"o":(96,102,122),"h":(255,255,255),"f":(238,239,244),"s":(200,206,220),"p":(246,170,196),"q":(214,120,152),"t":(186,196,214)},
 "black": {"o":(10,10,14),"h":(104,104,122),"f":(58,58,68),"s":(38,38,46),"p":(170,108,134),"q":(120,70,92),"t":(92,92,110)},
}

import os
import struct
import zlib

OUT = "src/main/resources/assets/femboycompanion/textures/item"


def write_png(path, rows):
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in rows)

    def chunk(tag, data):
        body = struct.pack(">I", len(data)) + tag + data
        return body + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def main():
    os.makedirs(OUT, exist_ok=True)
    for fam, pal in PAL.items():
        for name, sprite in SPRITES.items():
            rows = []
            for line in sprite:
                assert len(line) == 16, name
                rows.append([pal[ch] + (255,) if ch != "." else (0, 0, 0, 0) for ch in line])
            write_png(os.path.join(OUT, "%s_%s.png" % (fam, name)), rows)
    print("item textures done")


main()
