# Romanian and European APN catalogue

Installed on the development Nokia on 28 October 2026. The system XML contains
**844 public carrier profiles**, including **18 Romanian profiles**, covering
all EU27 countries plus Iceland, Liechtenstein, Norway, Switzerland, the UK,
Monaco and Andorra. San Marino is included in the generator scope but has no
separate profile in this upstream snapshot.

Romanian entries include Vodafone, Digi, Orange, Telekom MCC/MNC combinations
and Lycamobile. Internet, MMS and IMS profiles remain separate. MVNO SPN/GID/IMSI
matching is retained; a profile sharing an MCC/MNC is not automatically suitable
for every virtual operator or subscription.

## Source and compatibility

- [AOSP device/sample, pinned commit 73f904fb](https://android.googlesource.com/device/sample/+/73f904fbeb872f5675cb5f18cd9dfb15856425a6/etc/apns-full-conf.xml)
- Upstream SHA-256: `9e44015793ec01d0773123103dca19b181fbc5fdd43cfc1b1d5cfacbda622726`.
- Source notice and Apache 2.0 licensing are retained in the XML.
- `device/nokia/leo/apns-conf.source.json` records counts and the generated hash.
- `device-common.mk` copies the catalogue to `/system/etc/apns-conf.xml` on build.

`build-apn-catalog.py` verifies the pinned source hash and emits XML version 8,
matching this Oreo TelephonyProvider. Modern carrier IDs and attributes which
Oreo cannot interpret are removed. Modern network-type constraints are mapped to
Oreo RIL bearer bitmasks; unsupported radio-only entries would be skipped and
reported. No such entries were skipped in this snapshot. Unknown attributes cause
generation to fail rather than silently widening a profile's applicability.

```sh
python3 android81/build-apn-catalog.py
python3 -m unittest discover -s android81/tests -p test_apn_catalog.py -v
bash android81/stage-device.sh
```

Alternatively pass `--source /path/to/cached/apns-full-conf.xml`; the same pinned
hash is required. Credentials present in the XML are public carrier defaults,
not account-specific secrets. This catalogue does not enable paid services,
mobile data, roaming or IMS registration by itself.

## Development deployment and evidence

The previous XML was the minimal 6,575-byte Android sample. A platform-signed,
temporary engineering activity confirmed **22 database rows and zero Romanian
profiles** before deployment. The old XML and private provider snapshots were
saved locally outside Git.

After atomic installation, permissions/context restoration and returning system
to read-only, the helper called `carriers/update_db`. This refresh retains edited
APNs; it does not use the destructive `carriers/restore` endpoint. The helper
snapshots preferred APN identities per active subscription and restores matching
preferences after the provider changes IDs. It checks existing custom APN IDs
and identities afterwards. The current phone had no custom or preferred APNs,
so preservation of non-empty user configurations remains untested on hardware.

The provider reported **843 database rows, including all 18 Romanian profiles**.
This is not a file-count error: TelephonyProvider merges defaults according to
its database uniqueness rules and also manages internal resource defaults.
Six host checks passed: integrity, EU coverage, Romanian Internet/IMS separation,
Oreo schema/radio constraints, MVNO specificity and rejection of unpinned input.
Mobile data on SIM 2 remained connected. MMS delivery and every foreign operator
are untested; operator-specific subscriptions may need a manually supplied APN.
The engineering APK was removed after saving the reports.

The previously published ROM ZIP has not been replaced. Rebuild/stage this
catalogue explicitly when assembling the next ROM.

## Dual LTE and IMS are separate work

The user reports that both SIMs supported LTE/VoLTE in original KaiOS. This is a
reference for further porting, not evidence of a hardware limitation.
On the current fresh Leo installation, active subscription IDs are 1 (slot 0,
Digi) and 2 (slot 1, Orange). A direct TelephonyManager query returned mode **9**
(LTE/GSM/WCDMA automatic) for both; their per-subscription saved preferences also
read 9. At inspection, Digi remained voice-registered on GSM and Orange was
voice/data-registered on LTE. A preferred mode is not proof of simultaneous LTE.
The cached radio-family values are also not proof of modem stack allocation.

The current `LeoImsController` and `port-ims-client.py` explicitly manage **one
selected physical IMS slot per process**. Both simultaneous VoLTE registrations
require a real multi-endpoint/slot lifecycle, not just two APN records, duplicated
status icons or changing the `dsds` property. DSDS is already enabled. During this
inspection the selected client reported registration but no voice capability;
the fresh-install IMS verifier therefore did not pass. No new call was placed
and no simultaneous dual-VoLTE claim is made. The earlier supervised VoLTE tests
document previous installations and do not validate this fresh state.
