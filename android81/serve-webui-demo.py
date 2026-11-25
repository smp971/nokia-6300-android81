"""Serve the public, offline KeyShell demo on this PC only. No phone API or user data."""
from pathlib import Path
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import argparse

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=18766)
    args = parser.parse_args()
    assets = Path(__file__).resolve().parent / "visual-shell/assets"
    with ThreadingHTTPServer(("127.0.0.1", args.port), partial(SimpleHTTPRequestHandler, directory=str(assets))) as server:
        print(f"KeyShell demo: http://127.0.0.1:{server.server_port}/webui/", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
