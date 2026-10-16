"""Tiny binary AndroidManifest (AXML) encoder – no Android SDK / aapt needed."""
import struct

ANDROID_NS = 'http://schemas.android.com/apk/res/android'
RES = {'label': 0x01010001, 'icon': 0x01010002, 'name': 0x01010003, 'theme': 0x01010000,
       'exported': 0x01010010, 'versionCode': 0x0101021b, 'versionName': 0x0101021c,
       'minSdkVersion': 0x0101020c, 'targetSdkVersion': 0x01010270, 'allowBackup': 0x01010280,
       'enabled': 0x0101000e, 'roundIcon': 0x0101052c}

STR, REF, INT, BOOL = 0x03, 0x01, 0x10, 0x12


class Node:
    def __init__(self, tag, attrs=None, children=None):
        self.tag, self.attrs, self.children = tag, attrs or [], children or []
        # attrs: list of (name, kind, value); name 'android:x' or plain


def _utf16(s):
    return struct.pack('<H', len(s)) + s.encode('utf-16-le') + b'\x00\x00'


def encode(root):
    # collect strings: resource-mapped attribute names first
    res_names, others = [], []

    def walk(n):
        for name, kind, val in n.attrs:
            if name.startswith('android:'):
                short = name[8:]
                if short not in res_names:
                    res_names.append(short)
            elif name not in others:
                others.append(name)
            if kind == STR and val not in others:
                others.append(val)
        if n.tag not in others:
            others.append(n.tag)
        for c in n.children:
            walk(c)
    walk(root)
    res_names.sort(key=lambda s: RES[s])
    for extra in ('android', ANDROID_NS):
        if extra not in others:
            others.append(extra)
    strings = res_names + [s for s in others if s not in res_names]
    idx = {s: i for i, s in enumerate(strings)}

    # string pool
    data = b''
    offsets = []
    for s in strings:
        offsets.append(len(data))
        data += _utf16(s)
    while len(data) % 4:
        data += b'\x00'
    sp_header = 28
    sp_size = sp_header + 4 * len(strings) + len(data)
    pool = struct.pack('<HHIIIIII', 0x0001, sp_header, sp_size, len(strings), 0, 0, sp_header + 4 * len(strings), 0)
    pool += b''.join(struct.pack('<I', o) for o in offsets) + data

    resmap = struct.pack('<HHI', 0x0180, 8, 8 + 4 * len(res_names)) + b''.join(struct.pack('<I', RES[s]) for s in res_names)

    body = b''
    ns_prefix, ns_uri = idx['android'], idx[ANDROID_NS]
    body += struct.pack('<HHIIIII', 0x0100, 16, 24, 1, 0xFFFFFFFF, ns_prefix, ns_uri)

    def elem(n):
        out = b''
        attrs = []
        for name, kind, val in n.attrs:
            if name.startswith('android:'):
                short = name[8:]
                attrs.append((RES[short], ns_uri, idx[short], kind, val))
            else:
                attrs.append((0, 0xFFFFFFFF, idx[name], kind, val))
        attrs.sort(key=lambda a: a[0])
        size = 36 + 20 * len(attrs)
        out += struct.pack('<HHIIIIIHHHHHH', 0x0102, 16, size, 1, 0xFFFFFFFF, 0xFFFFFFFF, idx[n.tag], 0x14, 0x14, len(attrs), 0, 0, 0)
        for _, ns, name_i, kind, val in attrs:
            if kind == STR:
                raw, typ, d = idx[val], STR, idx[val]
            elif kind == BOOL:
                raw, typ, d = 0xFFFFFFFF, BOOL, 0xFFFFFFFF if val else 0
            else:
                raw, typ, d = 0xFFFFFFFF, kind, val & 0xFFFFFFFF
            out += struct.pack('<IIIHBBI', ns, name_i, raw, 8, 0, typ, d)
        for c in n.children:
            out += elem(c)
        out += struct.pack('<HHIIIII', 0x0103, 16, 24, 1, 0xFFFFFFFF, 0xFFFFFFFF, idx[n.tag])
        return out
    body += elem(root)
    body += struct.pack('<HHIIIII', 0x0101, 16, 24, 1, 0xFFFFFFFF, ns_prefix, ns_uri)

    chunks = pool + resmap + body
    return struct.pack('<HHI', 0x0003, 8, 8 + len(chunks)) + chunks
