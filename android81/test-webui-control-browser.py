"""Browser contracts with synthetic phone APIs. Never connects to the handset or makes calls/SMS."""
import functools
import http.server
import json
from pathlib import Path
import threading
import mimetypes
import runpy
import time
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

ROOT=Path(__file__).resolve().parent
class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self,*_):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),functools.partial(Quiet,directory=str(ROOT/'visual-shell/assets')))
threading.Thread(target=server.serve_forever,daemon=True).start()
checks=0
frame_requests=0
interactive=True
def check(ok,why):
    global checks
    if not ok:raise AssertionError(why)
    checks+=1
try:
    with sync_playwright() as runtime:
        browser=runtime.chromium.launch(headless=True,executable_path='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
        context=browser.new_context(viewport={'width':1440,'height':1100})
        page=context.new_page();errors=[];sent=[];role='admin';features={'remote':True,'communication':True};ticket=0
        page.on('pageerror',lambda error:errors.append(str(error)))
        def route(request):
            global ticket,role,frame_requests,interactive
            url=urlparse(request.request.url)
            if not url.path.startswith('/api/'):
                relative=url.path.lstrip('/') or 'index.html'
                file=ROOT/'visual-shell/assets'/relative if relative.startswith('catalog-icons/') else ROOT/'visual-shell/assets/webui'/relative
                if not file.is_file():request.fulfill(status=404,body='');return
                request.fulfill(status=200,content_type=mimetypes.guess_type(str(file))[0] or 'application/octet-stream',body=file.read_bytes());return
            path=request.request.url.split('/api/')[-1]
            data=json.loads(request.request.post_data or '{}')
            if path=='login':result={'token':'synthetic-test-token'}
            elif path=='desktop':
                result=runpy.run_path(str(ROOT/'webui-test-fixture.py'))['model']()
                result.update(account={'username':role,'role':role},webui={'theme':0,'autoStart':True,'appearance':True,'layout':True,'files':True,**features})
            elif path=='phone/state':result={'sims':[{'slot':0,'label':'SIM 1 · Demo'}],'calls':[],'defaultDialer':True,'smsPending':False,'smsStatus':''}
            elif path=='phone/history':result={'rows':[{'number':'200','name':'Extensie demo','date':1800000000000,'duration':12,'type':2}]}
            elif path=='phone/contacts':result={'rows':[{'name':'<img src=x onerror=alert(1)>','number':'200'},{'name':'Contact demonstrativ','number':'201'}]}
            elif path=='phone/messages':result={'rows':[{'number':'200','body':'Mesaj demo · șț <script>bad()</script>','date':1800000000000,'type':1,'sub':1}]}
            elif path=='phone/prepare':
                ticket+=1;sent.append(('prepare',data));result={**data,'ticket':str(ticket),'segments':1,'expiresSeconds':60}
            elif path=='phone/confirm':sent.append(('confirm',data));result={'accepted':True,'state':'queued'}
            elif path=='remote/info':result={'width':240,'height':320,'rootAvailable':True}
            elif path=='remote/screen':
                frame_requests+=1
                headers={'ETag':'"fixture-frame"','X-KeyShell-Width':'240','X-KeyShell-Height':'320','X-KeyShell-Interactive':str(interactive).lower()}
                if request.request.headers.get('if-none-match')=='"fixture-frame"':request.fulfill(status=304,headers=headers);return
                request.fulfill(status=200,headers=headers,content_type='image/png',body=(ROOT/'screenshots/webui-controls-2026-11-25/native-controls.png').read_bytes());return
            elif path.startswith('remote/'):
                if path=='remote/key' and data.get('key') in ('sleep','wake'):interactive=data['key']=='wake'
                sent.append((path,data));result={'accepted':True}
            else:result={}
            request.fulfill(status=200,content_type='application/json',body=json.dumps(result))
        page.route('https://keyshell.fixture/**',route)
        page.goto('https://keyshell.fixture/',wait_until='networkidle')
        page.locator('[data-tab=remote]').click();check(page.locator('#remote .control-gate').is_visible(),'anonymous sees connection gate')
        check(page.locator('#battery').inner_text()=='—' and page.locator('#app-count').inner_text()=='—','no invented statistics before login')
        check(page.locator('#phone-screen').count()==0 and page.locator('#reset-demo').count()==0,'virtual demo removed')
        page.locator('#login-password').fill('synthetic-password');page.locator('#connect-form button[type=submit]').click();page.wait_for_function("document.querySelector('#mode').textContent==='NOKIA · CONECTAT'")
        page.locator('[data-tab=calls]').click();page.locator('#call-number').fill('200');page.locator('#call-prepare').click();page.locator('#phone-confirmation').wait_for(state='visible')
        check(not any(kind=='confirm' for kind,_ in sent),'prepare does not initiate call')
        check('SIM 1' in page.locator('#confirmation-summary').inner_text(),'confirmation identifies destination and SIM')
        page.locator('#confirmation-cancel').click();check(not any(kind=='confirm' for kind,_ in sent),'cancel does not initiate call')
        page.locator('#call-prepare').click();page.locator('#phone-confirmation').wait_for(state='visible');page.locator('#confirmation-send').click();page.wait_for_timeout(100)
        check(len([x for x in sent if x[0]=='confirm'])==1,'single call confirmation')
        page.locator('[data-tab=sms]').click();page.locator('#sms-number').fill('200');page.locator('#sms-body').fill('Salut · șț');page.locator('#sms-prepare').click();page.locator('#phone-confirmation').wait_for(state='visible')
        check(page.locator('#confirmation-body').inner_text()=='Salut · șț','SMS preview retains Unicode')
        page.locator('#confirmation-send').click();page.wait_for_timeout(100);check(len([x for x in sent if x[0]=='confirm'])==2,'single SMS confirmation')
        check('<script>' in page.locator('#sms-list').inner_text(),'message content displayed literally')
        page.locator('[data-tab=contacts]').click();page.locator('#contacts-list li').first.wait_for()
        check(page.locator('#contacts-list img').count()==0,'contact names cannot inject HTML')
        page.locator('#contacts-search').fill('demonstrativ');check(page.locator('#contacts-list li').count()==1,'contact search')
        page.locator('#contacts-list button').filter(has_text='SMS').click();check(page.locator('#sms-number').input_value()=='201','contact populates SMS without sending')
        page.locator('[data-tab=remote]').click();page.wait_for_function("document.querySelector('#remote-screen').naturalWidth>0")
        check(page.locator('#screen-auto').is_checked(),'adaptive live view starts on the remote page')
        page.locator('#remote-keys [data-remote-key=up]').click();page.wait_for_timeout(850)
        check(('remote/key',{'key':'up'}) in sent,'virtual key uses named allowlist API')
        keys_before=len([x for x in sent if x[0]=='remote/key'])
        page.evaluate("() => ['1','2','3','left','right','back'].forEach(k=>document.querySelector('[data-remote-key=\"'+k+'\"]').click())")
        page.wait_for_timeout(1000)
        check([data['key'] for kind,data in [x for x in sent if x[0]=='remote/key'][keys_before:]]==['1','2','3','left','right','back'],'rapid presses retained in order')
        check(page.locator('#remote-screen').locator('xpath=ancestor::*[contains(@class,\"handset\")]').count()>0,'real screen is inside physical handset')
        check(page.locator('.hardware-keypad [data-remote-key]').count()==23,'handset contains complete physical keypad')
        page.locator('[data-remote-key=sleep]').click();page.wait_for_function("() => document.querySelector('#screen-state').textContent.includes('Ecran stins')")
        paused=frame_requests;page.wait_for_timeout(1400);check(frame_requests==paused,'no frame polling while phone display sleeps')
        page.locator('[data-remote-key=wake]').click();page.wait_for_function("() => document.querySelector('#screen-state').textContent===''")
        check(frame_requests>paused,'wake key resumes live image')
        page.locator('#remote-text').fill('Text șț');page.locator('#remote-paste').click();page.wait_for_timeout(100)
        check(('remote/text',{'text':'Text șț'}) in sent,'Unicode paste request')
        check(page.locator('#remote-text').input_value()=='','pasted content cleared from editor')
        check(page.evaluate("Object.keys(localStorage).every(k=>!localStorage.getItem(k).includes('synthetic-test-token'))"),'no auth token in browser storage')
        shots=ROOT/'output/webui-fast/browser-fixtures';shots.mkdir(parents=True,exist_ok=True)
        # Synthetic APIs and a previously published stopped control screenshot only.
        page.evaluate('window.scrollTo(0,0)');page.screenshot(path=str(shots/'remote.png'),full_page=True)
        page.locator('[data-tab=sms]').click();page.locator('#sms-list p').first.wait_for();page.locator('#sms-list p').first.evaluate("el=>el.textContent='Salut! Am ajuns. Vorbim mai târziu?'");page.locator('#sms-body').fill('Salut! Ne auzim în câteva minute.');page.evaluate('window.scrollTo(0,0)');page.screenshot(path=str(shots/'messages.png'),full_page=True)
        page.set_viewport_size({'width':390,'height':844});page.locator('[data-tab=remote]').click()
        page.wait_for_function("() => document.querySelector('#remote-screen').naturalWidth>0")
        check(page.evaluate('document.documentElement.scrollWidth<=innerWidth'),'active handset fits mobile width')
        check(page.locator('.handset').evaluate('el=>el.getBoundingClientRect().width<=innerWidth'),'physical keypad remains inside mobile viewport')
        page.evaluate('window.scrollTo(0,0)');page.screenshot(path=str(shots/'mobile-handset.png'),full_page=True)
        page.set_viewport_size({'width':1440,'height':1100})
        page.locator('#disconnect').click();check(page.locator('#sms-list li').count()==0,'disconnect clears private messages')
        check(not page.locator('#remote-screen').get_attribute('src'),'disconnect releases captured frame')
        role='viewer';page.locator('#login-password').fill('synthetic-password');page.locator('#connect-form button[type=submit]').click();page.wait_for_function("document.querySelector('#mode').textContent==='NOKIA · CONECTAT'")
        page.locator('[data-tab=remote]').click();check(page.locator('#remote .control-private').is_hidden(),'viewer cannot view/control real screen')
        page.locator('[data-tab=sms]').click();check(page.locator('#sms .control-private').is_hidden(),'viewer cannot read messages')
        check(not errors,'no browser script errors')
        page.set_viewport_size({'width':390,'height':844});page.screenshot(path=str(shots/'mobile.png'),full_page=True)
        check(page.evaluate('document.documentElement.scrollWidth<=innerWidth'),'no mobile horizontal overflow')
        browser.close()
    print(f'PASS {checks} synthetic browser control checks; no handset, call or SMS')
finally:server.shutdown();server.server_close()
