# Pocket app dependencies

- **JSch 2.28.0**, maintained by Matthias Wiedemann and contributors, forked from JCraft JSch. BSD-style terms and bundled JZlib/jBCrypt notices are included here. The original Maven jar is pinned by SHA-256 in `sources.json`.
- **Project Nayuki QR Code generator**, Java implementation, MIT. The exact upstream commit and each source-file hash are in `sources.json`; original licence headers are retained.

`prepare-pocket-dependencies.py` verifies every pin, keeps the Java 8 root classes from the multi-release SSH jar, removes module metadata and strips only MethodParameters debug metadata to accommodate the pinned D8 compiler. Code, cryptography and host-key validation are not removed or weakened. The APK carries the applicable notices in `assets/licenses/POCKET-THIRD-PARTY.txt`.

Optional SSH algorithms needing additional providers are not enabled. The Leo client selects ECDSA or RSA/SHA-2 host keys and supported ECDH/group14-SHA256 exchanges. The on-device fixture validated RSA/SHA-2 authentication, an interactive PTY shell and SFTP using Android's existing providers. Password authentication is the current UI; private-key import and a full terminal emulator are future work.

ZXing 3.5.3 is downloaded and hash-checked by the standalone **test** builder for independent QR decoding. It is not included in Leo Visual. Test-server dependencies such as Paramiko are also host-only.
