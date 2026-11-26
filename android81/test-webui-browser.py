"""Exercise the offline demo and optional pinned, isolated Nokia HTTPS fixture in a fresh browser."""
import argparse
import functools
import hashlib
import http.server
import json
from pathlib import Path
import ssl
import socket
import threading
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--session", type=Path, help="ignored local fixture credentials; never a production account")
    args = parser.parse_args()
    shots = ROOT / "screenshots/webui-2026-11-25"
    shots.mkdir(exist_ok=True)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(QuietHandler, directory=str(ROOT / "visual-shell/assets")))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    checks = 0

    def check(condition, message):
        nonlocal checks
        if not condition:
            raise AssertionError(message)
        checks += 1

    try:
        with sync_playwright() as runtime:
            edge = Path("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe")
            browser = runtime.chromium.launch(headless=True, **({"executable_path": str(edge)} if edge.exists() else {}))
            context = browser.new_context(viewport={"width": 1440, "height": 1050}, device_scale_factor=1)
            errors = []
            page = context.new_page()
            page.on("pageerror", lambda error: errors.append(str(error)))
            page.goto(f"http://127.0.0.1:{server.server_port}/webui/index.html", wait_until="networkidle")
            check(page.locator("#app-count").inner_text() == "36", "complete demo catalog")
            check(page.locator("#root-count").inner_text().startswith("18"), "default eighteen-entry root")
            check(page.locator("#mode").inner_text() == "DEMO · LOCAL", "explicit demo identity")
            check(page.locator("#upload").is_disabled(), "demo cannot upload")
            check(page.locator("img").evaluate_all("imgs => imgs.every(i => i.complete && i.naturalWidth > 0)"), "bundled icons load offline")
            page.screenshot(path=str(shots / "desktop.png"), full_page=True)
            page.locator('[data-key="ok"]').click()
            page.locator('.phone-cell[aria-label="Unelte"]').click()
            check(page.locator(".phone-title").inner_text() == "Unelte", "folder opens")
            page.locator("#phone-screen").press("ArrowRight")
            check(page.locator(".phone-cell.selected").get_attribute("aria-label") == "Reportofon", "D-pad selects next app")
            page.locator("#phone-screen").press("Escape")
            check(page.locator(".phone-cell.selected").get_attribute("aria-label") == "Unelte", "Back preserves root folder focus")
            page.locator("#phone-screen").press("Enter")
            check(page.locator(".phone-cell.selected").get_attribute("aria-label") == "Reportofon", "folder child focus restored")
            page.locator("#phone-screen").press("1")
            page.locator("#phone-screen").press("Enter")
            check(page.locator(".demo-app h3").inner_text() == "Calculator", "demo application route")
            page.locator("#phone-screen").press("Escape")
            check(page.locator(".phone-title").inner_text() == "Unelte", "app return keeps folder")
            page.locator('[data-tab="apps"]').click()
            page.get_by_label("Folder pentru Calculator", exact=True).select_option("1")
            page.locator("#save-layout").click()
            check("Telefonul nu a fost modificat" in page.locator("#message").inner_text(), "local save cannot claim device changes")
            page.reload(wait_until="networkidle")
            page.locator('[data-tab="apps"]').click()
            check(page.get_by_label("Folder pentru Calculator", exact=True).input_value() == "1", "demo layout survives reload")
            page.screenshot(path=str(shots / "folders.png"), full_page=True)
            page.locator('[data-tab="appearance"]').click()
            page.get_by_role("button", name="Ocean", exact=True).click()
            check(page.locator("#phone-screen").evaluate("n => getComputedStyle(n).getPropertyValue('--accent').trim()") == "#58b6be", "theme preview updates")
            page.locator("#view-mode").select_option("list")
            page.locator("#save-appearance").click()
            page.locator('[data-key="ok"]').click()
            check(page.locator(".phone-list").count() == 1, "list preview")
            page.screenshot(path=str(shots / "appearance.png"), full_page=True)
            page.locator("#reset-demo").click()
            page.locator('[data-tab="overview"]').click()
            page.set_viewport_size({"width": 390, "height": 844})
            check(page.evaluate("document.documentElement.scrollWidth <= innerWidth"), "no mobile horizontal overflow")
            page.screenshot(path=str(shots / "mobile.png"), full_page=True)
            check(not errors, "no demo JavaScript errors: " + repr(errors))
            context.close()
            if args.session:
                credentials = json.loads(args.session.read_text(encoding="utf-8"))
                endpoint = urlparse(credentials["endpoint"])
                check(endpoint.scheme == "https" and endpoint.hostname == "127.0.0.1" and endpoint.port == 18765, "fixture is local TLS only")
                tls = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
                tls.check_hostname = False
                tls.verify_mode = ssl.CERT_NONE
                with socket.create_connection(("127.0.0.1", 18765), timeout=10) as connection:
                    with tls.wrap_socket(connection, server_hostname="127.0.0.1") as secured:
                        actual = hashlib.sha256(secured.getpeercert(binary_form=True)).hexdigest()
                check(actual == credentials["fingerprint"].replace(":", "").lower(), "Nokia fixture certificate pinned before token use")
                context = browser.new_context(viewport={"width": 1440, "height": 1050}, ignore_https_errors=True)
                page = context.new_page()
                page.on("pageerror", lambda error: errors.append(str(error)))
                page.goto(credentials["endpoint"], wait_until="networkidle")
                page.locator("#username").fill(credentials["username"])
                page.locator("#login-password").fill(credentials["password"])
                page.get_by_role("button", name="Conectează", exact=True).click()
                page.wait_for_function("() => document.getElementById('mode').textContent === 'NOKIA · CONECTAT'")
                check(page.locator("#app-count").inner_text() == "36", "live catalog through HTTPS")
                check(page.locator("img").evaluate_all("imgs => imgs.every(i => i.complete && i.naturalWidth > 0)"), "icons work under device CSP")
                page.locator('[data-tab="apps"]').click()
                page.get_by_label("Folder pentru Calculator", exact=True).select_option("1")
                page.locator("#save-layout").click()
                page.wait_for_function("() => document.getElementById('message').textContent.includes('aplicat pe Nokia')")
                page.locator("#refresh").click()
                page.wait_for_function("() => document.getElementById('message').textContent.includes('actualizată')")
                check(page.get_by_label("Folder pentru Calculator", exact=True).input_value() == "1", "live layout persists through refresh")
                page.locator('[data-tab="appearance"]').click()
                page.get_by_role("button", name="Ocean", exact=True).click()
                page.locator("#save-appearance").click()
                page.wait_for_function("() => document.getElementById('message').textContent.includes('Aspectul a fost aplicat')")
                check(page.get_by_role("button", name="Ocean", exact=True).get_attribute("aria-pressed") == "true", "live appearance save")
                page.locator('[data-tab="files"]').click()
                page.wait_for_selector("#file-list li")
                check(page.locator("#file-list li").count() >= 1, "isolated file list supports empty state")
                page.locator("#upload-file").set_input_files({"name": "browser-fixture.txt", "mimeType": "text/plain", "buffer": b"KeyShell browser upload\n"})
                page.locator("#upload").click()
                page.wait_for_function("() => document.getElementById('message').textContent.includes('Fișier trimis')")
                row = page.locator("#file-list li").filter(has_text="browser-fixture.txt")
                with page.expect_download() as download:
                    row.get_by_role("button", name="Descarcă").click()
                check(Path(download.value.path()).read_bytes() == b"KeyShell browser upload\n", "HTTPS upload/download byte match")
                check(page.evaluate("!localStorage.getItem('token') && !sessionStorage.getItem('token')") and page.locator("#login-password").input_value() == "", "token not stored or left in form")
                page.locator('[data-tab="security"]').click()
                page.wait_for_selector("#user-list li")
                check("reader" in page.locator("#user-list").inner_text(), "administrator sees role list")
                page.locator("#web-theme").select_option("1")
                page.locator("#save-webui").click()
                page.wait_for_function("() => document.getElementById('message').textContent.includes('Setările WebUI')")
                check(page.locator("html").get_attribute("data-web-theme") == "1", "WebUI theme persists separately from phone appearance")
                page.locator("#current-password").fill(credentials["password"])
                page.locator("#new-password").fill("browser-rotated-fixture-password")
                page.locator("#confirm-password").fill("browser-rotated-fixture-password")
                page.locator("#change-password").click()
                page.wait_for_function("() => document.getElementById('message').textContent.includes('Parola a fost schimbată')", timeout=60000)
                check(page.locator("#current-password").input_value() == "" and page.locator("#new-password").input_value() == "", "password forms clear after rotation")
                page.locator("#refresh").click()
                page.wait_for_function("() => document.getElementById('message').textContent.includes('actualizată')")
                check(page.locator("#mode").inner_text() == "NOKIA · CONECTAT", "password rotation keeps current session")
                reader_context = browser.new_context(viewport={"width": 1280, "height": 900}, ignore_https_errors=True)
                reader_page = reader_context.new_page()
                reader_page.goto(credentials["endpoint"], wait_until="networkidle")
                reader_page.locator("#username").fill(credentials["reader"])
                reader_page.locator("#login-password").fill(credentials["readerPassword"])
                reader_page.get_by_role("button", name="Conectează", exact=True).click()
                reader_page.wait_for_function("() => document.getElementById('mode').textContent === 'NOKIA · CONECTAT'", timeout=60000)
                reader_page.locator('[data-tab="apps"]').click()
                check(reader_page.locator("#save-layout").is_disabled() and reader_page.get_by_label("Folder pentru Calculator", exact=True).is_disabled(), "read-only account cannot edit launcher")
                reader_page.locator('[data-tab="security"]').click()
                check(reader_page.locator("#admin-controls").is_hidden() and reader_page.locator("#password-form").is_visible(), "viewer has own password control without admin controls")
                reader_context.close()
                page.locator("#disconnect").click()
                check(page.locator("#mode").inner_text() == "DEMO · LOCAL", "explicit disconnect restores demo")
                check(not errors, "no live JavaScript errors: " + repr(errors))
                context.close()
            browser.close()
        print(f"PASS {checks} browser checks; screenshots use public demo data only")
    finally:
        server.shutdown()
        server.server_close()


if __name__ == "__main__":
    main()
