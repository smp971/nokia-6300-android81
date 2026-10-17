#!/system/bin/sh
# Switch only graphics HAL files. Root ADB, boot, recovery and radio stay available.
set -eu
base=/system/vendor/lib/leo-adreno
marker=/data/local/tmp/leo-adreno-accepted
stop_graphics() {
    for service in zygote surfaceflinger gralloc-2-0 hwcomposer-2-1; do
        setprop ctl.stop "$service"
    done
    sleep 3
}
start_graphics() {
    for service in hwcomposer-2-1 gralloc-2-0 surfaceflinger zygote; do
        setprop ctl.start "$service"
    done
}
replace() {
    cp "$1" "$2.new"
    chmod 644 "$2.new"
    chown 0:0 "$2.new"
    restorecon "$2.new"
    mv -f "$2.new" "$2"
}
case "${1:-}" in
    watch)
        sleep 300
        # A verified installation remains enabled on later ordinary boots.
        if [ "$(getprop persist.leo.adreno.validated)" = 1 ]; then exit 0; fi
        if [ ! -f "$marker" ] || [ "$(cat "$marker")" != "$(cat /proc/sys/kernel/random/boot_id)" ]; then
            /system/bin/sh "$base/switch.sh" rollback
        fi
        ;;
    activate)
        test -r "$base/gralloc-software.so"
        test -r "$base/hwcomposer-adreno.so"
        rm -f "$marker"
        setprop persist.leo.adreno.validated 0
        setprop persist.leo.adreno.hwui_finish 1
        # Start recovery before stopping Android; it is independent of zygote.
        nohup /system/bin/sh /tmp/adreno-integration-switch.sh watch \
            > /data/local/tmp/leo-adreno-watch.log 2>&1 < /dev/null &
        stop_graphics
        mount -o remount,rw /system
        trap 'mount -o remount,ro /system' EXIT
        cp "$base/egl/"*.so /system/vendor/lib/egl/
        # Exact filenames take precedence over the untouched SwiftShader files.
        replace "$base/egl/libEGL_adreno.so" /system/vendor/lib/egl/libEGL.so
        replace "$base/egl/libGLESv1_CM_adreno.so" /system/vendor/lib/egl/libGLESv1_CM.so
        replace "$base/egl/libGLESv2_adreno.so" /system/vendor/lib/egl/libGLESv2.so
        # This Oreo loader's exact-name branch omits the .so suffix.
        ln -sf libEGL_adreno.so /system/vendor/lib/egl/libEGL
        ln -sf libGLESv1_CM_adreno.so /system/vendor/lib/egl/libGLESv1_CM
        ln -sf libGLESv2_adreno.so /system/vendor/lib/egl/libGLESv2
        chmod 644 /system/vendor/lib/egl/*.so
        replace "$base/gralloc-adreno-bridge.so" /system/vendor/lib/hw/gralloc.default.so
        replace "$base/hwcomposer-adreno.so" /system/vendor/lib/hw/hwcomposer.default.so
        replace "$base/hwcomposer-adreno.so" /system/vendor/lib/hw/hwcomposer.leo.so
        replace "$base/libhwui-adreno.so" /system/lib/libhwui.so
        cp /tmp/leo-adreno.rc /system/etc/init/leo-adreno.rc
        cp /tmp/adreno-integration-switch.sh "$base/switch.sh"
        chmod 644 "$base/switch.sh"
        chmod 644 /system/etc/init/leo-adreno.rc
        restorecon -R /system/vendor/lib/egl /system/etc/init/leo-adreno.rc
        sync
        mount -o remount,ro /system
        trap - EXIT
        chown 1000:1000 /dev/kgsl-3d0 /dev/ion
        chmod 666 /dev/kgsl-3d0
        chmod 664 /dev/ion
        printf '%s' /vendor/firmware > /sys/module/firmware_class/parameters/path
        echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor
        start_graphics
        echo ADRENO_GRAPHICS_STARTED
        ;;
    rollback)
        setprop persist.leo.adreno.validated 0
        stop_graphics
        mount -o remount,rw /system
        trap 'mount -o remount,ro /system' EXIT
        replace "$base/gralloc-software.so" /system/vendor/lib/hw/gralloc.default.so
        replace "$base/hwcomposer.default.so" /system/vendor/lib/hw/hwcomposer.default.so
        replace "$base/hwcomposer.leo.so" /system/vendor/lib/hw/hwcomposer.leo.so
        if [ -f "$base/libhwui-original.so" ]; then
            replace "$base/libhwui-original.so" /system/lib/libhwui.so
        fi
        setprop persist.leo.adreno.hwui_finish 0
        rm -f /system/vendor/lib/egl/libEGL.so /system/vendor/lib/egl/libGLESv1_CM.so \
            /system/vendor/lib/egl/libGLESv2.so /system/vendor/lib/egl/libEGL_adreno.so \
            /system/vendor/lib/egl/libGLESv1_CM_adreno.so /system/vendor/lib/egl/libGLESv2_adreno.so \
            /system/vendor/lib/egl/libq3dtools_adreno.so /system/vendor/lib/egl/eglsubAndroid.so \
            /system/etc/init/leo-adreno.rc
        rm -f /system/vendor/lib/egl/libEGL /system/vendor/lib/egl/libGLESv1_CM \
            /system/vendor/lib/egl/libGLESv2
        sync
        mount -o remount,ro /system
        trap - EXIT
        chown 0:0 /dev/kgsl-3d0 /dev/ion
        chmod 600 /dev/kgsl-3d0 /dev/ion
        printf '%s' /firmware/image > /sys/module/firmware_class/parameters/path
        echo msm-adreno-tz > /sys/class/kgsl/kgsl-3d0/devfreq/governor
        start_graphics
        echo SWIFTSHADER_RESTORED
        ;;
    *) exit 2 ;;
esac
