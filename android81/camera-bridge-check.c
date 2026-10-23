/* Local fake-vendor contract test. No device nodes, service or camera hardware. */
#include <hardware/camera.h>
#include <hardware/camera_common.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <dlfcn.h>
#ifdef FAKE_VENDOR
int leo_test_memory_ok;
static int close_fake(hw_device_t *d){free(d);return 0;}
static void callbacks(camera_device_t *d,camera_notify_callback n,camera_data_callback data,camera_data_timestamp_callback t,camera_request_memory mem,void *cookie){
 (void)d;(void)n;(void)data;(void)t;(void)cookie;
 camera_memory_t *m=mem(-1,128,2,(void*)0x1234);
 leo_test_memory_ok=m&&m->size==256;
}
static camera_device_ops_t ops={.set_callbacks=callbacks};
static int open_fake(const hw_module_t *m,const char *id,hw_device_t **out){(void)m;(void)id;camera_device_t *d=calloc(1,sizeof(*d));if(!d)return -ENOMEM;d->common.close=close_fake;d->ops=&ops;*out=&d->common;return 0;}
static int count(void){return 1;}
static int info(int id,struct camera_info *out){if(id)return -EINVAL;out->facing=0;out->orientation=270;return 0;}
static hw_module_methods_t methods={.open=open_fake};
camera_module_t HAL_MODULE_INFO_SYM={.common={.module_api_version=CAMERA_MODULE_API_VERSION_1_0,.id=CAMERA_HARDWARE_MODULE_ID,.methods=&methods},.get_number_of_cameras=count,.get_camera_info=info};
#else
static int checks,allocations;
#define CHECK(x) do {if(!(x)){fprintf(stderr,"FAIL line %d\n",__LINE__);return 1;}checks++;}while(0)
static camera_memory_t memory;
static camera_memory_t *allocate(int fd,size_t size,unsigned count,void *cookie){if(fd!=-1||size!=128||count!=2||cookie!=(void*)0xcafe)return NULL;allocations++;memory.size=size*count;return &memory;}
int main(int argc,char **argv){
 CHECK(argc==3);setenv("LEO_CAMERA_ROOT",argv[2],1);
 void *handle=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);CHECK(handle!=NULL);
 camera_module_t *module=dlsym(handle,HAL_MODULE_INFO_SYM_AS_STR);CHECK(module!=NULL);CHECK(module->get_number_of_cameras()==1);
 struct camera_info info;CHECK(module->get_camera_info(0,&info)==0&&info.orientation==270);
 for(int i=0;i<2;i++){
  hw_device_t *d=NULL,*duplicate=NULL;CHECK(module->common.methods->open(&module->common,"0",&d)==0&&d!=NULL);
  CHECK(module->common.methods->open(&module->common,"0",&duplicate)==-EBUSY);
  ((camera_device_t*)d)->ops->set_callbacks((camera_device_t*)d,NULL,NULL,NULL,allocate,(void*)0xcafe);
  CHECK(allocations==i+1);CHECK(d->close(d)==0);
 }
 printf("PASS %d camera HAL bridge checks; fake vendor, no hardware\n",checks);return 0;
}
#endif
