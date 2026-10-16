"""Minimal Firefox/B2G remote debugging protocol (RDP) client over an adb-forwarded socket.

Usage: python3 rdp.py <command> [args]
  info                      list root actors / traits
  getpref <name>            read a preference (char/bool/int)
  setchar <name> <value>    set a char preference
"""
import json
import socket
import sys
import time

HOST, PORT = "127.0.0.1", 6000


class RDP:
    def __init__(self):
        self.s = socket.create_connection((HOST, PORT), timeout=15)
        self.buf = b""
        self.hello = self.recv()

    def recv(self):
        while True:
            i = self.buf.find(b":")
            if i > 0 and self.buf[:i].isdigit():
                n = int(self.buf[:i])
                if len(self.buf) >= i + 1 + n:
                    msg = self.buf[i + 1:i + 1 + n]
                    self.buf = self.buf[i + 1 + n:]
                    return json.loads(msg.decode("utf-8"))
            chunk = self.s.recv(65536)
            if not chunk:
                raise EOFError("connection closed")
            self.buf += chunk

    def call(self, to, type_, **kw):
        body = json.dumps(dict(to=to, type=type_, **kw)).encode("utf-8")
        self.s.sendall(str(len(body)).encode() + b":" + body)
        while True:
            r = self.recv()
            if r.get("from") == to:
                return r


if __name__ == "__main__":
    cmd = sys.argv[1]
    c = RDP()
    print("hello:", json.dumps(c.hello)[:300])
    root = c.call("root", "listTabs")
    actors = {k: v for k, v in root.items() if k.lower().endswith("actor")}
    if cmd == "info":
        print("root keys:", sorted(root.keys()))
        print("actors:", json.dumps(actors, indent=1))
    elif cmd == "install":
        import base64
        import io
        import os
        import zipfile
        src = sys.argv[2]
        appid = sys.argv[3]
        mode = os.environ.get("ZMODE", "deflate")      # deflate | stored | infozip
        chunk_size = int(os.environ.get("CHUNK", "49152"))
        if mode == "infozip":
            import subprocess
            subprocess.run("cd %s && rm -f /tmp/pkg.zip && zip -r -X -q /tmp/pkg.zip ." % src, shell=True, check=True)
            data = open("/tmp/pkg.zip", "rb").read()
        else:
            bio = io.BytesIO()
            comp = zipfile.ZIP_STORED if mode == "stored" else zipfile.ZIP_DEFLATED
            with zipfile.ZipFile(bio, "w", comp) as z:
                for base, _, files in os.walk(src):
                    for f in sorted(files):
                        p = os.path.join(base, f)
                        z.write(p, os.path.relpath(p, src).replace(os.sep, "/"))
            data = bio.getvalue()
        print("package bytes:", len(data), "mode:", mode, "chunk:", chunk_size)
        wa = root["webappsActor"]
        up = c.call(wa, "uploadPackage")
        print("uploadPackage ->", json.dumps(up)[:300])
        ua = up.get("actor")
        if not ua:
            sys.exit("no upload actor")
        for i in range(0, len(data), chunk_size):
            r = c.call(ua, "chunk", chunk=base64.b64encode(data[i:i + chunk_size]).decode())
            if "error" in r:
                sys.exit("chunk error: " + json.dumps(r))
        print("done ->", json.dumps(c.call(ua, "done"))[:200])
        print("install ->", json.dumps(c.call(wa, "install", appId=appid, upload=ua))[:600])
        time.sleep(2)
        print("getAll ->", json.dumps(c.call(wa, "getAll"))[:400])
    elif cmd == "getpref":
        pa = root["preferenceActor"]
        for t in ("getCharPref", "getBoolPref", "getIntPref"):
            r = c.call(pa, t, value=sys.argv[2])
            print(t, "->", json.dumps(r)[:200])
    elif cmd == "setchar":
        pa = root["preferenceActor"]
        print(json.dumps(c.call(pa, "setCharPref", name=sys.argv[2], value=sys.argv[3])))
        print("verify:", json.dumps(c.call(pa, "getCharPref", value=sys.argv[2])))
