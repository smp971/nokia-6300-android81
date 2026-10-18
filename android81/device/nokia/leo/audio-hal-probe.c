/* Open the original audio HAL privately; never selects it for AudioFlinger. */
#include <dlfcn.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <hardware/audio.h>
static unsigned le16(const unsigned char *p){return p[0]|((unsigned)p[1]<<8);}
static unsigned le32(const unsigned char *p){return le16(p)|(le16(p+2)<<16);}
static int play(audio_hw_device_t *dev,const char *path){
    FILE *f=fopen(path,"rb");unsigned char header[44];
    if(!f)return 1;
    if(fread(header,1,sizeof(header),f)!=sizeof(header)||memcmp(header,"RIFF",4)||
       memcmp(header+8,"WAVEfmt ",8)||le32(header+16)!=16||le16(header+20)!=1||
       le16(header+22)!=2||le32(header+24)!=48000||le16(header+34)!=16||memcmp(header+36,"data",4)){
        fprintf(stderr,"Expected generated 48 kHz stereo PCM16 test WAV\n");fclose(f);return 1;
    }
    struct audio_config config=AUDIO_CONFIG_INITIALIZER;
    config.sample_rate=48000;config.channel_mask=AUDIO_CHANNEL_OUT_STEREO;config.format=AUDIO_FORMAT_PCM_16_BIT;
    struct audio_stream_out *out=NULL;
    int rc=dev->open_output_stream(dev,1,AUDIO_DEVICE_OUT_SPEAKER,AUDIO_OUTPUT_FLAG_PRIMARY,&config,&out,"");
    printf("output_open=%d rate=%u channels=0x%x\n",rc,config.sample_rate,config.channel_mask);fflush(stdout);
    if(rc||!out){fclose(f);return 1;}
    unsigned remaining=le32(header+40),total=0;unsigned char data[4096];
    while(remaining){
        size_t want=remaining<sizeof(data)?remaining:sizeof(data),got=fread(data,1,want,f);
        if(!got){rc=-1;break;}
        ssize_t written=out->write(out,data,got);
        if(written<=0||(size_t)written>got){fprintf(stderr,"write=%zd\n",written);rc=-1;break;}
        if((size_t)written!=got){fprintf(stderr,"Partial write unsupported by this probe\n");rc=-1;break;}
        total+=(unsigned)written;remaining-=(unsigned)written;
    }
    printf("pcm_bytes_written=%u remaining=%u\n",total,remaining);fflush(stdout);
    out->common.standby(&out->common);dev->close_output_stream(dev,out);fclose(f);return rc?1:0;
}
int main(int argc,char **argv){
    if(argc!=2 && !(argc==4&&!strcmp(argv[2],"--play"))){fprintf(stderr,"Usage: %s HAL.so [--play test.wav]\n",argv[0]);return 2;}
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
    if(!rc&&argc==4)rc=play(dev,argv[3]);
    raw->close(raw);dlclose(lib);return rc?1:0;
}
