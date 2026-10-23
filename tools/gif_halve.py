"""Keeps every other frame of a GIF and doubles each kept frame's delay (50 fps -> 25 fps, same loop length).

Safe for GIFs whose frames are disposed to the background (disposal 2), where every frame stands on its own.
Usage: gif_halve.py in.gif out.gif
"""
import struct
import sys


def sub_blocks(d, i):
    """Index just past a run of data sub-blocks starting at i."""
    while d[i]:
        i += d[i] + 1
    return i + 1


def main(src, dst):
    d = open(src, "rb").read()
    out = bytearray(d[:13])
    flags = d[10]
    i = 13
    if flags & 0x80:
        i += 3 * (2 << (flags & 7))
        out += d[13:i]
    pending_gce = None
    frame = 0
    kept = 0
    while i < len(d):
        b = d[i]
        if b == 0x3B:
            out.append(0x3B)
            break
        if b == 0x21:
            end = sub_blocks(d, i + 2)
            block = bytearray(d[i:end])
            if d[i + 1] == 0xF9:
                pending_gce = block
                if (block[3] >> 2) & 7 not in (0, 1, 2):
                    sys.exit("frames are not self-contained (disposal 3); not safe to drop frames")
            else:
                out += block
            i = end
            continue
        if b == 0x2C:
            desc_end = i + 10
            lflags = d[i + 9]
            if lflags & 0x80:
                desc_end += 3 * (2 << (lflags & 7))
            end = sub_blocks(d, desc_end + 1)   # +1 skips the LZW minimum code size
            if frame % 2 == 0:
                if pending_gce is not None:
                    delay = struct.unpack("<H", pending_gce[4:6])[0]
                    pending_gce[4:6] = struct.pack("<H", delay * 2)
                    out += pending_gce
                out += d[i:end]
                kept += 1
            frame += 1
            pending_gce = None
            i = end
            continue
        sys.exit(f"unexpected byte {b:#x} at {i}")
    open(dst, "wb").write(out)
    print(f"{frame} frames -> {kept}, {len(d)} -> {len(out)} bytes")


main(sys.argv[1], sys.argv[2])
