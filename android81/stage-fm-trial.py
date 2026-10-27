"""Legacy FM staging has been replaced by the clean system-image builder.

The old /data executable layout is incompatible with the new system-owned FM
runtime. Keep the installed phone's paired APK/runtime until installing the
complete matching image; never stage half of this migration.
"""
raise SystemExit("Build FM with build-fm-diagnostic.sh / build-fm-route.sh, then use build-recovery-bundle.py SNAPSHOT --fresh. Legacy /data staging is retired.")
