#!/bin/bash
# Source in WSL before invoking the Android 8.1 build.
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH="/root/android81-leo/host-bin:$JAVA_HOME/bin:$PATH"
export LC_ALL=C
export LANG=C
export BUILD_USERNAME=leo-builder
export BUILD_HOSTNAME=android81-wsl
# The full framework's concurrent Jack/proguard jobs exceed Java's ~3.7GiB default.
# Scope the larger heap to this legacy Android build server.
export JACK_SERVER_VM_ARGUMENTS="-Dfile.encoding=UTF-8 -XX:+TieredCompilation -Xmx6g"
# jack_server_setup.mk ignores the launcher variable unless this AOSP option is set.
export ANDROID_JACK_VM_ARGS="$JACK_SERVER_VM_ARGUMENTS"
# Jack 4.x uses legacy TLS on localhost. The overlay is scoped to this build;
# system Java security settings remain untouched. Certificate verification stays on.
if [ -f /root/android81-leo/jack-java.security ]; then
    export JAVA_TOOL_OPTIONS="-Djava.security.properties=/root/android81-leo/jack-java.security"
    export JACK_EXTRA_CURL_OPTIONS="--tlsv1 --tls-max 1.0 --ciphers DEFAULT:@SECLEVEL=0"
fi
