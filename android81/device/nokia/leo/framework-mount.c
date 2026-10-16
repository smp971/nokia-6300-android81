/* Explicit boot modes: SD image files, or validated internal Android partitions. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/loop.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <unistd.h>

static int text(const char *path,char *buf,size_t size){
    int fd=open(path,O_RDONLY|O_CLOEXEC);if(fd<0)return -1;
    ssize_t n=read(fd,buf,size-1);close(fd);if(n<0)return -1;buf[n]=0;return 0;
}
static int loop_image(const char *file,const char *target,int number,int readonly){
    char node[64];snprintf(node,sizeof(node),"/dev/leo-framework-loop%d",number);
    struct stat st;
    if(lstat(file,&st)||!S_ISREG(st.st_mode)||st.st_size<67108864||st.st_size>1073741824LL)return -1;
    if(mknod(node,S_IFBLK|0600,makedev(7,number))&&errno!=EEXIST)return -1;
    int backing=open(file,(readonly?O_RDONLY:O_RDWR)|O_CLOEXEC|O_NOFOLLOW);
    int loop=open(node,(readonly?O_RDONLY:O_RDWR)|O_CLOEXEC);
    if(backing<0||loop<0){if(backing>=0)close(backing);if(loop>=0)close(loop);return -1;}
    if(ioctl(loop,LOOP_SET_FD,backing)){close(backing);close(loop);return -1;}
    struct loop_info64 info;memset(&info,0,sizeof(info));
    info.lo_flags=readonly?LO_FLAGS_READ_ONLY:0;
    snprintf((char*)info.lo_file_name,LO_NAME_SIZE,"%s",file);
    if(ioctl(loop,LOOP_SET_STATUS64,&info)){ioctl(loop,LOOP_CLR_FD,0);close(backing);close(loop);return -1;}
    mkdir(target,0755);
    int result=mount(node,target,"ext4",MS_NOSUID|MS_NODEV|(readonly?MS_RDONLY:0),readonly?"noload":"errors=remount-ro");
    if(result)ioctl(loop,LOOP_CLR_FD,0);
    close(backing);close(loop);return result;
}
int main(void){
    char buf[256];
    if(access("/leo-framework-internal",F_OK)==0){
        char identity[2048];
        if(text("/proc/cmdline",identity,sizeof(identity))||!strstr(identity,"androidboot.serialno=695d ")||!strstr(identity,"androidboot.hw.name=leo "))return 20;
        if(text("/sys/class/block/mmcblk0p30/dev",buf,sizeof(buf))||strcmp(buf,"179:30\n"))return 21;
        if(text("/sys/class/block/mmcblk0p30/size",buf,sizeof(buf))||strcmp(buf,"1638400\n"))return 22;
        if(text("/sys/class/block/mmcblk0p30/uevent",buf,sizeof(buf))||!strstr(buf,"PARTNAME=system\n"))return 23;
        if(text("/sys/class/block/mmcblk0p39/dev",buf,sizeof(buf))||strcmp(buf,"259:7\n"))return 24;
        if(text("/sys/class/block/mmcblk0p39/size",buf,sizeof(buf))||strcmp(buf,"5013471\n"))return 25;
        if(text("/sys/class/block/mmcblk0p39/uevent",buf,sizeof(buf))||!strstr(buf,"PARTNAME=userdata\n"))return 26;
        if(mknod("/dev/leo-internal-system",S_IFBLK|0600,makedev(179,30))&&errno!=EEXIST)return 27;
        if(mknod("/dev/leo-internal-data",S_IFBLK|0600,makedev(259,7))&&errno!=EEXIST)return 28;
        mkdir("/system",0755);mkdir("/data",0771);
        if(mount("/dev/leo-internal-system","/system","ext4",MS_RDONLY|MS_NODEV,"noload")){perror("internal system");return 29;}
        if(access("/system/framework/services.jar",R_OK)){fputs("Not an Android system partition\n",stderr);return 30;}
        if(mount("/dev/leo-internal-data","/data","ext4",MS_NOSUID|MS_NODEV,"errors=remount-ro")){perror("internal data");return 31;}
        puts("LEO_INTERNAL_ANDROID_PARTITIONS_MOUNTED");return 0;
    }
    /* This kernel reports removable=0 even for the SD slot; verify card type. */
    if(text("/sys/class/block/mmcblk1/device/type",buf,sizeof(buf))||strcmp(buf,"SD\n"))return 2;
    if(text("/sys/class/block/mmcblk1p1/dev",buf,sizeof(buf))||strcmp(buf,"179:65\n"))return 3;
    mkdir("/leo-sd",0700);
    if(mknod("/dev/leo-framework-sd",S_IFBLK|0600,makedev(179,65))&&errno!=EEXIST)return 4;
    if(mount("/dev/leo-framework-sd","/leo-sd","vfat",MS_NOSUID|MS_NODEV|MS_NOEXEC,"utf8,errors=remount-ro")){perror("SD mount");return 5;}
    if(text("/leo-sd/leo-android81/owner",buf,sizeof(buf))||strcmp(buf,"LEO_ANDROID81_DIAGNOSTIC_695d\n"))return 6;
    if(loop_image("/leo-sd/leo-android81/system.raw.img","/system",0,1)){perror("test system image");return 7;}
    if(loop_image("/leo-sd/leo-android81/data.raw.img","/data",1,0)){perror("test data image");return 8;}
    puts("LEO_FRAMEWORK_IMAGES_MOUNTED");return 0;
}
