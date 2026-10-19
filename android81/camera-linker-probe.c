/* Linker-only camera probe. Hardware stays hidden; no camera methods called. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <grp.h>
#include <sched.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/mount.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

struct module_prefix {
    uint32_t tag;uint16_t module_api,hal_api;
    const char *id,*name,*author;void *methods;
};
static int child_probe(void){
    alarm(12);
    struct rlimit memory={128U*1024U*1024U,128U*1024U*1024U};
    struct rlimit cpu={8,8};
    if(setrlimit(RLIMIT_AS,&memory)||setrlimit(RLIMIT_CPU,&cpu))return 10;
    if(unshare(CLONE_NEWNS|CLONE_NEWNET))return 11;
    if(mount(NULL,"/",NULL,MS_REC|MS_PRIVATE,NULL))return 12;
    if(mount("tmpfs","/dev","tmpfs",MS_NOSUID,"mode=0755,size=64k"))return 13;
    /* Only harmless character devices. No binder, video, ion, GPU or sockets. */
    if(mknod("/dev/null",S_IFCHR|0666,makedev(1,3))||
       mknod("/dev/zero",S_IFCHR|0666,makedev(1,5))||
       mknod("/dev/urandom",S_IFCHR|0666,makedev(1,9)))return 14;
    if(setgroups(0,NULL)||setgid(9999)||setuid(9999))return 15;
    printf("SANDBOX uid=%u devices=hidden network=isolated\n",(unsigned)getuid());
    fflush(stdout);
    void *handle=dlopen("/data/local/tmp/leo-camera-probe/camera.msm8909.so",RTLD_NOW|RTLD_LOCAL);
    if(!handle){printf("DLOPEN_ERROR %s\n",dlerror());return 20;}
    const struct module_prefix *module=dlsym(handle,"HMI");
    if(!module){printf("MODULE_SYMBOL_MISSING\n");return 21;}
    printf("DLOPEN_OK module=%s name=%s module_api=0x%x hal_api=0x%x\n",
           module->id,module->name,module->module_api,module->hal_api);
    /* Do not call get_number_of_cameras, open or daemon entry points. */
    dlclose(handle);return 0;
}
int main(void){
    fflush(NULL);pid_t child=fork();if(child<0)return 2;
    if(child==0){int result=child_probe();fflush(NULL);_exit(result);}
    int status;while(waitpid(child,&status,0)<0)if(errno!=EINTR)return 3;
    if(WIFSIGNALED(status)){printf("PROBE_CHILD_SIGNAL=%d\n",WTERMSIG(status));return 4;}
    printf("PROBE_CHILD_EXIT=%d\n",WEXITSTATUS(status));return WEXITSTATUS(status);
}
