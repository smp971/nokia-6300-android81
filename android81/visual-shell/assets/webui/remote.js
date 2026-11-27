/* Private data stays in this page's memory. No device content, frames or tickets are persisted. */
(() => {
  'use strict';
  const api = window.KeyShellWeb, $ = id => document.getElementById(id);
  let imageURL = '', screenBusy = false, actionBusy = false, frameTimer = 0, stateTimer = 0;
  let frameTag = '', displayAwake = true, quietFrames = 0, frameDirty = false, inputBusy = false, repeatAt = 0;
  const inputQueue = [];
  let geometry = {width:240,height:320}, contacts = [], messages = [], ticket = '', version = 0;
  const titles = {remote:'Control telefon',calls:'Apeluri',sms:'Mesaje',contacts:'Contacte'};
  const node = (tag,text) => { const value=document.createElement(tag);if(text!==undefined)value.textContent=text;return value; };
  const remote = node('section'); remote.id='remote';remote.className='tab-panel';remote.hidden=true;
  remote.innerHTML = `<div class="section-heading"><div><p class="eyebrow">CONTROL LA DISTANȚĂ</p><h2>Telefonul tău. Tastele lui.</h2><p>Click pe ecran sau folosește tastatura: săgeți, Enter, Escape și cifre.</p></div><span id="remote-latency" class="pill">În așteptare</span></div>
  <p class="control-gate" data-feature="remote"></p><div class="control-private" data-feature="remote" hidden>
  <div class="remote-layout"><div class="handset-column"><div class="handset" aria-label="Telefon KeyShell cu ecran real și tastatură">
    <div class="handset-earpiece" aria-hidden="true"></div><div class="handset-brand">KeyShell <span>4G</span></div>
    <div class="handset-display"><img id="remote-screen" alt="Ecranul real al telefonului" width="240" height="320"><span id="screen-placeholder">Conectează telefonul</span></div>
    <div id="remote-keys" class="hardware-keypad"><div class="handset-navigation"><div id="handset-left" class="handset-sidekeys"></div><div id="handset-dpad" class="handset-dpad"></div><div id="handset-right" class="handset-sidekeys"></div></div><div id="handset-digits" class="handset-digits"></div></div>
    <div class="handset-foot" aria-hidden="true">KEYSHELL · KEYPAD PHONE</div></div>
    <div class="remote-toolbar screen-controls"><label class="switch"><input id="screen-auto" type="checkbox" checked> Live · adaptiv</label><button id="screen-refresh" aria-label="Actualizează ecranul">↻</button></div><p id="screen-state" role="status"></p>
  </div><div class="remote-tools"><section class="panel"><h3>La îndemână</h3><div class="remote-toolbar"><button data-remote-key="wake">Trezește</button><button data-remote-key="sleep">Stinge ecranul</button><button data-remote-key="home">Acasă</button><button data-remote-key="volumeDown">Volum −</button><button data-remote-key="volumeUp">Volum +</button></div><p class="muted">Softkeys, Meniu și Înapoi au aceleași roluri ca pe telefon.</p></section>
  <form id="remote-text-form" class="panel settings-form"><h3>Scrie de la tastatură</h3><label for="remote-text">Text pentru câmpul selectat pe Nokia</label><textarea id="remote-text" maxlength="2048" rows="4" placeholder="Selectează întâi câmpul pe ecranul telefonului"></textarea><p>Textul este copiat în clipboard-ul telefonului și lipit în câmpul activ.</p><button id="remote-paste" class="primary">Lipește pe telefon</button></form>
  <section class="panel settings-form"><h3>Deschide o aplicație</h3><label for="remote-app">Aplicații de pe Nokia</label><select id="remote-app"></select><button id="remote-launch">Deschide pe telefon</button></section></div></div></div>`;
  const calls = node('section'); calls.id='calls';calls.className='tab-panel';calls.hidden=true;
  calls.innerHTML = `<div class="section-heading"><div><h2>Apeluri de pe birou.</h2><p>Apelurile folosesc SIM-ul ales. Sunetul rămâne pe Nokia, în receptor sau difuzor.</p></div><button id="calls-refresh">Actualizează</button></div><p class="control-gate" data-feature="communication"></p><div class="control-private" data-feature="communication" hidden><form id="call-form" class="panel settings-form"><label for="call-number">Număr de telefon</label><input id="call-number" type="tel" maxlength="16" autocomplete="off" required><label for="call-sim">SIM pentru apel</label><select id="call-sim"></select><button id="call-prepare" class="primary">Continuă spre apel</button></form><h3>Apeluri curente</h3><div id="current-calls"></div><h3>Istoric recent</h3><ul id="call-history" class="file-list"></ul></div>`;
  const sms = node('section');sms.id='sms';sms.className='tab-panel';sms.hidden=true;
  sms.innerHTML = `<div class="section-heading"><div><h2>Mesaje, cu loc pentru cuvinte.</h2><p>SMS-uri reale, prin SIM. Trimiterea și livrarea sunt stări distincte.</p></div><button id="sms-refresh">Actualizează</button></div><p class="control-gate" data-feature="communication"></p><div class="control-private" data-feature="communication" hidden><form id="sms-form" class="panel settings-form"><label for="sms-number">Destinatar</label><input id="sms-number" type="tel" maxlength="16" required autocomplete="off"><label for="sms-sim">SIM pentru SMS</label><select id="sms-sim"></select><label for="sms-body">Mesaj · maximum 1.600 caractere / 10 segmente</label><textarea id="sms-body" maxlength="1600" rows="5" required></textarea><button id="sms-prepare" class="primary">Verifică mesajul</button><p id="sms-status"></p></form><label for="sms-search">Caută în cele mai recente 200 de mesaje</label><input id="sms-search" type="search" autocomplete="off"><ul id="sms-list" class="file-list message-list"></ul></div>`;
  const people = node('section');people.id='contacts';people.className='tab-panel';people.hidden=true;
  people.innerHTML = `<div class="section-heading"><div><h2>Oamenii tăi, aproape.</h2><p>Primele 300 de numere din agenda telefonului. Alegerea unui contact completează formularul.</p></div><button id="contacts-refresh">Actualizează</button></div><p class="control-gate" data-feature="communication"></p><div class="control-private" data-feature="communication" hidden><label for="contacts-search">Caută nume sau număr</label><input id="contacts-search" type="search" autocomplete="off"><ul id="contacts-list" class="file-list"></ul></div>`;
  [remote,calls,sms,people].forEach(panel => document.querySelector('.content').insertBefore(panel,document.querySelector('.page-footer')));
  const navigation=document.createDocumentFragment();
  Object.entries(titles).forEach(([id,title]) => {const button=node('button',title);button.dataset.tab=id;button.onclick=()=>api.showTab(id);navigation.append(button);});
  document.querySelector('.sidebar nav').prepend(navigation);
  const dialog=node('dialog');dialog.id='phone-confirmation';dialog.className='phone-confirmation';
  dialog.innerHTML='<h2 id="confirmation-title"></h2><p id="confirmation-summary"></p><pre id="confirmation-body"></pre><p>Confirmare valabilă 60 de secunde. Nu retrimite dacă rezultatul este neclar; verifică starea pe telefon.</p><div class="remote-toolbar"><button id="confirmation-cancel" autofocus>Anulează</button><button id="confirmation-send" class="primary">Confirmă</button></div>';
  document.body.append(dialog);
  const active = feature => {const s=api.state();return s.live&&s.model.account.role==='admin'&&s.model.webui[feature]===true;};
  async function run(work) {if(actionBusy)return;actionBusy=true;try{await work();}catch(e){api.notice(e.message,true);}finally{actionBusy=false;}}
  const json = async (path,data) => (await api.request(path,data===undefined?{}:{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)})).json();
  function releaseFrame(){if(imageURL)URL.revokeObjectURL(imageURL);imageURL='';frameTag='';$('remote-screen').removeAttribute('src');$('screen-placeholder').hidden=false;}
  function cleanup(){version++;clearInputs();frameTag='';quietFrames=0;clearTimeout(frameTimer);clearTimeout(stateTimer);releaseFrame();ticket='';if(dialog.open)dialog.close();contacts=[];messages=[];['contacts-list','sms-list','call-history','current-calls'].forEach(id=>$(id).replaceChildren());['remote-text','sms-body','sms-number','call-number'].forEach(id=>$(id).value='');$('screen-auto').checked=true;}
  function access(){
    document.body.classList.toggle('control-page',api.state().live&&Object.hasOwnProperty.call(titles,api.state().tab));
    if(!api.state().live)cleanup();
    document.querySelectorAll('.control-gate').forEach(el=>{el.hidden=active(el.dataset.feature);el.textContent=!api.state().live?'Conectează-te la Nokia pentru control real.':api.state().model.account.role!=='admin'?'Disponibil doar administratorilor.':'Activează această funcționalitate din Administrare sau din aplicația WebUI de pe telefon.';});
    document.querySelectorAll('.control-private').forEach(el=>el.hidden=!active(el.dataset.feature));
    if(!active('remote')){clearTimeout(frameTimer);releaseFrame();}
    if(!active('communication')){clearTimeout(stateTimer);ticket='';if(dialog.open)dialog.close();contacts=[];messages=[];['contacts-list','sms-list','call-history','current-calls'].forEach(id=>$(id).replaceChildren());}
    $('remote-app').replaceChildren();api.state().model.apps.forEach(app=>{const option=node('option',app.name);option.value=app.id;$('remote-app').append(option);});
  }
  function scheduleFrame(delay = 30) {
    clearTimeout(frameTimer);
    if (screenBusy) { frameDirty = true; return; }
    if (active('remote') && api.state().tab === 'remote' && !document.hidden) frameTimer = setTimeout(frame,delay);
  }
  async function frame() {
    if (screenBusy || !active('remote') || api.state().tab !== 'remote' || document.hidden) return;
    screenBusy=true; frameDirty=false;
    const ownVersion=version, started=performance.now();
    try {
      const response=await api.request('/api/remote/screen',{headers:frameTag?{'If-None-Match':frameTag}:{}});
      if (ownVersion!==version || !active('remote') || api.state().tab!=='remote') return;
      geometry.width=Number(response.headers.get('X-KeyShell-Width'))||geometry.width;
      geometry.height=Number(response.headers.get('X-KeyShell-Height'))||geometry.height;
      displayAwake=response.headers.get('X-KeyShell-Interactive')!=='false';
      $('screen-state').textContent=!displayAwake?'Ecran stins. Apasă Trezește, apoi OK → * dacă tastatura este blocată.':'';
      if (response.status===304) quietFrames++;
      else {
        const next=URL.createObjectURL(await response.blob());
        const loaded=new Image();loaded.src=next;
        try { await loaded.decode(); } catch (_) { URL.revokeObjectURL(next);throw Error('Imaginea telefonului nu a putut fi citită.'); }
        if (ownVersion!==version || !active('remote') || api.state().tab!=='remote') { URL.revokeObjectURL(next);return; }
        const previous=imageURL;imageURL=next;$('remote-screen').src=next;$('screen-placeholder').hidden=true;
        if (previous) URL.revokeObjectURL(previous);
        frameTag=response.headers.get('ETag')||'';quietFrames=0;
      }
    } catch(error) { $('screen-auto').checked=false;api.notice(error.message,true); }
    finally {
      screenBusy=false;
      if (active('remote') && api.state().tab==='remote' && !document.hidden) {
        if (frameDirty || ownVersion!==version) scheduleFrame(30);
        else if ($('screen-auto').checked && displayAwake) scheduleFrame(Math.max(50,(quietFrames>10?1200:quietFrames>3?600:250)-(performance.now()-started)));
      }
    }
  }
  function clearInputs() {
    inputQueue.splice(0).forEach(item=>item.reject(Error('Control întrerupt. Apăsarea nu a fost trimisă.')));
  }
  async function sendInputs() {
    if (inputBusy) return;
    inputBusy=true;
    try {
      while (inputQueue.length && active('remote') && api.state().tab==='remote') {
        const item=inputQueue.shift(), ownVersion=version, started=performance.now();
        try {
          const result=await json(item.path,item.data);
          if (ownVersion===version) {
            quietFrames=0;$('remote-latency').textContent=Math.round(performance.now()-started)+' ms / comandă';scheduleFrame(25);
          }
          item.resolve(result);
        } catch(error) { item.reject(error);clearInputs();break; }
      }
    } finally { inputBusy=false; }
  }
  function enqueue(path,data) {
    if (!active('remote') || api.state().tab!=='remote') return Promise.reject(Error('Deschide controlul telefonului.'));
    if (inputQueue.length>=64) return Promise.reject(Error('Așteaptă procesarea tastelor deja apăsate.'));
    return new Promise((resolve,reject)=>{inputQueue.push({path,data,resolve,reject});sendInputs();});
  }
  async function key(value) {
    if ((value==='call'||value==='end') && !confirm(value==='call'?'Transmiți butonul verde către telefon? Poate iniția sau accepta un apel.':'Transmiți butonul de închidere a apelului?')) return;
    return enqueue('/api/remote/key',{key:value});
  }
  function physicalKey(value,label,letters='') {
    const button=node('button');button.type='button';button.dataset.remoteKey=value;button.setAttribute('aria-label',label);
    button.append(node('span',label));if(letters)button.append(node('small',letters));return button;
  }
  [['softLeft','—'],['call','☎'],['menu','▦']].forEach(([value,label])=>$('handset-left').append(physicalKey(value,label)));
  [['softRight','—'],['end','⏻'],['back','↶']].forEach(([value,label])=>$('handset-right').append(physicalKey(value,label)));
  [['up','↑'],['left','←'],['ok','OK'],['right','→'],['down','↓']].forEach(([value,label])=>{const button=physicalKey(value,label);button.classList.add('dpad-'+value);$('handset-dpad').append(button);});
  ['1','2','3','4','5','6','7','8','9','star','0','hash'].forEach((value,i)=>$('handset-digits').append(physicalKey(value,value==='star'?'*':value==='hash'?'#':value,['','ABC','DEF','GHI','JKL','MNO','PQRS','TUV','WXYZ','','+',''][i])));
  document.querySelectorAll('[data-remote-key]').forEach(button=>{
    const names={softLeft:'Soft stânga',softRight:'Soft dreapta',menu:'Meniu',back:'Înapoi',call:'Apel',end:'Închide apel',up:'Sus',down:'Jos',left:'Stânga',right:'Dreapta'};
    if(names[button.dataset.remoteKey])button.setAttribute('aria-label',names[button.dataset.remoteKey]);
    button.onclick=()=>{button.classList.add('key-pressed');setTimeout(()=>button.classList.remove('key-pressed'),120);key(button.dataset.remoteKey).catch(error=>api.notice(error.message,true));};
  });
  $('remote-screen').onclick=event=>{
    if (!active('remote') || !imageURL) return;
    const box=event.currentTarget.getBoundingClientRect();
    enqueue('/api/remote/tap',{x:Math.max(0,Math.min(geometry.width-1,Math.floor((event.clientX-box.left)*geometry.width/box.width))),y:Math.max(0,Math.min(geometry.height-1,Math.floor((event.clientY-box.top)*geometry.height/box.height)))}).catch(error=>api.notice(error.message,true));
  };
  remote.addEventListener('keydown',event=>{
    if (/INPUT|TEXTAREA|SELECT|BUTTON/.test(event.target.tagName) || event.altKey || event.ctrlKey || event.metaKey) return;
    const value={ArrowUp:'up',ArrowDown:'down',ArrowLeft:'left',ArrowRight:'right',Enter:'ok',Escape:'back',Backspace:'back','*':'star','#':'hash'}[event.key]||(/^[0-9]$/.test(event.key)?event.key:null);
    if (value) {event.preventDefault();if(event.repeat&&(!value.match(/^(up|down|left|right)$/)||performance.now()-repeatAt<90))return;repeatAt=performance.now();key(value).catch(error=>api.notice(error.message,true));}
  });
  $('remote-screen').tabIndex=0;
  $('screen-refresh').onclick=()=>scheduleFrame(0);
  $('screen-auto').onchange=()=>{clearTimeout(frameTimer);if($('screen-auto').checked)scheduleFrame(0);};
  $('remote-text-form').onsubmit=event=>{event.preventDefault();run(async()=>{await enqueue('/api/remote/text',{text:$('remote-text').value});$('remote-text').value='';api.notice('Text lipit prin clipboard pe Nokia.');});};
  $('remote-launch').onclick=()=>run(()=>enqueue('/api/remote/launch',{id:Number($('remote-app').value)}));
  function selectNumber(number,kind){$(kind==='sms'?'sms-number':'call-number').value=number.replace(/[ ()-]/g,'');api.showTab(kind==='sms'?'sms':'calls');}
  function row(label,number){const li=node('li');const body=node('span',label);li.append(body);const buttons=node('div');buttons.className='remote-toolbar';[['Apel','call'],['SMS','sms']].forEach(([title,kind])=>{const b=node('button',title);b.onclick=()=>selectNumber(number,kind);buttons.append(b);});li.append(buttons);return li;}
  function renderContacts(){const query=$('contacts-search').value.toLocaleLowerCase();$('contacts-list').replaceChildren();contacts.filter(c=>(c.name+' '+c.number).toLocaleLowerCase().includes(query)).forEach(c=>$('contacts-list').append(row((c.name||'Contact')+'\n'+c.number,c.number)));if(!$('contacts-list').children.length)$('contacts-list').append(node('li','Niciun contact găsit.'));}
  function renderMessages(){const query=$('sms-search').value.toLocaleLowerCase();$('sms-list').replaceChildren();messages.filter(m=>(m.number+' '+m.body).toLocaleLowerCase().includes(query)).forEach(m=>{const li=row(m.number+' · '+new Date(m.date).toLocaleString('ro-RO')+' · '+({1:'Primit',2:'Trimis',4:'În așteptare',5:'Eșuat',6:'În așteptare'}[m.type]||'SMS'),m.number);li.append(node('p',m.body));$('sms-list').append(li);});if(!messages.length)$('sms-list').append(node('li','Niciun mesaj disponibil.'));}
  $('contacts-search').oninput=renderContacts;$('sms-search').oninput=renderMessages;
  async function loadContacts(){if(!active('communication'))return;const v=version,data=await json('/api/phone/contacts');if(v!==version||!active('communication'))return;contacts=data.rows;renderContacts();}
  async function loadMessages(){if(!active('communication'))return;const v=version,data=await json('/api/phone/messages');if(v!==version||!active('communication'))return;messages=data.rows;renderMessages();await loadState();}
  function simOptions(rows){['call-sim','sms-sim'].forEach(id=>{const selected=$(id).value;$(id).replaceChildren();rows.forEach(sim=>{const option=node('option',sim.label);option.value=sim.slot;$(id).append(option);});if(rows.some(sim=>String(sim.slot)===selected))$(id).value=selected;});}
  async function loadState(){
    if(!active('communication'))return;const v=version,data=await json('/api/phone/state');if(v!==version||!active('communication'))return;simOptions(data.sims);$('sms-status').textContent=data.smsPending?'Trimitere în așteptare: verifică rezultatul înainte de repetare.':data.smsStatus;
    $('current-calls').replaceChildren();if(!data.defaultDialer)$('current-calls').append(node('p','KeyShell trebuie să fie aplicația Telefon implicită pentru controlul apelului curent.'));
    data.calls.forEach(call=>{const card=node('article');card.className='panel';card.append(node('h3',call.name||call.number||'Apel'),node('p',call.label));const controls=node('div');controls.className='remote-toolbar';
      let buttons=[['Închide','hangup',''],['Difuzor','speaker','true'],['Receptor','speaker','false'],['Microfon oprit','mute','true'],['Microfon pornit','mute','false']];if(call.state===2)buttons.unshift(['Răspunde','answer','']);if(call.state===4)buttons.push(['Pune în așteptare','hold','']);if(call.state===3)buttons.push(['Reia','resume','']);
      buttons.forEach(([label,action,value])=>{const b=node('button',label);b.onclick=()=>run(async()=>{await json('/api/phone/action',{id:call.id,action,value});await loadState();});controls.append(b);});
      const tones=node('div');tones.className='remote-toolbar';'123456789*0#'.split('').forEach(value=>{const b=node('button',value);b.onclick=()=>run(()=>json('/api/phone/action',{id:call.id,action:'dtmf',value}));tones.append(b);});card.append(controls,tones);$('current-calls').append(card);
    });if(!data.calls.length)$('current-calls').append(node('p','Niciun apel curent.'));
    clearTimeout(stateTimer);if(api.state().tab==='calls'&&!document.hidden)stateTimer=setTimeout(()=>loadState().catch(e=>api.notice(e.message,true)),3000);
  }
  async function loadCalls(){if(!active('communication'))return;await loadState();const v=version,data=await json('/api/phone/history');if(v!==version||!active('communication'))return;$('call-history').replaceChildren();data.rows.forEach(call=>$('call-history').append(row((call.name||call.number||'Număr privat')+' · '+new Date(call.date).toLocaleString('ro-RO')+' · '+call.duration+' s',call.number||'')));}
  async function prepare(kind){if(!active('communication'))return;const values={kind,number:$(kind==='sms'?'sms-number':'call-number').value.trim(),slot:Number($(kind==='sms'?'sms-sim':'call-sim').value),body:kind==='sms'?$('sms-body').value:''};if(!/^\+?[0-9]{3,15}$/.test(values.number))throw Error('Introdu un număr valid.');const data=await json('/api/phone/prepare',values);ticket=data.ticket;$('confirmation-title').textContent=kind==='sms'?'Trimiți acest SMS?':'Inițiezi acest apel?';$('confirmation-summary').textContent='Către '+data.number+' · SIM '+(data.slot+1)+(kind==='sms'?' · '+data.segments+' segmente SMS':'');$('confirmation-body').textContent=data.body;$('confirmation-send').disabled=false;dialog.showModal();$('confirmation-cancel').focus();}
  $('call-form').onsubmit=event=>{event.preventDefault();run(()=>prepare('call'));};$('sms-form').onsubmit=event=>{event.preventDefault();run(()=>prepare('sms'));};
  $('confirmation-cancel').onclick=()=>{ticket='';dialog.close();};dialog.addEventListener('cancel',()=>{ticket='';});
  $('confirmation-send').onclick=()=>run(async()=>{if(!ticket)return;const current=ticket;ticket='';$('confirmation-send').disabled=true;dialog.close();try{await json('/api/phone/confirm',{ticket:current});api.notice('Cerere transmisă telefonului. Verifică starea apelului sau SMS-ului.');await loadState();}catch(e){api.notice(e.message+' Cererea nu va fi repetată automat.',true);}});
  $('calls-refresh').onclick=()=>run(loadCalls);$('sms-refresh').onclick=()=>run(loadMessages);$('contacts-refresh').onclick=()=>run(loadContacts);
  async function enter(name){document.body.classList.toggle('control-page',api.state().live&&Object.hasOwnProperty.call(titles,name));clearTimeout(frameTimer);clearTimeout(stateTimer);if(name!=='remote'){version++;clearInputs();releaseFrame();}if(name==='remote'&&active('remote')){geometry=await json('/api/remote/info');await frame();}else if(name==='calls')await loadCalls();else if(name==='sms')await loadMessages();else if(name==='contacts')await loadContacts();}
  window.addEventListener('keyshell-session',access);window.addEventListener('keyshell-tab',event=>{enter(event.detail).catch(e=>api.notice(e.message,true));});
  document.addEventListener('visibilitychange',()=>{clearTimeout(frameTimer);clearTimeout(stateTimer);if(!document.hidden)enter(api.state().tab).catch(e=>api.notice(e.message,true));});
  window.addEventListener('pagehide',cleanup);access();api.showTab(api.state().tab);
})();
