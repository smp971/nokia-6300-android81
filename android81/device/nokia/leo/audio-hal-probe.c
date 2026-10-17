/* Open the original audio HAL privately; never selects it for AudioFlinger. */
#include <dlfcn.h>
#include <stdio.h>
#include <hardware/audio.h>
int main(int argc,char **argv){
    if(argc!=2){fprintf(stderr,"Usage: %s /path/audio.primary.msm8909.so\n",argv[0]);return 2;}
    void *lib=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);
    if(!lib){fprintf(stderr,"dlopen: %s\n",dlerror());return 1;}
    hw_module_t *m=(hw_module_t*)dlsym(lib,HAL_MODULE_INFO_SYM_AS_STR);
    if(!m||!m->methods||!m->methods->open){fprintf(stderr,"Missing audio module interface\n");return 1;}
    printf("module=%s api=0x%x\n",m->name,m->module_api_version);fflush(stdout);
    hw_device_t *raw=0;int rc=m->methods->open(m,AUDIO_HARDWARE_INTERFACE,&raw);
    printf("open=%d device=%p\n",rc,(void*)raw);fflush(stdout);
    if(rc||!raw)return 1;
    audio_hw_device_t *dev=(audio_hw_device_t*)raw;
    rc=dev->init_check(dev);printf("device_api=0x%x init_check=%d\n",raw->version,rc);fflush(stdout);
    raw->close(raw);dlclose(lib);return rc?1:0;
}
