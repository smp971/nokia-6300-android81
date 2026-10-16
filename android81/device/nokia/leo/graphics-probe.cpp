/* Test real EGL/GLES2 and gralloc without taking over the display. */
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <hardware/gralloc.h>
#include <hardware/hardware.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

int main() {
    setbuf(stdout,nullptr);
    alarm(20); // Bound a broken EGL/service wait; no orphaned CPU loop.
    const hw_module_t *module=nullptr;
    int rc=hw_get_module(GRALLOC_HARDWARE_MODULE_ID,&module);
    printf("gralloc_module=%d name=%s\n",rc,module?module->name:"missing");
    if(rc)return 2;
    alloc_device_t *alloc=nullptr;
    if(gralloc_open(module,&alloc))return 3;
    buffer_handle_t buffer=nullptr;int stride=0;
    rc=alloc->alloc(alloc,32,32,HAL_PIXEL_FORMAT_RGB_565,GRALLOC_USAGE_SW_READ_OFTEN|GRALLOC_USAGE_SW_WRITE_OFTEN,&buffer,&stride);
    printf("gralloc_alloc=%d stride=%d\n",rc,stride);if(rc)return 4;
    const gralloc_module_t *gralloc=reinterpret_cast<const gralloc_module_t*>(module);
    void *address=nullptr;
    rc=gralloc->lock(gralloc,buffer,GRALLOC_USAGE_SW_WRITE_OFTEN,0,0,32,32,&address);
    printf("gralloc_lock=%d\n",rc);if(rc)return 5;
    memset(address,0x5a,static_cast<size_t>(stride)*32*2);
    if(gralloc->unlock(gralloc,buffer))return 6;
    alloc->free(alloc,buffer);gralloc_close(alloc);
    EGLDisplay display=eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major=0,minor=0;
    if(!eglInitialize(display,&major,&minor)){printf("eglInitialize failed 0x%x\n",eglGetError());return 7;}
    printf("EGL %d.%d vendor=%s version=%s\n",major,minor,eglQueryString(display,EGL_VENDOR),eglQueryString(display,EGL_VERSION));
    const EGLint configAttrs[]={EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_NONE};
    EGLConfig config;EGLint count=0;
    if(!eglChooseConfig(display,configAttrs,&config,1,&count)||count<1)return 8;
    const EGLint size[]={EGL_WIDTH,16,EGL_HEIGHT,16,EGL_NONE};
    EGLSurface surface=eglCreatePbufferSurface(display,config,size);
    const EGLint ctxAttrs[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
    EGLContext context=eglCreateContext(display,config,EGL_NO_CONTEXT,ctxAttrs);
    if(surface==EGL_NO_SURFACE||context==EGL_NO_CONTEXT||!eglMakeCurrent(display,surface,surface,context))return 9;
    printf("GL vendor=%s renderer=%s version=%s\n",glGetString(GL_VENDOR),glGetString(GL_RENDERER),glGetString(GL_VERSION));
    glClearColor(0.25f,0.5f,0.75f,1.0f);glClear(GL_COLOR_BUFFER_BIT);glFinish();
    unsigned char pixel[4]={0};glReadPixels(8,8,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
    GLenum error=glGetError();
    printf("readback=%u,%u,%u,%u gl_error=0x%x\n",pixel[0],pixel[1],pixel[2],pixel[3],error);
    bool ok=error==GL_NO_ERROR&&pixel[0]>=62&&pixel[0]<=65&&pixel[1]>=126&&pixel[1]<=129&&pixel[2]>=189&&pixel[2]<=193;
    eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
    eglDestroyContext(display,context);eglDestroySurface(display,surface);eglTerminate(display);
    if(!ok)return 10;
    puts("LEO_GRAPHICS_PROBE_OK");return 0;
}
