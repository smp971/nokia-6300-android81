/* No browser, account, network or microphone. Verify the adapter preserves the
 * real API contracts and never forwards payloads to the diagnostic trace. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const traces = [], events = {}, sip = {}, calls = {};
let mediaResult = {}, mediaFailure, offerFailure, receiver;
let dialCount = 0, dialReceiver;
const media = { getUserMedia(options) { receiver = this; assert.equal(options.audio, true); return mediaFailure ? Promise.reject(mediaFailure) : Promise.resolve(mediaResult); } };
class Peer {
  constructor() { this.listeners = {}; this.iceGatheringState = 'gathering'; this.iceConnectionState = 'new'; }
  addEventListener(name, fn) { (this.listeners[name] ||= []).push(fn); }
  createOffer(options) { assert.equal(options.audio, true); if (offerFailure) throw offerFailure; return Promise.resolve({ type: 'offer', sdp: 'SDP_SECRET_MARKER' }); }
  createAnswer() { return Promise.resolve({ type: 'answer' }); }
  setLocalDescription(value) { assert.equal(value.sdp, 'SDP_SECRET_MARKER'); return Promise.resolve(); }
  setRemoteDescription() { return Promise.resolve(); }
  addTrack() {}
  getSenders() { return []; }
}
class Socket { send(value) { this.last = value; return 17; } }
const native = { trace(stage, value) { traces.push([stage, value]); }, editing() {}, editor() {}, background() {}, call() {}, state() {} };
const context = {
  navigator: { mediaDevices: media }, RTCPeerConnection: Peer, WebSocket: Socket,
  NativeKaiSIP: native, addEventListener(name, fn) { events[name] = fn; },
  Background: { apply() {} }, Config: { get() { return { background: 'off' }; } },
  Call: { on(name, fn) { calls[name] = fn; }, inCall() { return false; }, dial(number) { dialCount++; dialReceiver = this; assert.equal(number, 'test-extension'); return 'mock-result'; } },
  Sip: { on(name, fn) { sip[name] = fn; } },
  document: { activeElement: null, addEventListener() {}, getElementById() { return null; } },
  setTimeout, clearTimeout, Log: {}, Route: {}, Bridge: {}
};
context.window = context;
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../assets/android-platform.js'), 'utf8'), context);
(async () => {
  assert.equal(context.Call.dial('test-extension'), 'mock-result'); assert.equal(dialReceiver, context.Call);
  const addTrack = Peer.prototype.addTrack; delete Peer.prototype.addTrack;
  assert.match(context.Call.dial('test-extension'), /WebRTC/); assert.equal(dialCount, 1, 'missing API cannot begin a stuck call');
  Peer.prototype.addTrack = addTrack;
  assert.equal(await media.getUserMedia({ audio: true }), mediaResult);
  assert.equal(receiver, media, 'preserves mediaDevices receiver');
  mediaFailure = Object.assign(new Error('PASSWORD_SECRET_MARKER'), { name: 'NotAllowedError' });
  await assert.rejects(media.getUserMedia({ audio: true }), error => error === mediaFailure);
  const peer = new Peer(), offer = await peer.createOffer({ audio: true });
  assert.equal(offer.sdp, 'SDP_SECRET_MARKER'); await peer.setLocalDescription(offer);
  assert.equal(peer.listeners.icecandidate.length, 1, 'one listener per peer despite multiple wrapped APIs');
  peer.listeners.icecandidate[0]({ candidate: { candidate: 'ADDRESS_SECRET_MARKER' } });
  peer.listeners.icecandidate[0]({ candidate: null });
  offerFailure = Object.assign(new Error('SDP_SECRET_MARKER'), { name: 'OperationError' });
  assert.throws(() => peer.createOffer({ audio: true }), error => error === offerFailure);
  const socket = new Socket();
  assert.equal(socket.send('INVITE sip:DESTINATION_SECRET_MARKER SIP/2.0\r\nAuthorization: PASSWORD_SECRET_MARKER'), 17);
  assert.match(socket.last, /PASSWORD_SECRET_MARKER/, 'real send retains its original payload');
  const count = traces.length; socket.send('MESSAGE sip:DESTINATION_SECRET_MARKER SIP/2.0');
  assert.equal(traces.length, count, 'message contents are never traced');
  const ua = { on(name, fn) { this[name] = fn; } }; sip.ua(ua);
  const session = { on(name, fn) { this[name] = fn; } }; ua.newRTCSession({ originator: 'local', session });
  session.sdp({ originator: 'local', type: 'offer', sdp: 'SDP_SECRET_MARKER' });
  session.progress({ response: { status_code: 180, body: 'PASSWORD_SECRET_MARKER' } });
  session.accepted();
  const serialized = JSON.stringify(traces);
  assert(!serialized.includes('SECRET_MARKER'), 'trace excludes credentials, destination, SDP and addresses');
  for (const pair of traces) assert(pair.every(value => /^[A-Za-z0-9_-]{1,40}$/.test(value)), 'bounded enum/number fields only');
  assert(traces.some(row => row[0] === 'signal' && row[1] === 'INVITE'));
  assert(traces.some(row => row[0] === 'response' && row[1] === '180'));
  assert(traces.some(row => row[0] === 'media' && row[1] === 'NotAllowedError'));
  console.log('PASS: adapter preserves API results, failures, receivers and transport payloads; diagnostics expose enums only');
})().catch(error => { console.error(error); process.exitCode = 1; });
