/* Process-local legacy FM compatibility and tracing. Never preload into other services. */
#define _GNU_SOURCE
#include <stdio.h>
#include <string.h>
#include <stdarg.h>
#include <fcntl.h>
#include <errno.h>
#include <dlfcn.h>
#include <sys/ioctl.h>
#include <linux/videodev2.h>
int open(const char *path,int flags,...){static int(*original)(const char*,int,...);if(!original)original=dlsym(RTLD_NEXT,"open");unsigned mode=0;if(flags&O_CREAT){va_list args;va_start(args,flags);mode=va_arg(args,unsigned);va_end(args);}int result=original(path,flags,mode);int error=errno;fprintf(stderr,"TRACE open path=%s flags=0x%x result=%d errno=%d\n",path,flags,result,result<0?error:0);errno=error;return result;}
int property_get(const char *key,char *value,const char *fallback){static int(*original)(const char*,char*,const char*);if(!original)original=dlsym(RTLD_NEXT,"property_get");int result=original(key,value,fallback);if(!result&&!strcmp(key,"ro.qualcomm.bt.hci_transport")){strcpy(value,"smd");result=3;fprintf(stderr,"COMPAT missing FM transport property -> smd (process-local)\n");}int error=errno;fprintf(stderr,"TRACE property key=%s value=%s result=%d\n",key,value,result);errno=error;return result;}
int ioctl(int fd,int request,...){static int(*original)(int,int,...);if(!original)original=dlsym(RTLD_NEXT,"ioctl");va_list args;va_start(args,request);void *arg=va_arg(args,void*);va_end(args);int result=original(fd,request,arg);int error=errno;if(arg&&(request==(int)VIDIOC_S_CTRL||request==(int)VIDIOC_G_CTRL)){struct v4l2_control *control=arg;fprintf(stderr,"TRACE ioctl fd=%d request=0x%x control=0x%x value=%d result=%d errno=%d\n",fd,request,control->id,control->value,result,result<0?error:0);}else fprintf(stderr,"TRACE ioctl fd=%d request=0x%x result=%d errno=%d\n",fd,request,result,result<0?error:0);errno=error;return result;}
