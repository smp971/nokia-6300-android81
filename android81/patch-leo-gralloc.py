"""Use shared software buffers for Leo's explicit RGBA-to-RGB565 HWC bridge."""
from pathlib import Path
root=Path('/root/android81-leo/src/hardware/libhardware/modules/gralloc')
source=root/'gralloc.cpp'
text=source.read_text()
marker='// LEO_SOFTWARE_HWC: HWC converts shared RGBA buffers into the physical RGB565 fb.'
needle='    if (!pHandle || !pStride)\n        return -EINVAL;\n'
replacement=needle+'''
#ifdef LEO_SOFTWARE_HWC
    // LEO_SOFTWARE_HWC: HWC converts shared RGBA buffers into the physical RGB565 fb.
    // Physical framebuffer handles contain allocator-process pointers and have a
    // different pixel size. Never expose them as RGBA client targets over HIDL.
    usage &= ~GRALLOC_USAGE_HW_FB;
#endif
'''
if marker not in text:
    assert text.count(needle)==1
    source.with_name('gralloc.cpp.leo-original').write_text(text)
    source.write_text(text.replace(needle,replacement))
make=root/'Android.mk'
text=make.read_text()
marker='# LEO software framebuffer bridge only.'
if marker not in text:
    needle='include $(BUILD_SHARED_LIBRARY)'
    assert text.count(needle)==1
    make.with_name('Android.mk.leo-original').write_text(text)
    make.write_text(text.replace(needle,'''# LEO software framebuffer bridge only.
ifeq ($(TARGET_DEVICE),leo)
LOCAL_CFLAGS += -DLEO_SOFTWARE_HWC
endif

'''+needle))
