#define LOG_TAG "LeoAudioBridge"
#include <hardware/hardware.h>
#include <cutils/properties.h>
#include <log/log.h>
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
static const char *base="/vendor/leo-audio/lib/";
static int load_open(const char *file,const char *name,hw_device_t **out){
    char path[256];snprintf(path,sizeof(path),"%s%s",base,file);
    void *lib=dlopen(path,RTLD_NOW|RTLD_GLOBAL);
    if(!lib){ALOGE("%s: %s",path,dlerror());return -ENOENT;}
    hw_module_t *m=(hw_module_t*)dlsym(lib,HAL_MODULE_INFO_SYM_AS_STR);
    if(!m||!m->methods||!m->methods->open)return -EINVAL;
    return m->methods->open(m,name,out);
}
static int open_audio(const hw_module_t *unused,const char *name,hw_device_t **out){
    (void)unused;
    char card[64]={0};FILE *f=fopen("/proc/asound/card0/id","r");
    if(f){fgets(card,sizeof(card),f);fclose(f);card[strcspn(card,"\r\n")]=0;}
    if(property_get_bool("persist.leo.audio.enabled",0)&&!strcmp(card,"msm8909sndcard")){
        const char *deps[]={"libmdmdetect.so","libperipheral_client.so","libdiag.so",
            "libtinycompress.so","libaudioroute.so","libaudcal.so","libacdbrtac.so",
            "libadiertac.so","libacdb-fts.so","libacdbloader.so"};
        int ready=1;
        for(unsigned i=0;i<sizeof(deps)/sizeof(deps[0]);i++){
            char path[256];snprintf(path,sizeof(path),"%s%s",base,deps[i]);
            if(!dlopen(path,RTLD_NOW|RTLD_GLOBAL)){ALOGE("Preload %s: %s",path,dlerror());ready=0;break;}
        }
        if(ready){int rc=load_open("audio.primary.msm8909.so",name,out);
            if(!rc){ALOGI("LEO_AUDIO_QCOM_ACTIVE");return 0;}
            ALOGE("Stock HAL open failed: %d",rc);
        }
    }
    ALOGW("LEO_AUDIO_GENERIC_FALLBACK");
    return load_open("audio-primary-generic.so",name,out);
}
static hw_module_methods_t methods={.open=open_audio};
hw_module_t HAL_MODULE_INFO_SYM={.tag=HARDWARE_MODULE_TAG,.module_api_version=1,
    .hal_api_version=0,.id="audio",.name="Leo stock Qualcomm audio with generic fallback",
    .author="Leo bring-up",.methods=&methods};
