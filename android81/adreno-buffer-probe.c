/* Exercise the stock ION allocator privately, without changing Android's HAL. */
#include <hardware/gralloc.h>
#include <dlfcn.h>
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <sys/wait.h>
#include <nativebase/nativebase.h>
static const gralloc_module_t *stock_module;
int hw_get_module(const char *id, const hw_module_t **module) {
    if (strcmp(id, "gralloc") || !stock_module) return -2;
    *module = &stock_module->common; return 0;
}
static void reference(struct android_native_base_t *base) { (void)base; }
#define LOAD(lib, result, name, args) \
    result (*name) args = dlsym(lib, #name); \
    if (!name) { printf("Missing %s: %s\n", #name, dlerror()); return 20; }
static int gpu_import(buffer_handle_t handle, int stride) {
    void *egl = dlopen("libEGL_adreno.so", RTLD_NOW);
    void *gl = dlopen("libGLESv2_adreno.so", RTLD_NOW);
    if (!egl || !gl) { puts(dlerror()); return 10; }
    LOAD(egl, void *, eglGetDisplay, (void *));
    LOAD(egl, unsigned, eglInitialize, (void *, int *, int *));
    LOAD(egl, int, eglGetError, (void));
    LOAD(egl, unsigned, eglChooseConfig, (void *, const int *, void **, int, int *));
    LOAD(egl, void *, eglCreatePbufferSurface, (void *, void *, const int *));
    LOAD(egl, void *, eglCreateContext, (void *, void *, void *, const int *));
    LOAD(egl, unsigned, eglMakeCurrent, (void *, void *, void *, void *));
    LOAD(egl, void *, eglCreateImageKHR, (void *, void *, unsigned, void *, const int *));
    LOAD(egl, unsigned, eglDestroyImageKHR, (void *, void *));
    LOAD(gl, void, glGenTextures, (int, unsigned *));
    LOAD(gl, void, glBindTexture, (unsigned, unsigned));
    LOAD(gl, void, glEGLImageTargetTexture2DOES, (unsigned, void *));
    LOAD(gl, void, glGenFramebuffers, (int, unsigned *));
    LOAD(gl, void, glBindFramebuffer, (unsigned, unsigned));
    LOAD(gl, void, glFramebufferTexture2D, (unsigned, unsigned, unsigned, unsigned, int));
    LOAD(gl, unsigned, glCheckFramebufferStatus, (unsigned));
    LOAD(gl, void, glClearColor, (float, float, float, float));
    LOAD(gl, void, glClear, (unsigned));
    LOAD(gl, void, glFinish, (void));
    LOAD(gl, unsigned, glGetError, (void));
    void *display = eglGetDisplay(0); int major, minor;
    if (!eglInitialize(display, &major, &minor)) return 11;
    const int attrs[] = {0x3033,1,0x3040,4,0x3024,8,0x3023,8,0x3022,8,0x3038};
    void *config = NULL; int count;
    if (!eglChooseConfig(display, attrs, &config, 1, &count) || count < 1) return 12;
    const int size[] = {0x3057,16,0x3056,16,0x3038}, ctx[] = {0x3098,2,0x3038};
    void *surface = eglCreatePbufferSurface(display, config, size);
    void *context = eglCreateContext(display, config, NULL, ctx);
    if (!surface || !context || !eglMakeCurrent(display, surface, surface, context)) return 13;
    ANativeWindowBuffer buffer; memset(&buffer, 0, sizeof(buffer));
    buffer.common.magic = ANDROID_NATIVE_BUFFER_MAGIC;
    buffer.common.version = sizeof(buffer);
    buffer.common.incRef = reference; buffer.common.decRef = reference;
    buffer.width=240; buffer.height=320; buffer.stride=stride;
    buffer.format=HAL_PIXEL_FORMAT_RGBA_8888; buffer.handle=handle;
    buffer.usage_deprecated = buffer.usage = GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_HW_TEXTURE |
        GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN;
    void *image = eglCreateImageKHR(display, NULL, 0x3140, &buffer, NULL);
    printf("egl_import=%p error=0x%x\n", image, eglGetError()); if (!image) return 14;
    unsigned texture, framebuffer;
    glGenTextures(1,&texture); glBindTexture(0x0de1,texture);
    glEGLImageTargetTexture2DOES(0x0de1,image);
    glGenFramebuffers(1,&framebuffer); glBindFramebuffer(0x8d40,framebuffer);
    glFramebufferTexture2D(0x8d40,0x8ce0,0x0de1,texture,0);
    unsigned status=glCheckFramebufferStatus(0x8d40);
    printf("framebuffer=0x%x gl_error=0x%x\n",status,glGetError());
    if (status!=0x8cd5) return 15;
    glClearColor(0.25f,0.5f,0.75f,1); glClear(0x4000); glFinish();
    unsigned error=glGetError();
    glBindFramebuffer(0x8d40,0); eglDestroyImageKHR(display,image);
    eglMakeCurrent(display,NULL,NULL,NULL);
    return error ? 16 : 0;
}
int main(void) {
    setbuf(stdout, NULL); alarm(20);
    const char *module_path=getenv("LEO_GRALLOC_MODULE");
    if (!module_path) module_path="/tmp/adreno-gralloc-libs/gralloc.msm8909.so";
    void *library = dlopen(module_path, RTLD_NOW);
    if (!library) { puts(dlerror()); return 1; }
    const gralloc_module_t *module = dlsym(library, "HMI");
    if (!module) return 2;
    stock_module = module;
    printf("module=%s version=0x%x\n", module->common.name, module->common.module_api_version);
    alloc_device_t *device = NULL;
    int result = gralloc_open(&module->common, &device);
    printf("open=%d\n", result); if (result) return 3;
    buffer_handle_t buffer = NULL; int stride = 0;
    result = device->alloc(device, 240, 320, HAL_PIXEL_FORMAT_RGBA_8888,
        GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_HW_TEXTURE |
        GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN, &buffer, &stride);
    printf("alloc=%d stride=%d\n", result, stride); if (result) return 4;
    int queried = 0;
    result = module->perform(module, 3, buffer, &queried);
    printf("perform_custom_stride=%d stride=%d fds=%d ints=%d\n",
           result, queried, buffer->numFds, buffer->numInts);
    void *address = NULL;
    result = module->lock(module, buffer, GRALLOC_USAGE_SW_WRITE_OFTEN, 0, 0, 240, 320, &address);
    printf("lock=%d\n", result); if (result) return 5;
    memset(address, 0x6a, stride * 320 * 4);
    if (module->unlock(module, buffer)) return 6;
    address = NULL;
    result = module->lock(module, buffer, GRALLOC_USAGE_SW_READ_OFTEN, 0, 0, 240, 320, &address);
    if (result) return 7;
    int ok = ((unsigned char *)address)[0] == 0x6a &&
             ((unsigned char *)address)[(stride * 319 + 239) * 4] == 0x6a;
    module->unlock(module, buffer);
    if (!ok) return 8;
    result = gpu_import(buffer,stride);
    printf("gpu_import_result=%d\n", result); if (result) return result;
    result = module->lock(module,buffer,GRALLOC_USAGE_SW_READ_OFTEN,0,0,240,320,&address);
    if (result) return 17;
    unsigned char *pixel=(unsigned char*)address+(stride*8+8)*4;
    printf("gpu_to_cpu=%u,%u,%u,%u\n",pixel[0],pixel[1],pixel[2],pixel[3]);
    ok=pixel[0]==64 && pixel[1]==128 && pixel[2]==191 && pixel[3]==255;
    module->unlock(module,buffer);
    pid_t child=fork();
    if (child==0) {
        /* HIDL sends cloned handle integers and duplicated fds to the mapper. */
        native_handle_t *copy=malloc(sizeof(native_handle_t)+(buffer->numFds+buffer->numInts)*sizeof(int));
        memcpy(copy,buffer,sizeof(native_handle_t)+(buffer->numFds+buffer->numInts)*sizeof(int));
        for (int i=0;i<copy->numFds;i++) copy->data[i]=dup(buffer->data[i]);
        if (module->registerBuffer(module,copy)) _exit(21);
        void *mapped=NULL;
        if (module->lock(module,copy,GRALLOC_USAGE_SW_READ_OFTEN,0,0,240,320,&mapped)) _exit(22);
        unsigned char *p=(unsigned char*)mapped+(stride*8+8)*4;
        int child_ok=p[0]==64 && p[1]==128 && p[2]==191 && p[3]==255;
        module->unlock(module,copy); module->unregisterBuffer(module,copy);
        _exit(child_ok ? 0 : 23);
    }
    int status=0;
    if (child<0 || waitpid(child,&status,0)!=child || !WIFEXITED(status) || WEXITSTATUS(status)) return 24;
    puts("LEO_ION_CROSS_PROCESS_OK");
    device->free(device, buffer); gralloc_close(device);
    puts(ok ? "LEO_ION_GPU_IMPORT_OK" : "LEO_ION_GPU_IMPORT_FAILED");
    return ok ? 0 : 8;
}
