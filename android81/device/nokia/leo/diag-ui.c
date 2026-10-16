/* Native display/keypad diagnostic. Not an Android launcher or framework UI. */
#include <errno.h>
#include <fcntl.h>
#include <linux/fb.h>
#include <linux/input.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>

/* Layout verified against msm8x09 kernel-headers/linux/msm_mdp.h. */
struct leo_rect { uint32_t x,y,w,h; };
struct leo_commit {uint32_t flags,wait_for_finish;struct fb_var_screeninfo var;struct leo_rect l_roi,r_roi;};
#define LEO_MDP_COMMIT _IOW('m',164,struct leo_commit)
static struct fb_var_screeninfo v;
static struct fb_fix_screeninfo f;
static uint8_t *pixels;
static volatile sig_atomic_t running=1;
static void stop(int sig){(void)sig;running=0;}
static const char chars[]="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
static const uint8_t font[][7]={
 {14,17,17,31,17,17,17},{30,17,17,30,17,17,30},{14,17,16,16,16,17,14},
 {30,17,17,17,17,17,30},{31,16,16,30,16,16,31},{31,16,16,30,16,16,16},
 {14,17,16,23,17,17,15},{17,17,17,31,17,17,17},{14,4,4,4,4,4,14},
 {7,2,2,2,2,18,12},{17,18,20,24,20,18,17},{16,16,16,16,16,16,31},
 {17,27,21,21,17,17,17},{17,25,21,19,17,17,17},{14,17,17,17,17,17,14},
 {30,17,17,30,16,16,16},{14,17,17,17,21,18,13},{30,17,17,30,20,18,17},
 {15,16,16,14,1,1,30},{31,4,4,4,4,4,4},{17,17,17,17,17,17,14},
 {17,17,17,17,17,10,4},{17,17,17,21,21,21,10},{17,17,10,4,10,17,17},
 {17,17,10,4,4,4,4},{31,1,2,4,8,16,31},{14,17,19,21,25,17,14},
 {4,12,4,4,4,4,14},{14,17,1,2,4,8,31},{30,1,1,14,1,1,30},
 {2,6,10,18,31,2,2},{31,16,16,30,1,1,30},{14,16,16,30,17,17,14},
 {31,1,2,4,8,8,8},{14,17,17,14,17,17,14},{14,17,17,15,1,1,14}};
static uint32_t channel(unsigned n,struct fb_bitfield b){return ((n*((1U<<b.length)-1)+127)/255)<<b.offset;}
static uint32_t color(unsigned r,unsigned g,unsigned b){return channel(r,v.red)|channel(g,v.green)|channel(b,v.blue);}
static void rect(int x,int y,int w,int h,uint32_t c){
    for(int yy=y;yy<y+h&&yy<(int)v.yres;yy++)for(int xx=x;xx<x+w&&xx<(int)v.xres;xx++){
        if(xx<0||yy<0)continue;
        size_t off=(size_t)(yy+v.yoffset)*f.line_length+(size_t)(xx+v.xoffset)*(v.bits_per_pixel/8);
        if(off+v.bits_per_pixel/8>f.smem_len)continue;
        if(v.bits_per_pixel==16)*(uint16_t*)(pixels+off)=(uint16_t)c;else *(uint32_t*)(pixels+off)=c;
    }
}
static void text(int x,int y,const char *s,int scale,uint32_t c){
    for(;*s;s++,x+=6*scale){
        const char *p=strchr(chars,*s);if(p){
            size_t n=(size_t)(p-chars);
            for(int yy=0;yy<7;yy++)for(int xx=0;xx<5;xx++)if(font[n][yy]&(1<<(4-xx)))rect(x+xx*scale,y+yy*scale,scale,scale,c);
        }else if(*s=='.')rect(x+2*scale,y+6*scale,scale,scale,c);
    }
}
static void paint(int fb,int selected,int page,int key){
    uint32_t white=color(235,243,250),muted=color(138,158,184),blue=color(31,90,170);
    rect(0,0,(int)v.xres,(int)v.yres,color(12,20,34));
    rect(0,0,(int)v.xres,48,color(21,40,67));text(12,16,"ANDROID 8.1",2,white);
    text(12,61,"NOKIA 6300",1,muted);text(12,79,"NATIVE UI TEST",1,muted);
    if(page<0){
        const char *items[]={"DISPLAY","KEYPAD","DEVICE INFO"};
        for(int i=0;i<3;i++){
            int y=107+i*43;rect(10,y,220,34,i==selected?blue:color(23,34,51));
            text(20,y+10,items[i],2,white);
        }
        text(12,250,"UP DOWN SELECT",1,muted);text(12,267,"OK OPEN",1,muted);
    }else if(page==0){
        text(12,110,"DISPLAY READY",2,white);
        rect(12,145,72,70,color(255,0,0));rect(84,145,72,70,color(0,255,0));rect(156,145,72,70,color(0,0,255));
        rect(12,223,216,16,color(255,255,255));text(12,257,"RGB565 FRAMEBUFFER",1,muted);
    }else if(page==1){
        text(12,110,"PRESS ANY KEY",2,white);
        char line[40];snprintf(line,sizeof(line),"KEY CODE %d",key);text(12,155,line,2,white);
        text(12,210,"ARROWS OK SOFTKEYS",1,muted);
    }else{
        text(12,110,"DEVICE INFO",2,white);
        char line[40];snprintf(line,sizeof(line),"SCREEN %u X %u",v.xres,v.yres);text(12,151,line,1,white);
        snprintf(line,sizeof(line),"PIXEL DEPTH %u",v.bits_per_pixel);text(12,176,line,1,white);
        text(12,201,"ANDROID INIT ACTIVE",1,white);text(12,226,"ADB ROOT ACTIVE",1,white);
    }
    rect(0,291,240,29,color(21,40,67));text(12,302,page<0?"END EXIT":"RIGHT SOFTKEY BACK",1,white);
    struct fb_var_screeninfo pan=v;pan.activate=FB_ACTIVATE_VBL|FB_ACTIVATE_FORCE;
    int rc=ioctl(fb,FBIOPAN_DISPLAY,&pan);printf("pan=%d errno=%d\n",rc,rc<0?errno:0);
    struct leo_commit commit;memset(&commit,0,sizeof(commit));commit.var=pan;commit.wait_for_finish=1;
    commit.l_roi.w=v.xres;commit.l_roi.h=v.yres;
    rc=ioctl(fb,LEO_MDP_COMMIT,&commit);printf("mdp_commit=%d errno=%d\n",rc,rc<0?errno:0);fflush(stdout);
}
int main(int argc,char **argv){
    if(argc!=3){fprintf(stderr,"usage: leo_diag_ui FRAMEBUFFER INPUT_EVENT\n");return 2;}
    int fb=open(argv[1],O_RDWR|O_CLOEXEC);if(fb<0){perror("framebuffer open");return 3;}
    if(ioctl(fb,FBIOGET_VSCREENINFO,&v)||ioctl(fb,FBIOGET_FSCREENINFO,&f)){perror("framebuffer info");return 4;}
    printf("fb=%ux%u virtual=%ux%u bpp=%u pitch=%u bytes=%u offset=%u\n",v.xres,v.yres,v.xres_virtual,v.yres_virtual,v.bits_per_pixel,f.line_length,f.smem_len,v.yoffset);
    if(v.xres!=240||v.yres!=320||(v.bits_per_pixel!=16&&v.bits_per_pixel!=32)||!f.smem_len||f.smem_len>8388608)return 5;
    pixels=mmap(NULL,f.smem_len,PROT_READ|PROT_WRITE,MAP_SHARED,fb,0);if(pixels==MAP_FAILED){perror("framebuffer mmap");return 6;}
    uint8_t *saved=malloc(f.smem_len);if(!saved)return 7;memcpy(saved,pixels,f.smem_len);
    int input=open(argv[2],O_RDONLY|O_NONBLOCK|O_CLOEXEC);if(input<0){perror("input open");return 8;}
    signal(SIGTERM,stop);signal(SIGINT,stop);ioctl(fb,FBIOBLANK,FB_BLANK_UNBLANK);
    int selected=0,page=-1,last=0;paint(fb,selected,page,last);
    while(running){
        struct pollfd p={input,POLLIN,0};if(poll(&p,1,500)<=0)continue;
        struct input_event event;
        while(read(input,&event,sizeof(event))==(ssize_t)sizeof(event)){
            if(event.type!=EV_KEY||event.value!=1)continue;
            last=event.code;printf("key=%d\n",last);
            if(last==107){running=0;break;}
            if(last==158)page=-1;
            else if(page<0){
                if(last==103)selected=(selected+2)%3;
                if(last==108)selected=(selected+1)%3;
                if(last==352||last==28)page=selected;
            }
            paint(fb,selected,page,last);
        }
    }
    memcpy(pixels,saved,f.smem_len);struct fb_var_screeninfo pan=v;pan.activate=FB_ACTIVATE_FORCE;
    ioctl(fb,FBIOPAN_DISPLAY,&pan);free(saved);munmap(pixels,f.smem_len);close(input);close(fb);return 0;
}
