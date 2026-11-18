import os
import random
import struct
import zlib

OUT = "src/main/resources/assets/femboycompanion/textures/entity"
W, H = 64, 32
DENSITY = [0.0, 0.008, 0.015, 0.025, 0.035, 0.05, 0.065, 0.08]

FAMILIES = {
    "white": {
        "fur": (242, 242, 245), "paw": (201, 210, 222), "muz": (250, 250, 252),
        "ear": (244, 169, 192), "eye": (42, 42, 51), "nose": (232, 132, 159), "tip": (201, 210, 222),
    },
    "black": {
        "fur": (43, 43, 50), "paw": (74, 74, 88), "muz": (60, 60, 70),
        "ear": (138, 90, 112), "eye": (242, 242, 245), "nose": (232, 132, 159), "tip": (85, 85, 99),
    },
}


def speck_color(base, rnd):
    lum = 0.299 * base[0] + 0.587 * base[1] + 0.114 * base[2]
    k = 0.10 + rnd.random() * 0.14
    if lum >= 128:
        return tuple(int(c * (1 - k)) for c in base)
    return tuple(int(c + (255 - c) * k) for c in base)


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def paint(kinds, u, v, w, h, d, fn):
    for name, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        for ly in range(fh):
            for lx in range(fw):
                kinds[y0 + ly][x0 + lx] = fn(name, lx, ly)


def head_fn(name, lx, ly):
    if name == "front" and lx in (1, 6) and ly in (3, 4):
        return "eye"
    return "fur"


def body_fn(name, lx, ly):
    if name == "front" and 2 <= lx <= 5 and 2 <= ly <= 9:
        return "muz"
    return "fur"


def limb_fn(name, lx, ly):
    if name == "bottom":
        return "paw"
    if name != "top" and ly >= 9:
        return "paw"
    return "fur"


def ear_fn(name, lx, ly):
    if name == "front" and lx == 1:
        return "ear"
    return "fur"


def muzzle_fn(name, lx, ly):
    if name == "front" and ly == 0 and lx in (1, 2):
        return "nose"
    return "muz"


def tip_fn(name, lx, ly):
    return "tip"


def build_kinds():
    kinds = [[None] * W for _ in range(H)]
    paint(kinds, 0, 0, 8, 8, 8, head_fn)
    paint(kinds, 16, 16, 8, 12, 4, body_fn)
    paint(kinds, 40, 16, 4, 12, 4, limb_fn)
    paint(kinds, 0, 16, 4, 12, 4, limb_fn)
    paint(kinds, 32, 0, 3, 3, 1, ear_fn)
    paint(kinds, 32, 4, 2, 3, 1, lambda n, x, y: "fur")
    paint(kinds, 40, 0, 4, 2, 3, muzzle_fn)
    paint(kinds, 40, 5, 1, 2, 2, lambda n, x, y: "muz")
    paint(kinds, 48, 5, 4, 4, 4, lambda n, x, y: "fur")
    paint(kinds, 32, 9, 4, 4, 3, tip_fn)
    return kinds


def write_png(path, rows):
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in rows)

    def chunk(tag, data):
        body = struct.pack(">I", len(data)) + tag + data
        return body + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def main():
    os.makedirs(OUT, exist_ok=True)
    kinds = build_kinds()
    for fam, pal in FAMILIES.items():
        for n, density in enumerate(DENSITY):
            rnd = random.Random(fam + "-" + str(n))
            rows = []
            for y in range(H):
                row = []
                for x in range(W):
                    kind = kinds[y][x]
                    if kind is None:
                        row.append((0, 0, 0, 0))
                        continue
                    color = pal[kind]
                    if kind == "fur" and rnd.random() < density:
                        color = speck_color(color, rnd)
                    row.append(color + (255,))
                rows.append(row)
            write_png(os.path.join(OUT, "femboy_%s_%d.png" % (fam, n)), rows)
    print("textures done")


main()
