"""Use same-context glFinish for this legacy driver's rejected bitmap fence wait."""
from pathlib import Path
path=Path('/root/android81-leo/src/frameworks/base/libs/hwui/renderthread/OpenGLPipeline.cpp')
text=path.read_text()
marker='// LEO_ADRENO_BITMAP_FENCE: this upload owns the current GL context.'
old='''    if (waitStatus != EGL_CONDITION_SATISFIED_KHR) {
        LOG_ALWAYS_FATAL("Failed to wait for the fence %#x", eglGetError());
        return false;
    }'''
new='''    if (waitStatus != EGL_CONDITION_SATISFIED_KHR) {
        EGLint error = eglGetError();
        // LEO_ADRENO_BITMAP_FENCE: this upload owns the current GL context.
        // The stock A304 driver rejects both finite and infinite client waits.
        // Finish this context's upload, rather than ignoring synchronization.
        const char* vendor = reinterpret_cast<const char*>(glGetString(GL_VENDOR));
        if (error == EGL_BAD_PARAMETER &&
                property_get_bool("persist.leo.adreno.hwui_finish", false) &&
                vendor && strstr(vendor, "Qualcomm")) {
            glFinish();
            if (glGetError() == GL_NO_ERROR) {
                static bool warned = false;
                if (!warned) {
                    ALOGW("Leo Adreno: hardware bitmap upload synchronized with glFinish");
                    warned = true;
                }
                return true;
            }
        }
        LOG_ALWAYS_FATAL("Failed to wait for the fence %#x", error);
        return false;
    }'''
if marker not in text:
    assert text.count(old)==1
    text=text.replace('#include <strings.h>','#include <strings.h>\n#include <string.h>')
    path.write_text(text.replace(old,new))
