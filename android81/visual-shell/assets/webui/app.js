/* Phone-backed WebUI. Private state and authentication exist only in this page memory. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const emptyModel = () => ({apps:[],folders:[],themes:[],settings:{},status:{},account:{},webui:{}});
  const iconNames = {0:'phone',1:'contacts',2:'messages',3:'clock',5:'gallery',6:'music',8:'settings',11:'calendar',12:'video',22:'email',25:'fm',34:'camera'};
  const glyphs = {4:'∑',9:'▦',10:'▤',13:'♩',14:'⇄',19:'◎',23:'>_',26:'☁',30:'▥',31:'✣',35:'⌖',37:'◇',38:'☷',39:'◉',40:'◈',41:'⌘',42:'▣',43:'▱',44:'▧',45:'▰',46:'△',47:'▦',48:'☎',49:'🛰'};
  let model = emptyModel(), live = false, token = '', tab = 'remote', generation = 0;
  const pending = new Set();

  function applyWebTheme(theme) {
    document.documentElement.dataset.webTheme = String([0,1,2].includes(theme) ? theme : 0);
  }
  function disabled(id) {
    if (!live) return true;
    const admin = model.account.role === 'admin', features = model.webui;
    if (id === 'upload') return !admin || !features.files;
    if (id === 'load-files') return !features.files;
    if (id === 'save-layout') return !admin || !features.layout;
    if (id === 'save-appearance') return !admin || !features.appearance;
    if (id === 'save-webui' || id === 'save-user') return !admin;
    return false;
  }

  function element(tag, cls, text) {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function notice(text, error = false) {
    $('message').textContent = text;
    $('message').classList.toggle('error', error);
  }
  function bytes(value) {
    const unit = value >= 1073741824 ? 1073741824 : 1048576;
    return (value / unit).toLocaleString('ro-RO', {maximumFractionDigits: unit === 1048576 ? 0 : 2}) + (unit === 1048576 ? ' MB' : ' GB');
  }
  async function request(path, extra = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), ['/api/login','/api/password','/api/admin/users'].includes(path) ? 60000 : 15000);
    try {
      const response = await fetch(path, {...extra,signal:controller.signal,headers:{Authorization:'Bearer '+token,...(extra.headers || {})},cache:'no-store'});
      if (!response.ok && !(path === '/api/remote/screen' && response.status === 304)) {
        if (response.status === 401) { disconnect(); throw Error('Autentificare necesară. Verifică utilizatorul și parola sau conectează-te din nou.'); }
        const messages = {400:'Datele nu sunt valide.',403:'Funcționalitatea este oprită sau contul nu are permisiunea necesară.',409:'Operația nu mai este disponibilă sau a fost deja folosită. Actualizează starea înainte de o nouă încercare.',413:'Numele sau dimensiunea fișierului nu sunt acceptate.',429:'Prea multe încercări. Așteaptă un minut și încearcă din nou.'};
        throw Error(messages[response.status] || 'Cererea nu a reușit ('+response.status+').');
      }
      return response;
    } catch (error) {
      if (error.name === 'AbortError') throw Error('Telefonul nu răspunde. Verifică sesiunea și conexiunea Wi-Fi.');
      throw error;
    } finally { clearTimeout(timeout); }
  }
  async function busy(id, action) {
    if (pending.has(id)) return;
    pending.add(id); $(id).disabled = true;
    try { await action(); } catch (error) { notice(error.message, true); }
    finally { pending.delete(id); $(id).disabled = disabled(id); }
  }
  async function reload(connect = false) {
    const ownGeneration = generation;
    const next = await (await request('/api/desktop')).json();
    if (!Array.isArray(next.apps) || !Array.isArray(next.folders) || !next.settings || !next.status || !Array.isArray(next.themes)) throw Error('Răspuns incompatibil cu această versiune.');
    if (ownGeneration !== generation) throw Error('Sesiunea s-a schimbat.');
    model = next; if (connect) live = true; render();
  }
  function disconnect() {
    generation++; live = false; token = ''; model = emptyModel();
    document.querySelectorAll('input[type=password]').forEach(input => { input.value = ''; });
    ['file-list','user-list'].forEach(id => $(id).replaceChildren()); render();
  }
  function showTab(name) {
    tab = name;
    document.querySelectorAll('.tab-panel').forEach(node => { node.hidden = node.id !== name; });
    document.querySelectorAll('[data-tab]').forEach(button => button.classList.toggle('active',button.dataset.tab === name));
    $('breadcrumb').textContent = {overview:'Prezentare',apps:'Aplicații și foldere',appearance:'Aspect',files:'Fișiere',security:'Administrare',remote:'Control telefon',calls:'Apeluri',sms:'Mesaje',contacts:'Contacte'}[name] || name;
    window.dispatchEvent(new CustomEvent('keyshell-tab',{detail:name}));
    if (name === 'files' && live && model.webui.files) busy('load-files', loadFiles);
    if (name === 'security' && live && model.account.role === 'admin') busy('save-webui',loadAdmin);
  }
  function visibleApps() { return model.apps.filter(app => !model.settings.simple || [0,1,2,3,6,8].includes(app.id)); }
  function entries(parent = -1) {
    const apps = visibleApps();
    if (model.settings.simple || !model.settings.foldersEnabled) return parent < 0 ? apps : [];
    if (parent >= 0) return apps.filter(app => app.folder === parent);
    const added = new Set(), out = [];
    apps.forEach(app => {
      if (app.folder === -1) out.push(app);
      else if (!added.has(app.folder)) {
        added.add(app.folder);
        const group = model.folders.find(f => f.id === app.folder);
        if (group) out.push({id:1000+group.id,name:group.name,group:group.id});
      }
    });
    return out;
  }
  function icon(app) {
    if (app.group !== undefined) {
      const node = element('span','folder-icon');
      model.apps.filter(a => a.folder === app.group).slice(0,4).forEach(a => node.append(icon(a)));
      return node;
    }
    const node = element('span','app-icon tone-'+(app.id % 5));
    if (iconNames[app.id]) {
      const image = document.createElement('img'); image.src = '../catalog-icons/gaia/'+iconNames[app.id]+'.png'; image.alt = ''; image.draggable = false; node.append(image);
    } else node.textContent = glyphs[app.id] || app.name.slice(0,1);
    return node;
  }
  function render() {
    document.querySelector('.connection').classList.toggle('is-connected',live);
    window.dispatchEvent(new Event('keyshell-session'));
    $('mode').textContent = live ? 'NOKIA · CONECTAT' : 'NECONECTAT';
    $('side-mode').textContent = live ? 'WebUI permanent' : 'Autentificare necesară';
    $('connection-title').textContent = live ? 'Conectat · '+model.account.username : 'Conectează-te la Nokia.';
    $('connection-note').textContent = live ? 'Controlezi telefonul prin conexiunea locală HTTPS.' : 'Deschide adresa HTTPS din aplicația WebUI a telefonului și introdu utilizatorul și parola.';
    $('login-password').hidden = live; $('username').hidden = live;
    $('connect-form').querySelector('label').hidden = live;
    $('connect-form').querySelector('[type=submit]').hidden = live; $('disconnect').hidden = !live;
    $('battery').textContent = !live || model.status.battery < 0 ? '—' : model.status.battery+'%';
    $('battery-detail').textContent = !live ? 'Conectare necesară' : model.status.charging ? 'La încărcat' : 'Pe baterie';
    $('storage').textContent = live ? bytes(model.status.free_bytes) : '—';
    $('memory').textContent = live ? bytes(model.status.ram_free) : '—';
    $('health').textContent = live ? model.status.health : 'Conectare necesară';
    $('app-count').textContent = live ? model.apps.length : '—';
    $('root-count').textContent = live ? entries(-1).length+' poziții în meniu' : 'Conectare necesară';
    $('folders-enabled').checked = live && model.settings.foldersEnabled;
    $('view-mode').value = live && model.settings.list ? 'list' : 'grid';
    $('folders-enabled').disabled = disabled('save-layout'); $('view-mode').disabled = disabled('save-appearance');
    ['upload','load-files','save-layout','save-appearance','change-password','save-webui','save-user','refresh'].forEach(id => { $(id).disabled = pending.has(id) || disabled(id); });
    $('upload-file').disabled = disabled('upload');
    $('files-note').textContent = live ? 'Doar folderul Companion este partajat. APK-urile se instalează separat pe telefon.' : 'Autentifică-te pentru transferuri.';
    $('security-private').hidden = !live; $('admin-controls').hidden = !live || model.account.role !== 'admin';
    $('security-note').textContent = !live ? 'Conectează-te la Nokia pentru parole, utilizatori și funcționalități.' : model.account.role === 'admin' ? 'Administrator · modificările sunt aplicate pe telefon.' : 'Acces doar pentru citire · îți poți schimba parola personală.';
    if (live) applyWebTheme(model.webui.theme);
    $('folder-cards').replaceChildren();
    model.folders.forEach(group => {
      const card = element('button','folder-card'); const count = model.apps.filter(a => a.folder === group.id).length;
      card.append(icon({group:group.id}),element('strong','',group.name),element('small','',count+' aplicații'));
      card.onclick = () => showTab('apps');
      $('folder-cards').append(card);
    });
    $('app-table').replaceChildren();
    model.apps.forEach(app => {
      const row = element('div','app-row'), select = document.createElement('select'); select.setAttribute('aria-label','Folder pentru '+app.name);
      [{id:-1,name:'Meniul principal'},...model.folders].forEach(group => { const option = document.createElement('option'); option.value = group.id; option.textContent = group.name; select.append(option); });
      select.value = app.folder; select.disabled = live && (model.account.role !== 'admin' || !model.webui.layout); select.onchange = () => { app.folder = Number(select.value); notice('Aranjament pregătit. Apasă Aplică pe Nokia.'); };
      row.append(icon(app),element('strong','',app.name),select); $('app-table').append(row);
    });
    $('themes').replaceChildren();
    model.themes.forEach(theme => {
      const card = element('button','theme-card'); card.style.setProperty('--theme-color',theme.color); card.setAttribute('aria-pressed',String(theme.id === model.settings.theme)); card.disabled = live && (model.account.role !== 'admin' || !model.webui.appearance); card.append(element('i'),element('span','',theme.name));
      card.onclick = () => { model.settings.theme = theme.id; render(); notice('Temă aleasă. Apasă Aplică pe Nokia.'); };
      $('themes').append(card);
    });
  }
  async function loadFiles() {
    const files = await (await request('/api/files')).json(); $('file-list').replaceChildren();
    files.forEach(file => {
      const row = element('li'), download = element('button','','Descarcă'); row.append(element('span','',file.name+' · '+bytes(file.bytes)),download);
      download.onclick = () => busy('load-files',async () => {
        const blob = await (await request('/api/file/'+encodeURIComponent(file.name))).blob(); const url = URL.createObjectURL(blob), link = document.createElement('a'); link.href = url; link.download = file.name; link.click(); setTimeout(() => URL.revokeObjectURL(url),1000);
      }); $('file-list').append(row);
    }); if (!files.length) $('file-list').append(element('li','','Folderul Companion este gol.'));
  }
  function userForm(action = 'add', user = null) {
    $('user-form').dataset.action = action;
    $('user-name').value = user ? user.username : '';
    $('user-name').readOnly = action !== 'add';
    $('user-password').value = '';
    $('user-role').hidden = $('user-role-label').hidden = action !== 'add';
    $('user-form-title').textContent = action === 'add' ? 'Adaugă utilizator' : 'Parolă nouă · '+user.username;
    $('save-user').textContent = action === 'add' ? 'Adaugă utilizator' : 'Schimbă parola';
  }
  async function loadAdmin() {
    const data = await (await request('/api/admin')).json();
    $('web-theme').value = data.settings.theme;
    $('auto-start').checked = data.settings.autoStart;
    ['appearance','layout','files','remote','communication'].forEach(key => { $('feature-'+key).checked = data.settings[key]; });
    $('user-list').replaceChildren();
    data.users.forEach(user => {
      const row = element('li'), actions = element('span','user-actions');
      row.append(element('span','',user.username+' · '+(user.role === 'admin' ? 'Administrator' : 'Doar citire')));
      const password = element('button','','Parolă'); password.type = 'button'; password.onclick = () => userForm('password',user);
      const remove = element('button','','Șterge'); remove.type = 'button'; remove.onclick = () => {
        if (!window.confirm('Ștergi utilizatorul '+user.username+'?')) return;
        busy('save-user',async () => {
          await request('/api/admin/users',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({action:'delete',username:user.username})});
          await loadAdmin(); notice('Utilizator șters. Cel puțin un administrator este păstrat.');
        });
      };
      actions.append(password,remove); row.append(actions); $('user-list').append(row);
    });
  }
  document.querySelectorAll('[data-tab]').forEach(button => { button.onclick = () => showTab(button.dataset.tab); });
  document.querySelector('.brand').onclick = () => showTab('overview');
  $('connect-form').onsubmit = event => { event.preventDefault(); busy('refresh',async () => {
    if (location.protocol !== 'https:') throw Error('Deschide adresa HTTPS afișată pe Nokia.');
    const username = $('username').value, password = $('login-password').value;
    if (!username || !password) throw Error('Introdu utilizatorul și parola configurate pe Nokia.');
    generation++; notice('Se verifică parola…');
    const login = await (await request('/api/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password})})).json();
    token = login.token; $('login-password').value = '';

    try { await reload(true); $('login-password').value = ''; showTab(tab); notice('Conectat prin HTTPS. Datele afișate acum vin de pe Nokia.'); }
    catch (error) { token = ''; throw error; }
  }); };
  $('disconnect').onclick = () => { request('/api/logout',{method:'POST'}).catch(() => {}); disconnect(); notice('Deconectat. Datele telefonului au fost șterse din pagină.'); };
  $('password-form').onsubmit = event => { event.preventDefault(); busy('change-password',async () => {
    if (!live) return;
    const currentPassword = $('current-password').value, newPassword = $('new-password').value;
    if (newPassword.length < 8 || newPassword !== $('confirm-password').value) throw Error('Verifică parola nouă și confirmarea ei.');
    notice('Se salvează parola…');
    try {
      const response = await (await request('/api/password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({currentPassword,newPassword})})).json();
      token = response.token; notice('Parola a fost schimbată. Celelalte sesiuni ale acestui utilizator au fost închise.');
    } finally { ['current-password','new-password','confirm-password'].forEach(id => { $(id).value = ''; }); }
  }); };
  $('web-settings-form').onsubmit = event => { event.preventDefault(); busy('save-webui',async () => {
    const values = {theme:Number($('web-theme').value),autoStart:$('auto-start').checked,appearance:$('feature-appearance').checked,layout:$('feature-layout').checked,files:$('feature-files').checked,remote:$('feature-remote').checked,communication:$('feature-communication').checked};
    await request('/api/admin/settings',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});
    await reload(); notice('Setările WebUI au fost aplicate pe Nokia.');
  }); };
  $('new-user').onclick = () => userForm();
  $('user-form').onsubmit = event => { event.preventDefault(); busy('save-user',async () => {
    const values = {action:$('user-form').dataset.action,username:$('user-name').value,password:$('user-password').value};
    if (values.action === 'add') values.role = $('user-role').value;
    try { await request('/api/admin/users',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(values)});userForm();await loadAdmin();notice('Utilizator salvat.'); }
    finally { $('user-password').value = ''; }
  }); };
  $('refresh').onclick = () => busy('refresh',async () => { if (live) { await reload(); notice('Stare actualizată de pe telefon.'); } else notice('Autentifică-te pentru datele telefonului.'); });
  $('folders-enabled').onchange = () => { model.settings.foldersEnabled = $('folders-enabled').checked; };
  $('view-mode').onchange = () => { model.settings.list = $('view-mode').value === 'list'; };
  $('save-layout').onclick = () => busy('save-layout',async () => {
    if (!live) return;
    await request('/api/layout',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({enabled:model.settings.foldersEnabled,moves:model.apps.map(app => ({id:app.id,folder:app.folder}))})}); await reload(); notice('Aranjamentul a fost aplicat pe Nokia.');
  });
  $('save-appearance').onclick = () => busy('save-appearance',async () => {
    if (!live) return;
    await request('/api/appearance',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({theme:model.settings.theme,list:model.settings.list})}); await reload(); notice('Aspectul a fost aplicat pe Nokia.');
  });

  $('load-files').onclick = () => busy('load-files',loadFiles);
  $('upload').onclick = () => busy('upload',async () => {
    if (!live) return; const file = $('upload-file').files[0]; if (!file) throw Error('Alege un fișier.');
    if (file.size > 16777216 || !/^[A-Za-z0-9][A-Za-z0-9._ -]{0,79}$/.test(file.name) || file.name.includes('..')) throw Error('Folosește un nume simplu, fără diacritice, și maximum 16 MB.');
    await request('/api/upload',{method:'POST',headers:{'X-Leo-Name':file.name},body:file}); $('upload-file').value = ''; await loadFiles(); notice('Fișier trimis în folderul Companion de pe Nokia.');
  });
  window.KeyShellWeb = Object.freeze({request,notice,showTab,state:()=>({live,model,tab})});
  try { localStorage.removeItem('keyshell-demo-v1'); } catch (_) {}
  render(); showTab(tab);
  if (location.protocol === 'https:') fetch('/api/info',{cache:'no-store'}).then(r => r.ok ? r.json() : null).then(info => { if (info && !live) applyWebTheme(info.theme); }).catch(() => {});
})();
