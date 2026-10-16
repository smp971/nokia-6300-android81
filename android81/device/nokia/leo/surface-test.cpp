/* Actual SurfaceFlinger client: a visible moving test surface, no framebuffer IO. */
#include <gui/Surface.h>
#include <gui/ISurfaceComposer.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>
#include <ui/DisplayInfo.h>
#include <utils/String8.h>
#include <stdio.h>
#include <stdint.h>
#include <unistd.h>
using namespace android;
int main(){
    setbuf(stdout,nullptr);alarm(150);
    sp<SurfaceComposerClient> client=new SurfaceComposerClient();
    if(client->initCheck()!=NO_ERROR){puts("composer client failed");return 2;}
    DisplayInfo info;
    if(SurfaceComposerClient::getDisplayInfo(SurfaceComposerClient::getBuiltInDisplay(ISurfaceComposer::eDisplayIdMain),&info)!=NO_ERROR)return 3;
    printf("DISPLAY %ux%u fps=%.2f\n",info.w,info.h,info.fps);
    sp<SurfaceControl> control=client->createSurface(String8("Leo SurfaceFlinger test"),info.w,info.h,PIXEL_FORMAT_RGBA_8888,0);
    if(control.get()==nullptr||!control->isValid())return 4;
    SurfaceComposerClient::openGlobalTransaction();control->setLayer(100000);control->show();SurfaceComposerClient::closeGlobalTransaction(true);
    sp<Surface> surface=control->getSurface();
    for(unsigned frame=0;frame<600;frame++){
        ANativeWindow_Buffer b;
        status_t rc=surface->lock(&b,nullptr);if(rc!=NO_ERROR){printf("lock=%d\n",rc);return 5;}
        for(int y=0;y<b.height;y++)for(int x=0;x<b.width;x++){
            uint32_t color=x<b.width/3?0xff2020e0U:x<2*b.width/3?0xff20d020U:0xffe06020U;
            if(y<42||y>b.height-40)color=0xff302010U;
            int bx=static_cast<int>((frame*3)%static_cast<unsigned>(b.width-32));
            if(x>=bx&&x<bx+32&&y>130&&y<162)color=0xffffffffU;
            static_cast<uint32_t*>(b.bits)[y*b.stride+x]=color;
        }
        rc=surface->unlockAndPost();if(rc!=NO_ERROR){printf("post=%d\n",rc);return 6;}
        if(frame%50==0)printf("SURFACE_FRAME %u\n",frame);
        usleep(100000);
    }
    puts("LEO_SURFACE_TEST_OK");return 0;
}
