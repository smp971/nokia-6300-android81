/* HWC 1.3 framebuffer bridge for the diagnostic RGB565 SPI display.
 * All layers are client-composed; no proprietary GPU or overlay ABI is used.
 */
#include <errno.h>
#include <fcntl.h>
#include <hardware/gralloc.h>
#include <hardware/hwcomposer.h>
#include <linux/fb.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sync/sync.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>

struct LeoHwc {
    hwc_composer_device_1_t device;
    framebuffer_device_t *fb;
    int fbfd;
    pthread_t thread;
    pthread_mutex_t mutex;
    const hwc_procs_t *procs;
    bool running,vsync,powered;
    const gralloc_module_t *gralloc;
    fb_var_screeninfo var;
    fb_fix_screeninfo fix;
    uint8_t *mapped;
    unsigned page,frames;
};
static LeoHwc *context(hwc_composer_device_1_t *d){return reinterpret_cast<LeoHwc*>(d);}
static constexpr int64_t period=1000000000LL/29;
static void *vsyncLoop(void *arg){
    LeoHwc *c=static_cast<LeoHwc*>(arg);
    for(;;){
        timespec delay={0,static_cast<long>(period)};nanosleep(&delay,nullptr);
        pthread_mutex_lock(&c->mutex);
        bool run=c->running,active=c->vsync&&c->powered;
        const hwc_procs_t *p=c->procs;pthread_mutex_unlock(&c->mutex);
        if(!run)break;
        if(active&&p&&p->vsync){timespec now;clock_gettime(CLOCK_MONOTONIC,&now);p->vsync(p,HWC_DISPLAY_PRIMARY,static_cast<int64_t>(now.tv_sec)*1000000000LL+now.tv_nsec);}
    }
    return nullptr;
}
static int prepare(hwc_composer_device_1_t*,size_t count,hwc_display_contents_1_t **displays){
    if(count&&displays&&displays[0])for(size_t i=0;i<displays[0]->numHwLayers;i++){
        hwc_layer_1_t &layer=displays[0]->hwLayers[i];
        if(layer.compositionType!=HWC_FRAMEBUFFER_TARGET)layer.compositionType=HWC_FRAMEBUFFER;
        layer.hints=0;
    }
    return 0;
}
static int present(hwc_composer_device_1_t *dev,size_t count,hwc_display_contents_1_t **displays){
    LeoHwc *c=context(dev);buffer_handle_t target=nullptr;
    if(!count||!displays||!displays[0])return 0;
    hwc_display_contents_1_t *display=displays[0];
    for(size_t i=0;i<display->numHwLayers;i++){
        hwc_layer_1_t &layer=display->hwLayers[i];
        if(layer.acquireFenceFd>=0){sync_wait(layer.acquireFenceFd,3000);close(layer.acquireFenceFd);layer.acquireFenceFd=-1;}
        layer.releaseFenceFd=-1;
        if(layer.compositionType==HWC_FRAMEBUFFER_TARGET)target=layer.handle;
    }
    display->retireFenceFd=-1;
    if(!target)return 0;
    // HWC2On1Adapter accepts RGBA8888 client targets only. The physical display
    // is RGB565, so copying an unconverted client target through fb->post is wrong.
    void *source=nullptr;
    int rc=c->gralloc->lock(c->gralloc,target,GRALLOC_USAGE_SW_READ_OFTEN,0,0,c->fb->width,c->fb->height,&source);
    if(rc)return rc;
    c->page^=1;
    size_t sourceStride=(c->fb->width+1)&~size_t(1); // Software gralloc tileWidth=2.
    if(c->gralloc->perform){
        int stride=0;
        // Stock Qualcomm GET_CUSTOM_STRIDE_FROM_HANDLE; never infer ION padding.
        rc=c->gralloc->perform(c->gralloc,3,target,&stride);
        if(rc||stride<static_cast<int>(c->fb->width)||stride>4096){
            c->gralloc->unlock(c->gralloc,target);return -EINVAL;
        }
        sourceStride=static_cast<size_t>(stride);
    }
    for(unsigned y=0;y<c->fb->height;y++){
        const uint8_t *row=static_cast<const uint8_t*>(source)+y*sourceStride*4;
        uint16_t *out=reinterpret_cast<uint16_t*>(c->mapped+(y+c->page*c->fb->height)*c->fix.line_length);
        for(unsigned x=0;x<c->fb->width;x++)out[x]=static_cast<uint16_t>(((row[x*4]>>3)<<11)|((row[x*4+1]>>2)<<5)|(row[x*4+2]>>3));
    }
    c->gralloc->unlock(c->gralloc,target);
    c->var.yoffset=c->page*c->fb->height;c->var.activate=FB_ACTIVATE_VBL|FB_ACTIVATE_FORCE;
    rc=ioctl(c->fbfd,FBIOPAN_DISPLAY,&c->var);
    if(rc<0)return -errno;
    c->frames++;return 0;
}
static int event(hwc_composer_device_1_t *dev,int display,int eventId,int enabled){
    if(display!=HWC_DISPLAY_PRIMARY||eventId!=HWC_EVENT_VSYNC)return -EINVAL;
    LeoHwc *c=context(dev);pthread_mutex_lock(&c->mutex);c->vsync=enabled!=0;pthread_mutex_unlock(&c->mutex);return 0;
}
static int blank(hwc_composer_device_1_t *dev,int display,int value){
    if(display!=HWC_DISPLAY_PRIMARY)return -EINVAL;
    LeoHwc *c=context(dev);pthread_mutex_lock(&c->mutex);c->powered=!value;pthread_mutex_unlock(&c->mutex);
    int result=ioctl(c->fbfd,FBIOBLANK,value?FB_BLANK_POWERDOWN:FB_BLANK_UNBLANK);return result<0?-errno:0;
}
static int query(hwc_composer_device_1_t*,int what,int *value){
    if(!value)return -EINVAL;
    switch(what){
        case HWC_BACKGROUND_LAYER_SUPPORTED:*value=0;return 0;
        case HWC_VSYNC_PERIOD:*value=static_cast<int>(period);return 0;
        case HWC_DISPLAY_TYPES_SUPPORTED:*value=HWC_DISPLAY_PRIMARY_BIT;return 0;
        default:return -EINVAL;
    }
}
static void callbacks(hwc_composer_device_1_t *dev,const hwc_procs_t *p){
    LeoHwc *c=context(dev);pthread_mutex_lock(&c->mutex);c->procs=p;pthread_mutex_unlock(&c->mutex);
}
static int configs(hwc_composer_device_1_t*,int display,uint32_t *config,size_t *count){
    if(!count||display!=HWC_DISPLAY_PRIMARY)return -EINVAL;
    if(config&&*count)config[0]=0;*count=1;return 0;
}
static int attributes(hwc_composer_device_1_t *dev,int display,uint32_t config,const uint32_t *attrs,int32_t *values){
    if(display!=HWC_DISPLAY_PRIMARY||config!=0||!attrs||!values)return -EINVAL;
    LeoHwc *c=context(dev);
    for(size_t i=0;attrs[i]!=HWC_DISPLAY_NO_ATTRIBUTE;i++)switch(attrs[i]){
        case HWC_DISPLAY_VSYNC_PERIOD:values[i]=static_cast<int32_t>(period);break;
        case HWC_DISPLAY_WIDTH:values[i]=static_cast<int32_t>(c->fb->width);break;
        case HWC_DISPLAY_HEIGHT:values[i]=static_cast<int32_t>(c->fb->height);break;
        case HWC_DISPLAY_DPI_X:values[i]=static_cast<int32_t>(c->fb->xdpi*1000);break;
        case HWC_DISPLAY_DPI_Y:values[i]=static_cast<int32_t>(c->fb->ydpi*1000);break;
        default:return -EINVAL;
    }
    return 0;
}
static int active(hwc_composer_device_1_t*,int display){return display==HWC_DISPLAY_PRIMARY?0:-EINVAL;}
static int setActive(hwc_composer_device_1_t*,int display,int index){return display==HWC_DISPLAY_PRIMARY&&index==0?0:-EINVAL;}
static void dump(hwc_composer_device_1_t *dev,char *buffer,int length){
    LeoHwc *c=context(dev);if(buffer&&length>0)snprintf(buffer,static_cast<size_t>(length),"Leo software HWC: %ux%u RGBA8888 to RGB565, client composition, software vsync 29Hz, frames=%u\n",c->fb->width,c->fb->height,c->frames);
}
static int closeDevice(hw_device_t *device){
    LeoHwc *c=reinterpret_cast<LeoHwc*>(device);pthread_mutex_lock(&c->mutex);c->running=false;pthread_mutex_unlock(&c->mutex);
    pthread_join(c->thread,nullptr);pthread_mutex_destroy(&c->mutex);munmap(c->mapped,c->fix.smem_len);framebuffer_close(c->fb);close(c->fbfd);free(c);return 0;
}
static int openDevice(const hw_module_t *module,const char *name,hw_device_t **result){
    if(strcmp(name,HWC_HARDWARE_COMPOSER)||!result)return -EINVAL;
    const hw_module_t *gralloc=nullptr;int rc=hw_get_module(GRALLOC_HARDWARE_MODULE_ID,&gralloc);if(rc)return rc;
    LeoHwc *c=static_cast<LeoHwc*>(calloc(1,sizeof(*c)));if(!c)return -ENOMEM;
    rc=framebuffer_open(gralloc,&c->fb);if(rc){free(c);return rc;}
    c->fbfd=open("/dev/graphics/fb0",O_RDWR|O_CLOEXEC);
    if(c->fbfd<0){rc=-errno;framebuffer_close(c->fb);free(c);return rc;}
    if(ioctl(c->fbfd,FBIOGET_VSCREENINFO,&c->var)||ioctl(c->fbfd,FBIOGET_FSCREENINFO,&c->fix)||
       c->fb->width!=240||c->fb->height!=320||c->var.bits_per_pixel!=16||
       c->var.yres_virtual<640||c->fix.smem_len<c->fix.line_length*640){
        close(c->fbfd);framebuffer_close(c->fb);free(c);return -EINVAL;
    }
    c->mapped=static_cast<uint8_t*>(mmap(nullptr,c->fix.smem_len,PROT_READ|PROT_WRITE,MAP_SHARED,c->fbfd,0));
    if(c->mapped==MAP_FAILED){rc=-errno;close(c->fbfd);framebuffer_close(c->fb);free(c);return rc;}
    c->gralloc=reinterpret_cast<const gralloc_module_t*>(gralloc);
    c->device.common.tag=HARDWARE_DEVICE_TAG;c->device.common.version=HWC_DEVICE_API_VERSION_1_3;
    c->device.common.module=const_cast<hw_module_t*>(module);c->device.common.close=closeDevice;
    c->device.prepare=prepare;c->device.set=present;c->device.eventControl=event;c->device.blank=blank;c->device.query=query;
    c->device.registerProcs=callbacks;c->device.dump=dump;c->device.getDisplayConfigs=configs;c->device.getDisplayAttributes=attributes;
    c->device.getActiveConfig=active;c->device.setActiveConfig=setActive;
    c->running=true;c->powered=true;pthread_mutex_init(&c->mutex,nullptr);
    rc=pthread_create(&c->thread,nullptr,vsyncLoop,c);
    if(rc){pthread_mutex_destroy(&c->mutex);munmap(c->mapped,c->fix.smem_len);close(c->fbfd);framebuffer_close(c->fb);free(c);return -rc;}
    *result=&c->device.common;return 0;
}
static hw_module_methods_t methods={openDevice};
extern "C" {
hwc_module_t HAL_MODULE_INFO_SYM={.common={.tag=HARDWARE_MODULE_TAG,.module_api_version=HWC_MODULE_API_VERSION_0_1,
    .hal_api_version=HARDWARE_HAL_API_VERSION,.id=HWC_HARDWARE_MODULE_ID,.name="Leo framebuffer software composition",
    .author="Leo Android diagnostic",.methods=&methods}};
}
