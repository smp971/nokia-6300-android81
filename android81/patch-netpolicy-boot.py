"""Fix Oreo startup completion when this kernel lacks bandwidth accounting."""
from pathlib import Path

ROOT=Path(__file__).resolve().parent
SOURCE=Path('/root/android81-leo/src/frameworks/base/services/core/java/com/android/server/net')
BACKUP=ROOT/'output/netpolicy-source-before'
BACKUP.mkdir(parents=True,exist_ok=True)
policy=SOURCE/'NetworkPolicyManagerService.java'
text=policy.read_text()
marker='// Leo: always release startup, including unsupported bandwidth control.'
if marker not in text:
    start=text.index('    private void initService(CountDownLatch initCompleteSignal)')
    end=text.index('    public CountDownLatch networkScoreAndNetworkManagementServiceReady()',start)
    method=text[start:end]
    old='            // tell systemReady() that the service has been initialized\n            initCompleteSignal.countDown();\n'
    assert method.count(old)==1
    method=method.replace(old,'')
    old='        } finally {\n            // Restore the default priority after init is done'
    assert method.count(old)==1
    method=method.replace(old,'        } finally {\n            '+marker+'\n            initCompleteSignal.countDown();\n            // Restore the default priority after init is done')
    target=BACKUP/policy.name
    if not target.exists(): target.write_text(text)
    policy.write_text(text[:start]+method+text[end:])
stats=SOURCE/'NetworkStatsService.java'
text=stats.read_text()
marker='// Leo: no recorders exist when kernel bandwidth accounting is unavailable.'
if marker not in text:
    old='''        if (!isBandwidthControlEnabled()) {
            Slog.w(TAG, "bandwidth controls disabled, unable to track stats");'''
    assert text.count(old)==1
    updated=text.replace(old,'''        if (!isBandwidthControlEnabled()) {
            mSystemReady = false;
            Slog.w(TAG, "bandwidth controls disabled, unable to track stats");''')
    old='''        synchronized (mStatsLock) {
            if (args.length > 0 && "--proto".equals(args[0])) {'''
    assert updated.count(old)==1
    updated=updated.replace(old,'''        synchronized (mStatsLock) {
            '''+marker+'''
            if (!mSystemReady || mDevRecorder == null || mXtRecorder == null
                    || mUidRecorder == null || mUidTagRecorder == null) {
                pw.println("Network usage accounting unavailable: recorders not initialized");
                return;
            }
            if (args.length > 0 && "--proto".equals(args[0])) {''')
    target=BACKUP/stats.name
    if not target.exists(): target.write_text(text)
    stats.write_text(updated)
print('Patched startup completion and unsupported-statistics dump; accounting stays explicitly unavailable')
