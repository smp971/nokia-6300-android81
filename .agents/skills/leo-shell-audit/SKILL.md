---
name: leo-shell-audit
description: Audit the Nokia 6300 Leo Visual shell for feature coverage, keypad regressions, unsafe Android entry points, persistence gaps, and release readiness. Use for explicit Leo feature inventories, code audits, and regression triage.
---

Start with `android81/LEO-SHELL-AUDIT.md` for the baseline and `python -X utf8 android81/audit-leo-shell.py` for current catalog and manifest coverage. Record the Git revision and, if using hardware, the running APK digest. Trace the user entry point, action, service and data store separately. A manifest entry proves wiring only. Distinguish fresh host results, fresh device checks, historical demonstrations, and unverified hardware or external services.

Review boundaries around exported Home/Dialer intents, the global accessibility menu, root helpers, imported files and restore journals. `MainActivity` is the launcher. `LeoActivity`, `KeypadDialog` and `KeypadTabs` cover most secondary pages. `ro.leo.keypad` and the framework/SystemUI overlay ship separately.

Use explicit Nokia ADB targeting (`695d` for the project handset, verify before use). Inspect an instrumentation test's setup and teardown before running it. Select loopback/synthetic tests on a daily phone. A hardware audit alone does not authorize real calls, sending SMS/email, changing SIM roles, rebooting, recording or installing updates. Save only sanitized evidence. When instrumentation restarts Leo, compare the prior Home, accessibility, notifications and IME state and restore changes caused by the test.

Report each confirmed finding with source, trigger, consequence and proposed check. Preserve uncertainty when the only evidence is a static trace or an old hardware run.
