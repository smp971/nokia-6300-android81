/* Volatile diagnostic PID 1: open emergency USB ADB before Android init. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/klog.h>
#include <sys/reboot.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/syscall.h>
#include <linux/reboot.h>
#include <unistd.h>

static int cache_ready=0;
static void note(const char *s) {
    int fd=open("/dev/kmsg",O_WRONLY|O_CLOEXEC);
    if(fd>=0){dprintf(fd,"<5>leo-bootstrap: %s\n",s);close(fd);}
    fd=open("/tmp/leo-bootstrap.log",O_WRONLY|O_CREAT|O_APPEND|O_CLOEXEC,0644);
    if(fd>=0){dprintf(fd,"%s\n",s);close(fd);}
    if(cache_ready){
        fd=open("/cache/leo-init-v4-bootstrap.log",O_WRONLY|O_CREAT|O_APPEND|O_CLOEXEC,0644);
        if(fd>=0){dprintf(fd,"%s\n",s);fsync(fd);close(fd);}
    }
}
static void failure(const char *operation){
    int saved=errno;char text[512];
    snprintf(text,sizeof(text),"%s failed: errno=%d (%s)",operation,saved,strerror(saved));
    note(text);
}
static void dir(const char *p,mode_t mode){if(mkdir(p,mode)&&errno!=EEXIST)note(p);}
static int put(const char *p,const char *s){
    int fd=open(p,O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC,0644);
    if(fd<0){failure(p);return -1;}
    ssize_t n=write(fd,s,strlen(s));close(fd);
    if(n!=(ssize_t)strlen(s)){failure(p);return -1;}return 0;
}
static int readtext(const char *path,char *buf,size_t size){
    int fd=open(path,O_RDONLY|O_CLOEXEC);if(fd<0)return -1;
    ssize_t n=read(fd,buf,size-1);close(fd);if(n<0)return -1;buf[n]=0;return 0;
}
static void cache_log_mount(void){
    char info[1024],dev[64],size[64];unsigned major_no=0,minor_no=0;
    if(readtext("/sys/class/block/mmcblk0p32/uevent",info,sizeof(info)) ||
       !strstr(info,"PARTNAME=cache\n") ||
       readtext("/sys/class/block/mmcblk0p32/size",size,sizeof(size)) ||
       strtoul(size,NULL,10)!=409600 ||
       readtext("/sys/class/block/mmcblk0p32/dev",dev,sizeof(dev)) ||
       sscanf(dev,"%u:%u",&major_no,&minor_no)!=2){note("cache identity check failed; no cache mount");return;}
    if(mknod("/dev/leo-cache",S_IFBLK|0600,makedev(major_no,minor_no))){failure("cache mknod");return;}
    dir("/cache",0770);
    if(mount("/dev/leo-cache","/cache","ext4",MS_NOSUID|MS_NODEV|MS_NOEXEC,"errors=remount-ro")){
        failure("cache mount");return;
    }
    cache_ready=1;note("V4 diagnostic cache log opened; partition identity verified");
}
static void snapshot(void){
    char *buf=malloc(262144);if(!buf)return;
    int n=klogctl(3,buf,262144);
    if(n>0&&cache_ready){
        int fd=open("/cache/leo-init-v4-kernel.log",O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC,0644);
        if(fd>=0){ssize_t written=write(fd,buf,(size_t)n);(void)written;fsync(fd);close(fd);}
    }
    free(buf);
}
static void android(char **argv){
    setenv("LEO_DIAG_HOLD_ON_FAILURE","1",1);
    execv("/init.android",argv);
    note("exec /init.android failed; holding for ADB");
    for(;;)sleep(1);
}
static int sandbox(const char *root){
    /* No live USB devices or properties in this root; isolate TCP listeners too. */
    if(unshare(CLONE_NEWNS|CLONE_NEWNET)){perror("sandbox unshare");return 2;}
    if(mount(NULL,"/",NULL,MS_REC|MS_PRIVATE,NULL)){perror("sandbox private mounts");return 3;}
    if(chroot(root)||chdir("/")){perror("sandbox chroot");return 4;}
    dir("/proc",0755);mount("proc","/proc","proc",MS_RDONLY|MS_NOSUID|MS_NODEV|MS_NOEXEC,NULL);
    dir("/dev",0755);mount("tmpfs","/dev","tmpfs",MS_NOSUID,"mode=0755");
    mknod("/dev/null",S_IFCHR|0666,makedev(1,3));
    mknod("/dev/random",S_IFCHR|0666,makedev(1,8));
    mknod("/dev/urandom",S_IFCHR|0666,makedev(1,9));
    clearenv();for(int fd=3;fd<1024;fd++)close(fd);
    setenv("LD_LIBRARY_PATH","/system/lib",1);
    setenv("PATH","/system/bin",1);
    setenv("ADB_TRACE","all",1);
    setenv("LEO_EARLY_ADB","1",1);
    pid_t p=fork();if(p<0)return 5;
    if(p==0){execl("/system/bin/adbd","/system/bin/adbd",(char*)NULL);perror("sandbox exec");_exit(111);}
    int status=0;
    for(int i=0;i<40;i++){
        if(waitpid(p,&status,WNOHANG)==p){
            printf("sandbox adbd exited: raw_status=%d signal=%d exit=%d\n",status,
                   WIFSIGNALED(status)?WTERMSIG(status):0,WIFEXITED(status)?WEXITSTATUS(status):-1);
            return 0;
        }
        usleep(100000);
    }
    kill(p,SIGTERM);waitpid(p,&status,0);
    printf("sandbox adbd survived 4 seconds; stopped test process\n");
    return 0;
}
int main(int argc,char **argv){
    if(argc==3&&!strcmp(argv[1],"--sandbox-adbd"))return sandbox(argv[2]);
    if(argc==2&&!strcmp(argv[1],"--reboot-recovery")){
        sync();syscall(__NR_reboot,LINUX_REBOOT_MAGIC1,LINUX_REBOOT_MAGIC2,LINUX_REBOOT_CMD_RESTART2,"recovery");
        perror("reboot recovery");return 1;
    }
    const char *base=strrchr(argv[0],'/');base=base?base+1:argv[0];
    if(getenv("INIT_SECOND_STAGE")||strcmp(base,"init")!=0)android(argv);
    umask(0);
    setenv("PATH","/system/bin:/sbin",1);
    dir("/dev",0755);mount("tmpfs","/dev","tmpfs",MS_NOSUID,"mode=0755");
    mknod("/dev/kmsg",S_IFCHR|0600,makedev(1,11));
    mknod("/dev/null",S_IFCHR|0666,makedev(1,3));
    mknod("/dev/random",S_IFCHR|0666,makedev(1,8));
    mknod("/dev/urandom",S_IFCHR|0666,makedev(1,9));
    dir("/proc",0755);mount("proc","/proc","proc",0,NULL);
    dir("/sys",0755);mount("sysfs","/sys","sysfs",0,NULL);
    dir("/tmp",0755);mount("tmpfs","/tmp","tmpfs",0,"mode=0755");
    mount(NULL,"/",NULL,MS_REC|MS_PRIVATE,NULL);
    cache_log_mount();
    note("PID1 started; only diagnostic cache mounted persistently");
    for(int i=0;i<2;i++){
        put("/sys/class/leds/button-backlight/brightness","40");usleep(400000);
        put("/sys/class/leds/button-backlight/brightness","0");usleep(400000);
    }
    dir("/config",0755);mount("configfs","/config","configfs",0,NULL);
    dir("/config/usb_gadget/g1",0770);
    put("/config/usb_gadget/g1/idVendor","0x18d1");
    put("/config/usb_gadget/g1/idProduct","0xd001");
    dir("/config/usb_gadget/g1/strings/0x409",0770);
    put("/config/usb_gadget/g1/strings/0x409/serialnumber","695d");
    put("/config/usb_gadget/g1/strings/0x409/manufacturer","Nokia");
    put("/config/usb_gadget/g1/strings/0x409/product","Leo init diagnostic");
    dir("/config/usb_gadget/g1/functions/ffs.adb",0770);
    put("/config/usb_gadget/g1/bcdUSB","0x0200");
    put("/config/usb_gadget/g1/os_desc/use","1");
    put("/config/usb_gadget/g1/os_desc/b_vendor_code","0x1");
    put("/config/usb_gadget/g1/os_desc/qw_sign","MSFT100");
    dir("/config/usb_gadget/g1/configs/b.1",0770);
    dir("/config/usb_gadget/g1/configs/b.1/strings/0x409",0770);
    put("/config/usb_gadget/g1/configs/b.1/strings/0x409/configuration","adb");
    symlink("/config/usb_gadget/g1/functions/ffs.adb","/config/usb_gadget/g1/configs/b.1/f1");
    symlink("/config/usb_gadget/g1/configs/b.1","/config/usb_gadget/g1/os_desc/b.1");
    dir("/dev/usb-ffs",0755);dir("/dev/usb-ffs/adb",0770);
    if(mount("adb","/dev/usb-ffs/adb","functionfs",0,NULL))failure("FunctionFS mount");
    pid_t child=fork();
    if(child==0){
        if(cache_ready){
            int fd=open("/cache/leo-init-v4-adbd.log",O_WRONLY|O_CREAT|O_APPEND,0644);
            if(fd>=0){dup2(fd,1);dup2(fd,2);close(fd);}
        }
        setenv("ADB_TRACE","all",1);
        setenv("LEO_EARLY_ADB","1",1);
        if(unshare(CLONE_NEWNS)){failure("adbd mount namespace");_exit(110);}
        mount(NULL,"/",NULL,MS_REC|MS_PRIVATE,NULL);
        execl("/system/bin/adbd","/system/bin/adbd",(char*)NULL);failure("adbd exec");_exit(111);
    }
    if(child<0)note("emergency adbd fork failed");
    for(int i=0;i<100&&access("/dev/usb-ffs/adb/ep2",F_OK);i++)usleep(100000);
    note(access("/dev/usb-ffs/adb/ep2",F_OK)?"FunctionFS ep2 absent":"FunctionFS ep2 present");
    for(int i=0;i<100;i++){
        if(put("/config/usb_gadget/g1/UDC","msm_hsusb")==0)break;
        usleep(100000);
    }
    put("/tmp/leo-stage","bootstrap-ready");
    char state[256];int status=0;pid_t ended=waitpid(child,&status,WNOHANG);
    snprintf(state,sizeof(state),"adbd pid=%d waitpid=%d status=%d",child,ended,status);note(state);
    note("USB setup attempted; waiting up to 25 seconds for Android init gate");
    if(access("/leo-autostart-android",F_OK)==0){
        put("/tmp/leo-run-android","automatic");note("image requests automatic Android init startup");
    }
    for(int i=0;i<25&&access("/tmp/leo-run-android",F_OK);i++)sleep(1);
    if(access("/tmp/leo-run-android",F_OK)){
        note("Android init gate absent; capture logs and return normally to KaiOS");
        if(ended==0&&child>0){kill(child,SIGTERM);waitpid(child,&status,0);}
        if(readtext("/config/usb_gadget/g1/UDC",state,sizeof(state))==0)note(state);
        snapshot();sync();
        if(cache_ready){cache_ready=0;umount("/cache");}
        reboot(RB_AUTOBOOT);
        note("normal reboot returned unexpectedly; holding");for(;;)sleep(1);
    }
    if(access("/leo-framework-enabled",F_OK)==0){
        note("mounting framework image files from removable SD");
        pid_t mount_child=fork();
        if(mount_child==0){
            int fd=open("/tmp/leo-framework-mount.log",O_WRONLY|O_CREAT|O_TRUNC,0644);
            if(fd>=0){dup2(fd,1);dup2(fd,2);close(fd);}
            execl("/sbin/leo-framework-mount","/sbin/leo-framework-mount",(char*)NULL);_exit(111);
        }
        int mount_status=0;
        if(mount_child<0||waitpid(mount_child,&mount_status,0)!=mount_child||!WIFEXITED(mount_status)||WEXITSTATUS(mount_status)){
            note("framework image mounting failed; holding with early ADB, no internal system mount");
            put("/tmp/leo-stage","framework-mount-failed");for(;;)sleep(1);
        }
        note("framework system/data image files mounted successfully");
    }
    note("executing Android 8.1 init first stage");
    android(argv);
    return 0;
}
