"""Minimal resources.arsc with a single drawable (the launcher icon) -> resource id 0x7f010000."""
import struct


def _pool(strings):
    data, offs = b'', []
    for s in strings:
        offs.append(len(data))
        data += struct.pack('<H', len(s)) + s.encode('utf-16-le') + b'\x00\x00'
    while len(data) % 4:
        data += b'\x00'
    hs = 28
    size = hs + 4 * len(strings) + len(data)
    return struct.pack('<HHIIIIII', 0x0001, hs, size, len(strings), 0, 0, hs + 4 * len(strings), 0) + \
        b''.join(struct.pack('<I', o) for o in offs) + data


def build(drawable_path='res/drawable/ic_launcher.png', key='ic_launcher', pkg_name='com.dailyboost.app'):
    global_pool = _pool([drawable_path])
    type_pool = _pool(['drawable'])
    key_pool = _pool([key])

    # TypeSpec: header 16 bytes (type,hs,size,id,res0,res1,entryCount) then flags[]
    spec = struct.pack('<HHIBBHI', 0x0202, 16, 20, 1, 0, 0, 1) + struct.pack('<I', 0)

    config = struct.pack('<I', 64) + b'\x00' * 60
    entry = struct.pack('<HHI', 8, 0, 0) + struct.pack('<HBBI', 8, 0, 0x03, 0)
    hs = 20 + 64
    entries_start = hs + 4
    size = entries_start + len(entry)
    typ = struct.pack('<HHIBBHII', 0x0201, hs, size, 1, 0, 0, 1, entries_start) + config + struct.pack('<I', 0) + entry

    name = pkg_name.encode('utf-16-le')[:254].ljust(256, b'\x00')
    phs = 288
    type_off = phs
    key_off = type_off + len(type_pool)
    body = type_pool + key_pool + spec + typ
    psize = phs + len(body)
    package = struct.pack('<HHII', 0x0200, phs, psize, 0x7f) + name + struct.pack('<IIIII', type_off, 1, key_off, 1, 0) + body

    total = 12 + len(global_pool) + len(package)
    return struct.pack('<HHII', 0x0002, 12, total, 1) + global_pool + package
