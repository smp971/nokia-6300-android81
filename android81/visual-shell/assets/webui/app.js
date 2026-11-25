/* The demo owns local data only. A live session uses same-origin HTTPS and an in-memory token. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const clone = value => JSON.parse(JSON.stringify(value));
  const seed = window.KEYSHELL_DEMO;
  const iconNames = {0:'phone',1:'contacts',2:'messages',3:'clock',5:'gallery',6:'music',8:'settings',11:'calendar',12:'video',22:'email',25:'fm',34:'camera'};
  const glyphs = {4:'∑',9:'▦',10:'▤',13:'♩',14:'⇄',19:'◎',23:'>_',26:'☁',30:'▥',31:'✣',35:'⌖',37:'◇',38:'☷',39:'◉',40:'◈',41:'⌘',42:'▣',43:'▱',44:'▧',45:'▰',46:'△',47:'▦',48:'☎'};
  let model = clone(seed), live = false, token = '', tab = 'overview';
  let folder = -1, selected = 0, grid = false, appPage = null;
  const lastChild = new Map();
  const pending = new Set();

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
  function loadDemo() {
    model = clone(seed);
    try {
      const saved = JSON.parse(localStorage.getItem('keyshell-demo-v1'));
      if (saved && Number.isInteger(saved.theme) && seed.themes.some(t => t.id === saved.theme)) {
        model.settings.theme = saved.theme;
        model.settings.list = saved.list === true;
        model.settings.foldersEnabled = saved.foldersEnabled !== false;
        model.apps.forEach(app => {
          const parent = saved.folders && saved.folders[app.id];
          if (Number.isInteger(parent) && parent >= -1 && parent < 5) app.folder = parent;
        });
      }
    } catch (_) { /* Unavailable/invalid local storage falls back to defaults. */ }
  }
  function saveDemo() {
    const folders = {};
    model.apps.forEach(app => { folders[app.id] = app.folder; });
    try {
      localStorage.setItem('keyshell-demo-v1', JSON.stringify({theme:model.settings.theme,list:model.settings.list,foldersEnabled:model.settings.foldersEnabled,folders}));
    } catch (_) { notice('Demonstrația funcționează, dar browserul nu permite salvarea locală.', true); return; }
    notice('Salvat în demonstrația din acest browser. Telefonul nu a fost modificat.');
  }
  async function request(path, extra = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(path, {...extra,signal:controller.signal,headers:{Authorization:'Bearer '+token,...(extra.headers || {})},cache:'no-store'});
      if (!response.ok) {
        if (response.status === 401) { disconnect(); throw Error('Token greșit sau sesiune expirată. Pornește o sesiune nouă pe Nokia.'); }
        const messages = {400:'Datele nu sunt valide.',403:'Conectarea este permisă numai din adresa afișată pe Nokia.',409:'Un fișier cu acest nume există deja. Alege alt nume.',413:'Numele sau dimensiunea fișierului nu sunt acceptate.'};
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
    finally { pending.delete(id); $(id).disabled = (id === 'upload' || id === 'load-files') && !live; }
  }
  async function reload() {
    const next = await (await request('/api/desktop')).json();
    if (!Array.isArray(next.apps) || !Array.isArray(next.folders) || !next.settings || !next.status || !Array.isArray(next.themes)) throw Error('Răspuns incompatibil cu această versiune.');
    model = next; folder = -1; selected = 0; appPage = null; render();
  }
  function disconnect() {
    live = false; token = ''; $('token').value = ''; grid = false; folder = -1; selected = 0; appPage = null;
    loadDemo(); render(); $('file-list').replaceChildren();
  }
  function showTab(name) {
    tab = name;
    document.querySelectorAll('.tab-panel').forEach(node => { node.hidden = node.id !== name; });
    document.querySelectorAll('[data-tab]').forEach(button => button.classList.toggle('active',button.dataset.tab === name));
    $('breadcrumb').textContent = {overview:'Prezentare',apps:'Aplicații și foldere',appearance:'Aspect',files:'Fișiere'}[name];
    if (name === 'files' && live) busy('load-files', loadFiles);
  }
  function visibleApps() { return model.apps.filter(app => !model.settings.simple || [0,1,2,3,6,8].includes(app.id)); }
  function entries(parent = folder) {
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
  function renderPhone() {
    const body = $('phone-body'); body.replaceChildren();
    const theme = model.themes.find(t => t.id === model.settings.theme) || seed.themes[17];
    $('phone-screen').style.setProperty('--accent', theme.color);
    $('phone-screen').style.setProperty('--phone-bg',theme.background || '#141218');
    $('soft-left').textContent = grid ? 'Opțiuni' : 'Notificări';
    $('soft-center').textContent = grid ? 'Deschide' : 'Aplicații';
    if (appPage) {
      const page = element('div','demo-app'); page.append(icon(appPage),element('h3','',appPage.name),element('p','','Previzualizare a navigării. Aplicația reală rulează pe Nokia.'),element('p','','Escape sau Înapoi pentru revenire.')); body.append(page); return;
    }
    if (!grid) {
      const home = element('div','phone-home');
      const network = element('div','phone-network'); network.append(element('span','','KeyShell Demo'),element('span','','VoLTE 4G'));
      home.append(network,element('div','phone-clock','12:45'),element('div','phone-date','miercuri, 25 noiembrie'),element('div','phone-weather','☁  7°C · București · demonstrație'));
      const shortcuts = element('div','home-shortcuts'); [0,2,6,22].forEach(id => { const app = model.apps.find(a => a.id === id); if (app) shortcuts.append(icon(app)); }); home.append(shortcuts); body.append(home); return;
    }
    let items = entries();
    if (!items.length && folder >= 0) { folder = -1; selected = 0; items = entries(); }
    selected = Math.max(0,Math.min(selected,items.length-1));
    const current = items[selected];
    body.append(element('div','phone-title',folder < 0 ? 'Aplicații' : model.folders.find(f => f.id === folder).name),element('span','phone-count',(selected+1)+' / '+items.length));
    const count = current && current.group !== undefined ? ' · '+model.apps.filter(a => a.folder === current.group).length : '';
    body.append(element('div','phone-subtitle',current ? current.name+count : 'Nicio aplicație'));
    const list = element('div',model.settings.list ? 'phone-list' : 'phone-grid');
    const start = model.settings.list ? Math.max(0,Math.min(items.length-6,selected-3)) : Math.floor(selected/9)*9;
    items.slice(start,start+(model.settings.list ? 6 : 9)).forEach((app,i) => {
      const button = element('button','phone-cell'+(start+i === selected ? ' selected' : ''));
      button.setAttribute('aria-label',app.name); button.append(icon(app),element('span','',app.id === 9 ? 'Android' : app.name));
      button.onclick = () => { selected = start+i; key('ok'); };
      list.append(button);
    }); body.append(list);
  }
  function key(action) {
    if (action === 'options') { showTab('apps'); return; }
    if (action === 'back') {
      if (appPage) appPage = null;
      else if (folder >= 0) {
        const parent = folder; const current = entries()[selected]; if (current) lastChild.set(parent,current.id);
        folder = -1; selected = Math.max(0,entries().findIndex(e => e.group === parent));
      } else grid = false;
    } else if (action === 'ok') {
      if (appPage) return;
      if (!grid) grid = true;
      else {
        const entry = entries()[selected]; if (!entry) return;
        if (entry.group !== undefined) { folder = entry.group; selected = Math.max(0,entries().findIndex(e => e.id === lastChild.get(folder))); }
        else appPage = entry;
      }
    } else if (grid && !appPage) {
      const length = entries().length; if (!length) return;
      if (action === 'next' || action === 'prev') selected = ((Math.floor(selected/9)+(action === 'next'?1:-1)+Math.ceil(length/9))%Math.ceil(length/9))*9;
      else if (/^[1-9]$/.test(action)) selected = Math.min(length-1,Math.floor(selected/9)*9+Number(action)-1);
      else {
        const delta = {up:model.settings.list?-1:-3,down:model.settings.list?1:3,left:-1,right:1}[action] || 0;
        selected = (selected === 0 && delta < 0) ? length-1 : (selected === length-1 && delta > 0) ? 0 : Math.max(0,Math.min(length-1,selected+delta));
      }
    }
    renderPhone(); $('phone-screen').focus({preventScroll:true});
  }
  function render() {
    $('mode').textContent = live ? 'NOKIA · CONECTAT' : 'DEMO · LOCAL'; $('side-mode').textContent = live ? 'Sesiune pe telefon' : 'Mod demonstrativ';
    $('connection-title').textContent = live ? 'Nokia este conectat.' : 'Încearcă-l înainte să te conectezi.';
    $('connection-note').textContent = live ? 'Starea și setările vin de pe telefon. Butoanele de salvare aplică modificările pe Nokia; previzualizarea rămâne o simulare a navigării.' : 'Acum folosești o demonstrație locală. Pentru Nokia, deschide adresa HTTPS din Setări → Rețele → Mai multe → KeyShell WebUI.';
    $('token').hidden = live; $('connect-form').querySelector('label').hidden = live; $('connect-form').querySelector('[type=submit]').hidden = live; $('disconnect').hidden = !live;
    $('reset-demo').hidden = live;
    $('battery').textContent = model.status.battery < 0 ? '—' : model.status.battery+'%'; $('battery-detail').textContent = !live ? 'Date demonstrative' : model.status.charging ? 'La încărcat' : 'Pe baterie';
    $('storage').textContent = bytes(model.status.free_bytes); $('memory').textContent = bytes(model.status.ram_free); $('health').textContent = model.status.health;
    $('app-count').textContent = model.apps.length; $('root-count').textContent = entries(-1).length+' poziții în meniu';
    $('folders-enabled').checked = model.settings.foldersEnabled; $('view-mode').value = model.settings.list ? 'list' : 'grid';
    $('save-layout').textContent = live ? 'Aplică pe Nokia' : 'Salvează aranjamentul'; $('save-appearance').textContent = live ? 'Aplică pe Nokia' : 'Salvează aspectul';
    $('upload').disabled = !live; $('load-files').disabled = !live; $('upload-file').disabled = !live; $('files-note').textContent = live ? 'Doar folderul Companion este partajat. APK-urile primite se instalează separat, de pe telefon.' : 'Conectează telefonul pentru transferuri reale.';
    $('folder-cards').replaceChildren();
    model.folders.forEach(group => {
      const card = element('button','folder-card'); const count = model.apps.filter(a => a.folder === group.id).length;
      card.append(icon({group:group.id}),element('strong','',group.name),element('small','',count+' aplicații'));
      card.onclick = () => { if (model.settings.simple || !model.settings.foldersEnabled || !count) { showTab('apps'); return; } grid = true; folder = group.id; selected = 0; appPage = null; renderPhone(); $('phone-screen').focus({preventScroll:true}); };
      $('folder-cards').append(card);
    });
    $('app-table').replaceChildren();
    model.apps.forEach(app => {
      const row = element('div','app-row'), select = document.createElement('select'); select.setAttribute('aria-label','Folder pentru '+app.name);
      [{id:-1,name:'Meniul principal'},...model.folders].forEach(group => { const option = document.createElement('option'); option.value = group.id; option.textContent = group.name; select.append(option); });
      select.value = app.folder; select.onchange = () => { app.folder = Number(select.value); renderPhone(); notice('Aranjament modificat în previzualizare. Apasă Salvează pentru a-l păstra.'); };
      row.append(icon(app),element('strong','',app.name),select); $('app-table').append(row);
    });
    $('themes').replaceChildren();
    model.themes.forEach(theme => {
      const card = element('button','theme-card'); card.style.setProperty('--theme-color',theme.color); card.setAttribute('aria-pressed',String(theme.id === model.settings.theme)); card.append(element('i'),element('span','',theme.name));
      card.onclick = () => { model.settings.theme = theme.id; render(); notice('Tema este în previzualizare. Apasă Aplică pentru a o păstra.'); };
      $('themes').append(card);
    }); renderPhone();
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
  document.querySelectorAll('[data-tab]').forEach(button => { button.onclick = () => showTab(button.dataset.tab); });
  document.querySelector('.brand').onclick = () => showTab('overview');
  document.querySelectorAll('[data-key]').forEach(button => { button.onclick = () => key(button.dataset.key); });
  $('phone-screen').addEventListener('keydown', event => {
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    const action = {ArrowUp:'up',ArrowDown:'down',ArrowLeft:'left',ArrowRight:'right',Enter:'ok',Escape:'back',Backspace:'back','*':'prev','#':'next','0':'next'}[event.key] || (/^[1-9]$/.test(event.key) ? event.key : null);
    if (action) { event.preventDefault(); event.stopPropagation(); key(action); }
  });
  $('phone-screen').addEventListener('click', event => { if (event.target === $('phone-screen')) $('phone-screen').focus(); });
  $('connect-form').onsubmit = event => { event.preventDefault(); busy('refresh',async () => {
    if (location.protocol !== 'https:') throw Error('Pentru conectare reală, deschide adresa HTTPS afișată pe Nokia. Aici poți folosi demonstrația.');
    token = $('token').value.trim(); if (!/^[a-f0-9]{32}$/.test(token)) throw Error('Introdu tokenul de 32 de caractere afișat pe Nokia.');
    try { await reload(); live = true; $('token').value = ''; render(); notice('Conectat prin HTTPS. Datele afișate acum vin de pe Nokia.'); }
    catch (error) { token = ''; throw error; }
  }); };
  $('disconnect').onclick = () => { disconnect(); notice('Deconectat. Demonstrația este din nou locală.'); };
  $('refresh').onclick = () => busy('refresh',async () => { if (live) { await reload(); notice('Stare actualizată de pe telefon.'); } else notice('Mod demonstrativ. Datele telefonului apar după conectare.'); });
  $('folders-enabled').onchange = () => { model.settings.foldersEnabled = $('folders-enabled').checked; folder = -1; selected = 0; renderPhone(); };
  $('view-mode').onchange = () => { model.settings.list = $('view-mode').value === 'list'; renderPhone(); };
  $('save-layout').onclick = () => busy('save-layout',async () => {
    if (!live) { saveDemo(); render(); return; }
    await request('/api/layout',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({enabled:model.settings.foldersEnabled,moves:model.apps.map(app => ({id:app.id,folder:app.folder}))})}); await reload(); notice('Aranjamentul a fost aplicat pe Nokia.');
  });
  $('save-appearance').onclick = () => busy('save-appearance',async () => {
    if (!live) { saveDemo(); return; }
    await request('/api/appearance',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({theme:model.settings.theme,list:model.settings.list})}); await reload(); notice('Aspectul a fost aplicat pe Nokia.');
  });
  $('reset-demo').onclick = () => { try { localStorage.removeItem('keyshell-demo-v1'); } catch (_) {} loadDemo(); grid = false; folder = -1; selected = 0; appPage = null; lastChild.clear(); render(); notice('Demonstrația a revenit la setările inițiale.'); };
  $('load-files').onclick = () => busy('load-files',loadFiles);
  $('upload').onclick = () => busy('upload',async () => {
    if (!live) return; const file = $('upload-file').files[0]; if (!file) throw Error('Alege un fișier.');
    if (file.size > 16777216 || !/^[A-Za-z0-9][A-Za-z0-9._ -]{0,79}$/.test(file.name) || file.name.includes('..')) throw Error('Folosește un nume simplu, fără diacritice, și maximum 16 MB.');
    await request('/api/upload',{method:'POST',headers:{'X-Leo-Name':file.name},body:file}); $('upload-file').value = ''; await loadFiles(); notice('Fișier trimis în folderul Companion de pe Nokia.');
  });
  loadDemo(); render(); showTab(tab);
})();
