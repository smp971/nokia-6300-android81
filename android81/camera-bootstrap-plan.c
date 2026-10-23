/* Stock bootstrap diagnosis. Sensor ioctls are refused by default by the exported
 * wrapper. Only MEDIA_IOC_DEVICE_INFO and MEDIA_IOC_ENUM_ENTITIES are forwarded.
 * --probe-sensors additionally permits only sensor-init CFG_SINIT_PROBE (0).
 * --enumerate-ready signals DONE after the runner validates the registered sensor.
 * WAIT is never forwarded: after DONE it is satisfied locally. No camera is opened.
 * Sensor state can change until reboot.
 * No original permissions, system libraries or services are changed. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <string.h>
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
#include <sys/prctl.h>
#include <time.h>
#include <unistd.h>

struct module_prefix {
    uint32_t tag;uint16_t module_api,hal_api;
    const char *id,*name,*author;void *methods;
};
static int expose_devices;
/* Bootstrap-only ioctl allowlist; never forward the blocking WAIT operation. */
#include <stdarg.h>
#include <fcntl.h>
#include <sys/syscall.h>
static unsigned refused,probed,accepted;
static int probe_sensors,enumerate_ready,probe_done;
int ioctl(int fd, int request, ...) {
    va_list args;va_start(args,request);void *arg=va_arg(args,void*);va_end(args);
    unsigned cmd=(unsigned)request;
    if(cmd==0xc1007c00U||cmd==0xc1007c01U)
        return (int)syscall(__NR_ioctl,fd,request,arg);
    if(enumerate_ready && probe_done && cmd==0xc0d456caU && arg && *(unsigned*)arg==2U) {
        printf("WAIT_SKIPPED bootstrap already completed\n");fflush(stdout);return 0;
    }
    if(probe_sensors && cmd==0xc0d456caU && arg && *(unsigned*)arg==0U) {
        const char *name=*(const char **)((unsigned char*)arg+4);
        printf("SENSOR_PROBE_BEGIN name=%.31s\n",name?name:"unknown");fflush(stdout);probed++;
        int result=(int)syscall(__NR_ioctl,fd,request,arg);int saved=errno;
        if(result>=0)accepted++;
        printf("SENSOR_PROBE_RETURN=%d errno=%d\n",result,result<0?saved:0);
        fflush(stdout);errno=saved;return result;
    }
    printf("REFUSED_IOCTL request=0x%08x cfgtype=%u\n",cmd,arg?*(unsigned*)arg:~0U);
    fflush(stdout);refused++;errno=EPERM;return -1;
}
struct node {const char *path;dev_t device;};
static int child_probe(void){
    alarm(12);
    struct rlimit memory={128U*1024U*1024U,128U*1024U*1024U};
    struct rlimit cpu={8,8};
    if(setrlimit(RLIMIT_AS,&memory)||setrlimit(RLIMIT_CPU,&cpu))return 10;
    if(unshare(CLONE_NEWNS|CLONE_NEWNET))return 11;
    if(mount(NULL,"/",NULL,MS_REC|MS_PRIVATE,NULL))return 12;
    struct node nodes[40];size_t count=0;
    if(expose_devices){
        const char *fixed[]={"/dev/media0","/dev/media1","/dev/video0","/dev/video1","/dev/video32","/dev/video33"};
        for(size_t i=0;i<sizeof(fixed)/sizeof(fixed[0]);i++){
            struct stat s;if(!stat(fixed[i],&s)&&S_ISCHR(s.st_mode))nodes[count++]=(struct node){fixed[i],s.st_rdev};
        }
        static char paths[32][32];
        for(int i=0;i<32;i++){
            snprintf(paths[i],sizeof(paths[i]),"/dev/v4l-subdev%d",i);
            struct stat s;if(!stat(paths[i],&s)&&S_ISCHR(s.st_mode))nodes[count++]=(struct node){paths[i],s.st_rdev};
        }
    }
    if(mount("tmpfs","/dev","tmpfs",MS_NOSUID,"mode=0755,size=64k"))return 13;
    /* No binder, ion, GPU, audio or modem. No external daemon socket. */
    if(mknod("/dev/null",S_IFCHR|0666,makedev(1,3))||
       mknod("/dev/zero",S_IFCHR|0666,makedev(1,5))||
       mknod("/dev/urandom",S_IFCHR|0666,makedev(1,9)))return 14;
    if(mkdir("/dev/socket",0755))return 16;
    for(size_t i=0;i<count;i++)if(mknod(nodes[i].path,S_IFCHR|0660,nodes[i].device)||chown(nodes[i].path,1006,1006))return 17;
    gid_t camera_group=1006;
    if(setgroups(expose_devices?1:0,expose_devices?&camera_group:NULL)||setgid(expose_devices?1006:9999)||setuid(expose_devices?1006:9999))return 15;
    printf("SANDBOX uid=%u camera_nodes=%u network=isolated\n",(unsigned)getuid(),(unsigned)count);
    fflush(stdout);
    if(enumerate_ready) {
        int fd=open("/dev/v4l-subdev5",O_RDWR);if(fd<0)return 25;
        unsigned char cfg[212]={0};*(unsigned*)cfg=1;
        int done=(int)syscall(__NR_ioctl,fd,0xc0d456caU,cfg);close(fd);
        printf("PROBE_DONE_RETURN=%d\n",done);fflush(stdout);if(done<0)return 26;
        probe_done=1;
        void *interface=dlopen("/data/local/tmp/leo-camera-probe/libmmcamera_interface.so",RTLD_NOW|RTLD_LOCAL);
        if(!interface){printf("DLOPEN_ERROR %s\n",dlerror());return 20;}
        unsigned char (*count)(void)=dlsym(interface,"get_num_of_cameras");
        if(!count)return 21;
        unsigned cameras=count();printf("PRIVATE_CAMERA_COUNT=%u\n",cameras);
        if(!cameras||cameras>8)return 27;
        void *hal=dlopen("/data/local/tmp/leo-camera-probe/camera.msm8909.so",RTLD_NOW|RTLD_LOCAL);
        if(!hal){printf("HAL_DLOPEN_ERROR %s\n",dlerror());return 28;}
        int (*hal_count)(void)=dlsym(hal,"get_number_of_cameras");if(!hal_count)return 29;
        int hal_cameras=hal_count();printf("PRIVATE_HAL_CAMERA_COUNT=%d\n",hal_cameras);
        return hal_cameras>0&&hal_cameras<=8?0:30;
    }
    void *handle=dlopen("/data/local/tmp/leo-camera-probe/libmmcamera2_sensor_modules.so",RTLD_NOW|RTLD_LOCAL);
    if(!handle){printf("DLOPEN_ERROR %s\n",dlerror());return 20;}
    int (*bootstrap)(void*)=dlsym(handle,"sensor_init_probe");
    if(!bootstrap){printf("BOOTSTRAP_SYMBOL_MISSING\n");return 21;}
    /* Audited stock module allocates a zeroed 964-byte bundle. Use extra room. */
    unsigned char bundle[4096]={0};
    printf("BOOTSTRAP_MODE=%s wait/done and non-media ioctls forbidden\n",probe_sensors?"probe-sensors":"plan");fflush(stdout);
    int result=bootstrap(bundle);
    printf("BOOTSTRAP_RETURN=%d REFUSED_IOCTLS=%u PROBED=%u ACCEPTED=%u\n",result,refused,probed,accepted);
    /* Never call module_sensor_init, camera count/open, preview, or capture. */
    return (refused||probed)?0:24;
}
int main(int argc,char **argv){
    if(argc!=2)return 2;
    if(!strcmp(argv[1],"--probe-sensors"))probe_sensors=1;else if(!strcmp(argv[1],"--enumerate-ready"))enumerate_ready=1;else if(strcmp(argv[1],"--plan"))return 2;
    expose_devices=1;
    fflush(NULL);pid_t child=fork();if(child<0)return 2;
    if(child==0){setpgid(0,0);prctl(PR_SET_PDEATHSIG,SIGKILL);int result=child_probe();fflush(NULL);_exit(result);}
    int status;struct timespec started,now;clock_gettime(CLOCK_MONOTONIC,&started);
    for(;;){
        pid_t done=waitpid(child,&status,WNOHANG);if(done==child)break;if(done<0&&errno!=EINTR)return 3;
        clock_gettime(CLOCK_MONOTONIC,&now);
        if(now.tv_sec-started.tv_sec>=15){
            kill(-child,SIGKILL);kill(child,SIGKILL);
            printf("PROBE_WATCHDOG_TIMEOUT=15\n");
            for(int n=0;n<10;n++){if(waitpid(child,&status,WNOHANG)==child)return 124;usleep(100000);}
            printf("PROBE_CHILD_NOT_REAPED\n");return 125;
        }
        usleep(100000);
    }
    if(WIFSIGNALED(status)){printf("PROBE_CHILD_SIGNAL=%d\n",WTERMSIG(status));return 4;}
    printf("PROBE_CHILD_EXIT=%d\n",WEXITSTATUS(status));return WEXITSTATUS(status);
}
