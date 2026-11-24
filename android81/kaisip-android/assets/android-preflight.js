/* Enum-only capability probe before the compatibility shim; native logging is
 * disabled unless its bounded diagnostic deadline was explicitly enabled. */
(function () {
  var pc = window.RTCPeerConnection || window.webkitRTCPeerConnection;
  if (window.NativeKaiSIP && NativeKaiSIP.trace && pc) {
    NativeKaiSIP.trace('script', 'beforeAddTrack-' + typeof pc.prototype.addTrack);
    NativeKaiSIP.trace('script', 'beforeGetSenders-' + typeof pc.prototype.getSenders);
  }
}());
