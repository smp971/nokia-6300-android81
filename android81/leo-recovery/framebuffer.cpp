// SPDX-License-Identifier: Apache-2.0
// Nokia's QPIC framebuffer is RGB565. AOSP Oreo minui draws 32-bit pixels;
// keep its font decoder only and provide a bounded native 16-bit renderer.
#include <minui/minui.h>
#include <linux/fb.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <vector>
#include "rgb565.h"

namespace {
int display=-1;fb_var_screeninfo var;fb_fix_screeninfo fix;
unsigned char* memory=nullptr;GRFont font{};
std::vector<uint16_t> canvas;
unsigned red,green,blue,alpha;
uint16_t pixel(unsigned r,unsigned g,unsigned b){return leo::rgb565(r,g,b);}
void point(int x,int y,unsigned opacity) {
    if(x<0||y<0||x>=(int)var.xres||y>=(int)var.yres||!opacity)return;
    auto& dest=canvas[(size_t)y*var.xres+x];
    if(opacity==255){dest=pixel(red,green,blue);return;}
    unsigned r=((dest>>11)&31)*255/31,g=((dest>>5)&63)*255/63,b=(dest&31)*255/31;
    dest=pixel((r*(255-opacity)+red*opacity)/255,(g*(255-opacity)+green*opacity)/255,(b*(255-opacity)+blue*opacity)/255);
}
bool pageFits(unsigned y){return leo::framebufferPageFits(fix.smem_len,fix.line_length,y,var.yres,var.xres);}
void copyPage(unsigned y) {
    for(unsigned row=0;row<var.yres;++row)
        memcpy(memory+(size_t)(row+y)*fix.line_length,canvas.data()+(size_t)row*var.xres,var.xres*2);
}
}
int gr_init() {
    display=open("/dev/graphics/fb0",O_RDWR|O_CLOEXEC);
    if(display<0||ioctl(display,FBIOGET_VSCREENINFO,&var)||ioctl(display,FBIOGET_FSCREENINFO,&fix))return -1;
    printf("Leo framebuffer: %ux%u, %u bpp, pitch=%u, memory=%u, RGB offsets=%u/%u/%u\n",var.xres,var.yres,var.bits_per_pixel,fix.line_length,fix.smem_len,var.red.offset,var.green.offset,var.blue.offset);
    if(var.bits_per_pixel!=16||var.red.offset!=11||var.green.offset!=5||var.blue.offset!=0||
       var.red.length!=5||var.green.length!=6||var.blue.length!=5||var.xoffset||
       var.xres<160||var.yres<200||var.xres>1024||var.yres>1024||fix.line_length<var.xres*2||!pageFits(var.yoffset))return -1;
    void* mapped=mmap(nullptr,fix.smem_len,PROT_READ|PROT_WRITE,MAP_SHARED,display,0);
    if(mapped==MAP_FAILED)return -1;memory=(unsigned char*)mapped;
    if(res_create_alpha_surface("font",&font.texture)||!font.texture||font.texture->pixel_bytes!=1||
       font.texture->width%96||font.texture->height%2)return -1;
    font.char_width=font.texture->width/96;font.char_height=font.texture->height/2;
    if(font.char_width<1||font.char_height<1)return -1;
    canvas.resize((size_t)var.xres*var.yres,0);alpha=255;
    return 0;
}
void gr_exit(){if(memory)munmap(memory,fix.smem_len);memory=nullptr;if(display>=0)close(display);display=-1;if(font.texture)res_free_surface(font.texture);font.texture=nullptr;canvas.clear();}
int gr_fb_width(){return var.xres;}
int gr_fb_height(){return var.yres;}
void gr_fb_blank(bool blank){ioctl(display,FBIOBLANK,blank?FB_BLANK_POWERDOWN:FB_BLANK_UNBLANK);}
void gr_flip() {
    unsigned old=var.yoffset,next=old;
    if(var.yres_virtual>=var.yres*2&&pageFits(var.yres))next=old?0:var.yres;
    copyPage(next);auto pan=var;pan.yoffset=next;pan.activate=FB_ACTIVATE_VBL|FB_ACTIVATE_FORCE;
    if(ioctl(display,FBIOPAN_DISPLAY,&pan)==0)var=pan;
    else {copyPage(old);pan.yoffset=old;ioctl(display,FBIOPAN_DISPLAY,&pan);}
}
const GRFont* gr_sys_font(){return &font;}
void gr_font_size(const GRFont* value,int* x,int* y){*x=value->char_width;*y=value->char_height;}
void gr_color(unsigned char r,unsigned char g,unsigned char b,unsigned char a){red=r;green=g;blue=b;alpha=a;}
void gr_clear(){std::fill(canvas.begin(),canvas.end(),pixel(red,green,blue));}
void gr_fill(int x1,int y1,int x2,int y2) {
    x1=std::max(0,x1);y1=std::max(0,y1);x2=std::min((int)var.xres,x2);y2=std::min((int)var.yres,y2);
    for(int y=y1;y<y2;++y)for(int x=x1;x<x2;++x)point(x,y,alpha);
}
void gr_text(const GRFont* value,int x,int y,const char* text,bool bold) {
    for(;*text;++text,x+=value->char_width) {
        unsigned ch=(unsigned char)*text;if(ch<32||ch>126)ch='?';
        for(int row=0;row<value->char_height;++row)for(int col=0;col<value->char_width;++col) {
            size_t pos=(size_t)(row+(bold?value->char_height:0))*value->texture->row_bytes+(ch-32)*value->char_width+col;
            point(x+col,y+row,value->texture->data[pos]*alpha/255);
        }
    }
}
