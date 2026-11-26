"""Pinned real-Wi-Fi WebUI smoke. Optional Calculator launch; never calls or sends SMS.

Password is read from stdin, not command arguments. Output contains no private rows.
Example: python android81/test-webui-handset.py --identity PRIVATE.json --exercise-controls
Identity JSON contains endpoint and certificate fingerprint, not credentials.
"""
import argparse
import hashlib
import http.client
import ipaddress
import json
from pathlib import Path
import ssl
import sys
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--identity',type=Path,required=True)
parser.add_argument('--username',default='admin')
parser.add_argument('--exercise-controls',action='store_true',help='open Calculator on Nokia')
args=parser.parse_args()
password=sys.stdin.readline().rstrip('\r\n')
if not password:raise SystemExit('Password required on stdin')
meta=json.loads(args.identity.read_text(encoding='utf-8'));origin=meta['endpoint'].rstrip('/');url=urlparse(origin)
if url.scheme!='https' or not ipaddress.ip_address(url.hostname).is_private or url.port!=18764:raise SystemExit('Expected private Nokia HTTPS endpoint on port 18764')
expected=meta['fingerprint'].replace(':','').lower();statuses=[]
def exchange(method,path,body=None,headers=None):
    connection=http.client.HTTPSConnection(url.hostname,url.port,context=ssl._create_unverified_context(),timeout=65)
    try:
        connection.connect()
        if hashlib.sha256(connection.sock.getpeercert(binary_form=True)).hexdigest()!=expected:raise RuntimeError('Certificate mismatch; request refused')
        connection.request(method,path,body=body,headers=headers or {});response=connection.getresponse();data=response.read();statuses.append((path,response.status))
        return response.status,dict(response.getheaders()),data
    finally:connection.close()
with sync_playwright() as runtime:
    browser=runtime.chromium.launch(headless=True,executable_path='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
    try:
        context=browser.new_context(viewport={'width':1440,'height':1000});page=context.new_page();errors=[];page.on('pageerror',lambda error:errors.append(str(error)))
        def proxy(route):
            request=route.request
            headers={key:value for key,value in request.all_headers().items() if key.lower() not in ('host','content-length','connection','accept-encoding')}
            status,received,data=exchange(request.method,urlparse(request.url).path,request.post_data_buffer,headers)
            route.fulfill(status=status,headers={key:value for key,value in received.items() if key.lower() not in ('connection','content-length')},body=data)
        page.route(origin+'/**',proxy);page.goto(origin+'/',wait_until='networkidle')
        page.locator('#username').fill(args.username);page.locator('#login-password').fill(password);password=''
        page.locator('#connect-form button[type=submit]').click();page.wait_for_function("() => document.getElementById('mode').textContent.includes('CONECTAT')",timeout=70000)
        page.locator('[data-tab=remote]').click();page.wait_for_function("() => document.querySelector('#remote-screen').naturalWidth===240",timeout=30000)
        if args.exercise_controls:
            page.locator('[data-remote-key=wake]').click();page.wait_for_timeout(1600)
            page.locator('#remote-app').select_option('4');page.locator('#remote-launch').click();page.wait_for_timeout(2200)
        if page.locator('#remote-screen').evaluate('el=>el.naturalHeight')!=320:raise AssertionError('Screen dimensions')
        private=Path(__file__).resolve().parent/'output/webui-remote';private.mkdir(exist_ok=True)
        page.evaluate('() => window.scrollTo(0,0)');page.screenshot(path=str(private/'actual-browser-control.png'),full_page=True)
        page.locator('[data-tab=calls]').click();page.locator('#current-calls p').first.wait_for(timeout=20000)
        page.locator('[data-tab=sms]').click();page.wait_for_timeout(1000)
        page.locator('[data-tab=contacts]').click();page.wait_for_timeout(1000)
        if errors:raise AssertionError('Browser script error; private content not exported')
        if any(status!=200 for _,status in statuses):raise AssertionError('A phone endpoint failed; private response not exported')
        if args.exercise_controls:
            page.locator('[data-tab=remote]').click();page.locator('#remote-keys [data-remote-key=back]').click();page.wait_for_timeout(1400)
        page.locator('#disconnect').click();page.wait_for_timeout(300)
        (private/'wifi-browser-result.json').write_text(json.dumps({'passed':True,'private_rows_exported':False,'call_or_sms_sent':False,'certificate_pinned_every_connection':True,'requests':len(statuses)},indent=2),encoding='utf-8')
        print('PASS real Wi-Fi browser: pinned each connection, owner login, CSP frame 240x320, phone state/history/messages/contacts; no call or SMS sent.')
    finally:browser.close()
