# Dual-slot IMS investigation — 29 October 2026

**A two-transport client was compiled and observed on the phone.** It read
distinct results for each physical slot without publishing a new IMS Binder or
sending IMS configuration/registration requests. The selected-slot client was
then restored. This is not a successful simultaneous dual-VoLTE test.

The investigation also found and fixed a stale voice-capability cache in the
installed selected-slot client. SIM 2 now passes the live IMS/LTE verifier on the
fresh Leo installation, without a new call. The selected-slot fix is installed
in both the system base and data update. The dual-slot candidate remains an
experimental build and is not installed for everyday use.

## Results on the current SIMs

| Observation | Physical SIM 1 · Digi | Physical SIM 2 · Orange |
| --- | --- | --- |
| Android radio registration | GSM voice; no packet registration | LTE voice and data |
| Modem allowed RATs, client-bound NAS query | GSM/UMTS/LTE (`0x1c`) | GSM/UMTS/LTE (`0x1c`) |
| Dual client registration response | `2` · not registered | `1` · registered |
| Dual client voice capability | unavailable | LTE (`rat=14`), enabled (`status=2`), restriction 0 |
| Corrected selected-slot client | not selected for this verification | registered, voice available, LTE Internet validated |

NAS reported dual standby with tune-away, both active-subscription bits set
(`0x3`) and the second subscription selected for data. The query bound only its
new diagnostic QMI client; it did not change global selection, NV or preferred
network types. Reserved modem fields are not interpreted as feature switches.

The user reports both SIMs had LTE/VoLTE in KaiOS. That remains a reference for
further comparison. These results neither prove a hardware limitation nor prove
simultaneous dual LTE support in the current port. More APN records or two LTE
icons would not establish a second real registration.

## Fixed: registration without refreshed voice capabilities

Before the fix, the installed client reported `registration=1` but
`voice_available=false`. The isolated query of the same modem socket returned
enabled LTE voice. Restoring the old client reproduced the false cached value.

The initial service-status query could run before its socket connected. The
client did not reliably query capabilities again after registration or normal
reconfiguration. `port-ims-client.py` now requests actual service status when
registration is reported, a listener attaches, and IMS configuration is applied.
A delayed startup read covers the initial connection race. No capability is
invented or forced true: the existing modem-response decoder remains authoritative.

After installation and a controller/package-state restart:

```text
sys.leo.ims.state=registered
sys.leo.ims.registration=1
sys.leo.ims.voice=1
LEO_IMS slot=1 phone=1 registration=1 voice_available=true
PASS: live selected-SIM IMS voice registration and validated LTE data.
```

Installed selected-slot APK SHA-256:
`338bc91f21f1f640a8fcca9441d661a31910cb90e9e22cb90b5ee19298c0639d`.
Its previous APK is backed up locally. No new real call or cold reboot was
performed. Earlier supervised call results remain separate evidence.

## Experimental dual-slot architecture

`patch-ims-dual-client.py` extends a fresh, pinned legacy compatibility port:

- Immutable physical-slot routing, captured before socket receiver threads start.
- A separate `ImsSenderRxr`, configuration object and registration/voice state
  for each endpoint; subscription IDs do not index physical-slot arrays.
- UT interfaces keyed by transport identity instead of one global singleton.
- Globally unique synchronized service IDs; reconnecting one socket no longer
  resets the serial counter used by the other socket.
- Binder routing for open, configuration, listeners and IMS enable/disable.
- Separate phone-targeted service broadcasts and per-slot diagnostic properties.

The ordinary selected-slot build remains the default. The dual candidate uses
the volatile `sys.leo.ims.dual` flag, fixed for a client process lifetime.
There is no automatic dual activation: its controller enters a manual trial
state, with call-aware shutdown handling. Android's `IImsMultiEndpoint`, which
concerns external call continuity, is still unsupported; it is not dual SIM.
Supplementary-service lifecycle and simultaneous call behavior are unverified.

## Build and guarded observation

Use the existing pinned local vendor reference and AOSP/Java 8 environment:

```sh
bash android81/build-ims-candidate.sh
bash android81/build-ims-candidate.sh --dual-slot
bash android81/test-ims-recovery.sh
```

Outputs are `output/ims-reference/ported-ims.apk` and `ported-ims-dual.apk`.
The build directories and APK names are separate. Neither command installs or
enables a service. The proprietary reference is still excluded from Git.

On the development Windows/ADB setup:

```powershell
python android81/trial-ims-dual.py --run
python android81/trial-ims-dual.py --install-refresh
python android81/verify-ims-runtime.py
```

`--run` temporarily disables package rebinding, pauses the existing Java client
and its watchdog, and launches the dual candidate in read-only mode. In that
mode it publishes neither the global IMS Binder nor service-UP broadcasts;
`onBind` returns null and startup performs queries rather than configuration.
The native daemons remain running. The script always restores the original APK,
package enabled state, volatile flags, socket modes and watchdog. `--install-refresh`
instead retains the selected-slot fix only after real voice-capability checks.
System-base synchronization was performed separately after that validation.

Both modes check idle calls and the Nokia serial, verify APK hashes and stage a
90-second independent on-phone rollback before changing the client. Rollback
defers while calls exist. Reports and APK backups stay in ignored local output.
Early attempts stopped before candidate observation because Android rebound the
old service; the final guarded observation succeeded after accounting for that
binding and Oreo's nonzero `stopservice` status. Restoration was verified.

Both APK variants compiled. Existing recovery policy checks (11) and topology/
readiness policy checks (50) passed; these are not modem or call coverage.
The observed dual APK was
`538de06ebcfd35cec2939809e4d3a72f5406270eecf03e25bc0eedf0934462c0`.
A subsequent dual rebuild also includes the shared capability-refresh fix;
that rebuild (`97c66b8f282050fd0625c6b165a1bdd15a38b5233366d742913e40ecc4182bdb`)
compiled successfully but was not installed for another dual observation.

## Remaining work

### Read-only modem allocation follow-up, 29 October

The NAS probe now also reads NAS GET_SYS_INFO (`0x4d`) and DMS GET_DEVICE_CAP
(`0x20`). These queries distinguish allowed network preferences from current
modem stack capabilities; they do not use Android's cached RAF fallback.

Live observations, with SIM 2 still selected for data:

| Modem report | First stack / SIM 1 observation | Second stack / SIM 2 observation |
| --- | --- | --- |
| Current DMS capability mask | `0x18`: GSM + WCDMA | `0x38`: GSM + WCDMA + LTE |
| Client-bound NAS actual service | GSM, circuit-switched only | LTE, circuit- and packet-switched domains |
| NAS system forbidden | false for the serving GSM system | false for the serving LTE system |

DMS advertises two multi-SIM configurations: `[0x38, 0x18]` and
`[0x18, 0x38]`. Neither advertised configuration gives both stacks LTE.
Current multi-SIM capability is two subscriptions, maximum active count one;
maximum active data subscriptions is one. These counts must not be confused
with the number of subscriptions that can remain registered in standby.

This establishes an allocation restriction in the currently running modem
configuration, despite both NAS allowed-mode masks including LTE. It does not
establish a permanent hardware limit or disprove the user's KaiOS observation.
A live KaiOS snapshot of these same DMS/NAS fields is still required to locate
the difference in firmware configuration, stack allocation or startup handling.
No DDS switch, radio restart, APN/NV write, call or reboot was performed here.
The original KaiOS init files inspected do not themselves establish dual LTE.

The DMS wire schema was inspected from `qmi/services/` in the same pinned
reference repository/revision used by the IMS reference fetcher. The ignored
reference header SHA-256 is
`31ba10ff8778820ddb1ea1781816333a519f05e64f525ff0e353d10b133e3727`;
the reference C schema SHA-256 is
`a8fd0969e2e24197e0b55988f3020a0e31fc8ba539737119603b270051dbe5d9`.
Only whitelisted status/capability fields are printed, excluding cell and
subscriber identities. The diagnostic binary was built and run on the Nokia;
the original IMS/LTE verifier passed afterward.

### Next steps

1. Compare live per-subscription NAS/RIL allocation against a KaiOS baseline to
   explain why Digi remains GSM despite the allowed LTE mask.
2. Validate a second real LTE/IMS registration before enabling dual IMS normally.
3. Replace manual trial activation with per-slot lifecycle/recovery and test SIM
   removal, data-SIM changes, airplane mode and incoming/outgoing calls per SIM.

The published ROM ZIP predates this change and has not been silently replaced.
