from http.server import BaseHTTPRequestHandler, HTTPServer
from collections import deque
import json
from pathlib import Path

LOG = r'\\wsl.localhost\kaios\root\android81-leo\logs\framework-build.log'
PAGE = '''<!doctype html><html lang="ro"><meta charset="utf-8"><title>Nokia 6300 — output LIVE</title><style>body{background:#10151c;color:#e0e9f5;font:15px monospace;padding:24px}h1{font:24px sans-serif;color:#7cd9ff}pre{white-space:pre-wrap;line-height:1.5}small{color:#a6b6ca}</style><h1>Nokia 6300 · Android 8.1</h1><small>Jurnal real de compilare · actualizare la 3 secunde</small><pre id="log">Se citește jurnalul…</pre><script>async function update(){try{let r=await fetch('/log',{cache:'no-store'});document.getElementById('log').textContent=await r.text()}catch(e){document.getElementById('log').textContent='Conexiunea cu jurnalul este întreruptă: '+e}}update();setInterval(update,3000)</script></html>'''

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/log':
            try:
                candidates = [Path(LOG), Path(LOG).with_name('keymaster-build.log'),
                              Path(LOG).with_name('settings-fix-build.log'),
                              Path(LOG).with_name('radio-compat-build.log'),
                              Path(LOG).with_name('radio-dualsim-build.log'),
                              Path(__file__).parent/'output/radio-audit/linker-probe.txt',
                              Path(__file__).parent/'output/internal-install.json']
                current = max((p for p in candidates if p.exists()), key=lambda p: p.stat().st_mtime)
                with current.open(encoding='utf-8', errors='replace') as stream:
                    body = ''.join(deque(stream, maxlen=45))
                body = current.name + '\n\n' + body
            except OSError as error:
                body = str(error)
            content_type = 'text/plain; charset=utf-8'
        elif self.path == '/':
            body, content_type = PAGE, 'text/html; charset=utf-8'
        else:
            self.send_error(404)
            return
        payload = body.encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)
    def log_message(self, *args):
        pass

HTTPServer(('127.0.0.1', 18763), Handler).serve_forever()

