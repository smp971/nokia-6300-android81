/* Keep the known SPI framebuffer device, use stock QCOM ION for shared buffers. */
#include <hardware/gralloc.h>
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
static gralloc_module_t *stock, *software;
static pthread_once_t once = PTHREAD_ONCE_INIT;
extern gralloc_module_t HAL_MODULE_INFO_SYM;
static void initialize(void) {
    void *lib = dlopen("/vendor/lib/leo-adreno/gralloc.msm8909.so",RTLD_NOW|RTLD_LOCAL);
    if (lib) stock = dlsym(lib,"HMI");
    lib = dlopen("/vendor/lib/leo-adreno/gralloc-software.so",RTLD_NOW|RTLD_LOCAL);
    if (lib) software = dlsym(lib,"HMI");
    if (!stock || !software) { fprintf(stderr,"Leo gralloc bridge: %s\n",dlerror()); return; }
    /* This stock perform implementation only checks module != NULL and reads
       its varargs; it never dereferences the module. Preserve its full ABI. */
    HAL_MODULE_INFO_SYM.perform=stock->perform;
}
__attribute__((constructor)) static void setup(void) { pthread_once(&once,initialize); }
struct allocation { alloc_device_t device; alloc_device_t *original; };
static int allocate(alloc_device_t *device,int w,int h,int format,int usage,
                    buffer_handle_t *handle,int *stride) {
    struct allocation *a=(struct allocation*)device;
    /* HWC2On1 requires an RGBA client target; never hand out physical RGB565 fb. */
    return a->original->alloc(a->original,w,h,format,usage & ~GRALLOC_USAGE_HW_FB,handle,stride);
}
static int release(alloc_device_t *device,buffer_handle_t handle) {
    struct allocation *a=(struct allocation*)device;
    return a->original->free(a->original,handle);
}
static int close_alloc(hw_device_t *device) {
    struct allocation *a=(struct allocation*)device;
    int rc=gralloc_close(a->original); free(a); return rc;
}
static int open_device(const hw_module_t *module,const char *name,hw_device_t **out) {
    pthread_once(&once,initialize);
    if (!stock || !software || !out) return -ENODEV;
    if (!strcmp(name,GRALLOC_HARDWARE_FB0))
        return software->common.methods->open(&software->common,name,out);
    if (strcmp(name,GRALLOC_HARDWARE_GPU0)) return -EINVAL;
    struct allocation *a=calloc(1,sizeof(*a)); if (!a) return -ENOMEM;
    int rc=gralloc_open(&stock->common,&a->original);
    if (rc) { free(a); return rc; }
    a->device=*a->original;
    a->device.common.module=(hw_module_t*)module;
    a->device.common.close=close_alloc;
    a->device.alloc=allocate; a->device.free=release;
    *out=&a->device.common; return 0;
}
static int register_buffer(const gralloc_module_t *m,buffer_handle_t h) {
    (void)m; return stock ? stock->registerBuffer(stock,h) : -ENODEV;
}
static int unregister_buffer(const gralloc_module_t *m,buffer_handle_t h) {
    (void)m; return stock ? stock->unregisterBuffer(stock,h) : -ENODEV;
}
static int lock_buffer(const gralloc_module_t *m,buffer_handle_t h,int usage,
                       int x,int y,int w,int height,void **address) {
    (void)m; return stock ? stock->lock(stock,h,usage,x,y,w,height,address) : -ENODEV;
}
static int unlock_buffer(const gralloc_module_t *m,buffer_handle_t h) {
    (void)m; return stock ? stock->unlock(stock,h) : -ENODEV;
}
static hw_module_methods_t methods={.open=open_device};
gralloc_module_t HAL_MODULE_INFO_SYM={
    .common={.tag=HARDWARE_MODULE_TAG,.module_api_version=GRALLOC_MODULE_API_VERSION_0_1,
        .hal_api_version=HARDWARE_HAL_API_VERSION,.id=GRALLOC_HARDWARE_MODULE_ID,
        .name="Leo Adreno ION / SPI framebuffer bridge",.author="Leo bring-up",.methods=&methods},
    .registerBuffer=register_buffer,.unregisterBuffer=unregister_buffer,
    .lock=lock_buffer,.unlock=unlock_buffer
};
