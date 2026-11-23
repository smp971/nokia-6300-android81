/* Android adapter; upstream KaiSIP and JsSIP remain pinned and unmodified. */
(function () {
  'use strict';
  var native = window.NativeKaiSIP;
  // The KaiOS SD logger retains an unbounded run in memory. Android keeps no
  // key/account/SIP trace and never requests storage access for diagnostics.
  Log = { i: function () {}, w: function () {}, e: function () {}, flush: function () {},
    onLine: function () {}, fileName: function () { return 'dezactivat pe Android'; }, mode: function () { return 'disabled'; } };
  var token = 0;
  navigator.requestWakeLock = function (kind) {
    if (kind !== 'cpu') { throw new Error('Unsupported wake lock'); }
    var id = ++token, active = true;
    native.cpu(id, true);
    return { unlock: function () { if (active) { active = false; native.cpu(id, false); } } };
  };
  var apply = Background.apply;
  Background.apply = function () { apply(); native.background(Config.get().background === 'on'); };
  Background.bringToFront = function () { native.foreground(); };
  var speaker = false;
  Route = {
    supported: function () { return true; },
    headphones: function () { return native.headphones(); },
    isSpeaker: function () { return speaker; },
    setSpeaker: function (on) { speaker = !!on; native.route(true, speaker); return true; },
    toggle: function () { return this.setSpeaker(!speaker); },
    channel: function () { return 'content'; },
    begin: function () { this.setSpeaker(Config.get().audioOut === 'speaker'); },
    end: function () { speaker = false; native.route(false, false); },
    label: function () { return speaker ? (native.headphones() ? 'Căști' : 'Ureche') : 'Difuzor'; },
    onHeadphones: function () {}
  };
  var remote = null;
  Bridge = {
    start: function (stream, level) {
      this.stop();
      var AC = window.AudioContext || window.webkitAudioContext;
      var context = new AC(), source = context.createMediaStreamSource(stream), gain = context.createGain();
      gain.gain.value = level === undefined ? 1 : level;
      source.connect(gain); gain.connect(context.destination);
      remote = { context: context, source: source, gain: gain };
      if (context.resume) { context.resume(); }
    },
    stop: function () { if (remote) { var old = remote; remote = null; try { old.source.disconnect(); old.context.close(); } catch (e) {} } },
    active: function () { return !!remote; },
    setGain: function (value) { if (remote) { remote.gain.gain.value = Math.max(0.2, Math.min(3, value)); } },
    stats: function () { return null; }
  };
  function syncCall() { native.call(Call.inCall()); }
  Call.on('new', syncCall); Call.on('change', syncCall); Call.on('ended', syncCall); Call.on('cleared', syncCall);
  Sip.on('state', function (state) { native.state(state.state || 'unknown'); });
  function editor() { var e = document.activeElement; return e && /^(INPUT|TEXTAREA)$/.test(e.tagName); }
  var oldCenter = '';
  function syncEditor() {
    var e = document.activeElement, active = !!editor();
    native.editing(active);
    if (active) {
      e.readOnly = true;
      var label = e.parentNode && e.parentNode.querySelector('label, .label, .k');
      native.editor(JSON.stringify({ id: e.id, value: e.value, type: e.id === 'loginpass' ? 'password' : (e.type || 'text'),
        multiline: e.tagName === 'TEXTAREA', label: label ? label.textContent : 'Editează' }));
      var center = document.getElementById('sk-c');
      if (center && center.textContent !== 'Editează') { oldCenter = center.textContent; center.textContent = 'Editează'; }
    }
  }
  document.addEventListener('focusin', function () { setTimeout(syncEditor, 0); });
  document.addEventListener('focusout', function () { setTimeout(syncEditor, 0); });
  window.AndroidPlatform = {
    key: function (value, down, repeat) {
      var event = new KeyboardEvent(down ? 'keydown' : 'keyup', { key: value, bubbles: true, cancelable: true, repeat: repeat });
      document.dispatchEvent(event);
      if (down && value === 'Backspace' && !event.defaultPrevented && !editor()) { native.close(); }
    },
    erase: function () {
      var e = document.activeElement; if (!editor()) { return; }
      var a = e.selectionStart, b = e.selectionEnd;
      if (a === b && a > 0) { a--; }
      e.value = e.value.slice(0, a) + e.value.slice(b); e.setSelectionRange(a, a);
      e.dispatchEvent(new Event('input', { bubbles: true }));
    },
    commitEditor: function (id, value) {
      var e = document.getElementById(id); if (!e || !/^(INPUT|TEXTAREA)$/.test(e.tagName)) { return; }
      e.value = value; e.dispatchEvent(new Event('input', { bubbles: true })); e.dispatchEvent(new Event('change', { bubbles: true })); e.blur();
    },
    blurEditor: function () {
      var e = document.activeElement; if (editor()) { e.blur(); }
      native.editing(false);
      var center = document.getElementById('sk-c'); if (center && center.textContent === 'Editează') { center.textContent = oldCenter; }
    },
    focusLost: function () { var c = Call.current(); if (c && c.session && !c.ended) { try { c.session.mute({ audio: true }); } catch (e) {} } },
    stop: function () { try { Config.set('background', 'off'); Background.apply(); } catch (e) {} try { if (Call.inCall()) { Call.hangup(); } } catch (e2) {} try { Sip.stop(); } catch (e3) {} }
  };
}());
