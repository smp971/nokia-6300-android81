/* HAL1 adapter for the single BF20A1 camera on Leo.
 * Stock QCameraStreamMemory passes its own this pointer to get_memory, whereas
 * Oreo's HIDL adapter requires the cookie supplied to set_callbacks. Restore
 * that cookie without changing the memory layout or the vendor allocation. */
#include <hardware/camera.h>
#include <hardware/camera_common.h>
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
static camera_module_t *vendor;
static pthread_once_t once=PTHREAD_ONCE_INIT;
static pthread_mutex_t lock=PTHREAD_MUTEX_INITIALIZER;
static camera_device_t *active;
static camera_device_ops_t original_ops,bridge_ops;
static int (*original_close)(hw_device_t*);
static camera_request_memory memory_callback;
static void *memory_cookie;
static void load_vendor(void){
 const char *base=getenv("LEO_CAMERA_ROOT");if(!base)base="/vendor/leo-camera";
 char path[256];int n=snprintf(path,sizeof(path),"%s/lib/camera.msm8909.so",base);
 if(n<0||(size_t)n>=sizeof(path))return;
 void *handle=dlopen(path,RTLD_NOW|RTLD_LOCAL);if(!handle){fprintf(stderr,"Leo camera vendor load: %s\n",dlerror());return;}
 camera_module_t *module=dlsym(handle,HAL_MODULE_INFO_SYM_AS_STR);
 if(!module||!module->common.id||strcmp(module->common.id,CAMERA_HARDWARE_MODULE_ID)||module->common.module_api_version!=CAMERA_MODULE_API_VERSION_1_0)return;
 vendor=module;
}
static camera_memory_t *get_memory(int fd,size_t size,unsigned count,void *vendor_cookie){
 (void)vendor_cookie;pthread_mutex_lock(&lock);camera_request_memory cb=memory_callback;void *cookie=memory_cookie;pthread_mutex_unlock(&lock);
 return cb?cb(fd,size,count,cookie):NULL;
}
static void set_callbacks(camera_device_t *device,camera_notify_callback notify,camera_data_callback data,camera_data_timestamp_callback timestamp,camera_request_memory memory,void *cookie){
 pthread_mutex_lock(&lock);memory_callback=memory;memory_cookie=cookie;pthread_mutex_unlock(&lock);
 original_ops.set_callbacks(device,notify,data,timestamp,get_memory,cookie);
}
static int close_device(hw_device_t *device){
 int result=original_close(device);
 pthread_mutex_lock(&lock);active=NULL;memory_callback=NULL;memory_cookie=NULL;pthread_mutex_unlock(&lock);
 return result;
}
static int open_device(const hw_module_t *module,const char *id,hw_device_t **out){
 (void)module;pthread_once(&once,load_vendor);if(!vendor)return -ENODEV;
 pthread_mutex_lock(&lock);if(active){pthread_mutex_unlock(&lock);return -EBUSY;}
 hw_device_t *device=NULL;int result=vendor->common.methods->open(&vendor->common,id,&device);
 if(result==0&&device){camera_device_t *camera=(camera_device_t*)device;
  if(!camera->ops||!camera->ops->set_callbacks||!device->close){pthread_mutex_unlock(&lock);if(device->close)device->close(device);return -EINVAL;}
  original_ops=*camera->ops;bridge_ops=original_ops;original_close=device->close;
  bridge_ops.set_callbacks=set_callbacks;camera->ops=&bridge_ops;device->close=close_device;active=camera;*out=device;
 }
 pthread_mutex_unlock(&lock);return result;
}
static int count(void){pthread_once(&once,load_vendor);return vendor?vendor->get_number_of_cameras():0;}
static int info(int id,struct camera_info *out){pthread_once(&once,load_vendor);if(!vendor)return -ENODEV;memset(out,0,sizeof(*out));return vendor->get_camera_info(id,out);}
static hw_module_methods_t methods={.open=open_device};
camera_module_t HAL_MODULE_INFO_SYM={
 .common={.tag=HARDWARE_MODULE_TAG,.module_api_version=CAMERA_MODULE_API_VERSION_1_0,.hal_api_version=HARDWARE_HAL_API_VERSION,.id=CAMERA_HARDWARE_MODULE_ID,.name="Leo BF20A1 HAL1 cookie bridge",.author="Leo project",.methods=&methods},
 .get_number_of_cameras=count,.get_camera_info=info
};
