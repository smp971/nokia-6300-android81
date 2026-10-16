/* Execute graphics diagnostics with init's device/property view in a private chroot. */
#define _GNU_SOURCE
#include <fcntl.h>
#include <asm/ptrace.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <unistd.h>

int main(int argc,char **argv){
    if(argc==3&&!strcmp(argv[1],"--sample-pc")){
        pid_t pid=(pid_t)atoi(argv[2]);char path[64],name[64]={0};
        snprintf(path,sizeof(path),"/proc/%d/comm",pid);FILE *file=fopen(path,"r");
        if(!file||!fgets(name,sizeof(name),file)||strncmp(name,"leo_graphics_pr",15))return 8;
        fclose(file);
        if(ptrace(PTRACE_ATTACH,pid,NULL,NULL)){perror("probe ptrace attach");return 9;}
        int status=0;waitpid(pid,&status,0);
        struct pt_regs regs;
        if(ptrace(PTRACE_GETREGS,pid,NULL,&regs)){perror("probe registers");ptrace(PTRACE_DETACH,pid,NULL,NULL);return 10;}
        printf("pc=%08lx lr=%08lx sp=%08lx fp=%08lx\n",(unsigned long)regs.ARM_pc,(unsigned long)regs.ARM_lr,(unsigned long)regs.ARM_sp,(unsigned long)regs.ARM_fp);
        ptrace(PTRACE_DETACH,pid,NULL,NULL);return 0;
    }
    if(argc<3||strcmp(argv[1],"/tmp/leo-graphics")){fprintf(stderr,"usage: graphics-exec /tmp/leo-graphics PROGRAM [ARGS]\n");return 2;}
    int fd=open("/proc/1/ns/mnt",O_RDONLY|O_CLOEXEC);
    if(fd<0||setns(fd,CLONE_NEWNS)){perror("init mount namespace");return 3;}close(fd);
    if(unshare(CLONE_NEWNS)||mount(NULL,"/",NULL,MS_REC|MS_PRIVATE,NULL)){perror("private mount namespace");return 4;}
    const char *dirs[]={"dev","proc","sys"};
    for(unsigned i=0;i<3;i++){
        char source[32],target[128];snprintf(source,sizeof(source),"/%s",dirs[i]);snprintf(target,sizeof(target),"%s/%s",argv[1],dirs[i]);
        mkdir(target,0755);
        if(mount(source,target,NULL,MS_BIND|MS_REC,NULL)){perror(target);return 5;}
    }
    if(chroot(argv[1])||chdir("/")){perror("chroot");return 6;}
    setenv("PATH","/system/bin:/vendor/bin:/vendor/bin/hw",1);
    setenv("LD_LIBRARY_PATH","/system/lib:/vendor/lib",1);
    execv(argv[2],&argv[2]);perror("graphics exec");return 7;
}
