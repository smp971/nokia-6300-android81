/* Early ADB lives in its own mount namespace; execute diagnostics in init's view. */
#define _GNU_SOURCE
#include <fcntl.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
int main(int argc,char **argv){
    if(argc<2||argv[1][0]!='/')return 2;
    int fd=open("/proc/1/ns/mnt",O_RDONLY|O_CLOEXEC);
    if(fd<0||setns(fd,CLONE_NEWNS)){perror("init mount namespace");return 3;}
    close(fd);
    setenv("PATH","/system/bin:/vendor/bin:/vendor/bin/hw:/sbin",1);
    execv(argv[1],argv+1);perror("framework exec");return 4;
}
