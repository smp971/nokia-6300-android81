/* Private legacy GNSS library mount. No global HAL/library replacement. */
#define _GNU_SOURCE
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <sched.h>
#include <signal.h>
#include <grp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <errno.h>
int main(int argc,char **argv){
 const char *base=getenv("LEO_GPS_ROOT");if(!base)base="/vendor/leo-gps";
 if(strcmp(base,"/tmp/leo-gnss-runtime")&&strcmp(base,"/vendor/leo-gps"))return 2;
 if(argc!=2)return 2;char *end;long seconds=strtol(argv[1],&end,10);if(*end||seconds<0||seconds>900)return 2;
 char hw[256],etc[256],libs[768],binary[256];
 snprintf(hw,sizeof(hw),"%s/hw",base);snprintf(etc,sizeof(etc),"%s/etc",base);
 snprintf(libs,sizeof(libs),"%s/lib:/vendor/leo-radio/lib:/vendor/lib:/system/lib",base);
 snprintf(binary,sizeof(binary),"%s/gnss-provider",base);
 pid_t parent=getpid(),child=fork();if(child<0)return 3;
 if(!child){
  if(setpgid(0,0)||prctl(PR_SET_PDEATHSIG,SIGKILL)||getppid()!=parent)_exit(4);
  if(unshare(CLONE_NEWNS)||mount(NULL,"/",NULL,MS_REC|MS_PRIVATE,NULL))_exit(5);
  if(mount(hw,"/vendor/lib/hw",NULL,MS_BIND,NULL))_exit(6);
  if(!strcmp(base,"/tmp/leo-gnss-runtime")&&mount(etc,"/system/etc",NULL,MS_BIND,NULL))_exit(6);
  setenv("LD_LIBRARY_PATH",libs,1);setenv("LD_PRELOAD","/vendor/leo-radio/shim.so",1);
  gid_t groups[]={1000,1001,1021,3003,3004,3005};
  if(setgroups(6,groups)||setgid(1021)||setuid(1021))_exit(7);
  execl(binary,binary,(char*)NULL);perror("gnss exec");_exit(8);
 }
 int status;struct timespec start,now;clock_gettime(CLOCK_MONOTONIC,&start);
 for(;;){pid_t done=waitpid(child,&status,WNOHANG);if(done==child)break;if(done<0&&errno!=EINTR)return 9;
  clock_gettime(CLOCK_MONOTONIC,&now);if(seconds>0&&now.tv_sec-start.tv_sec>=seconds){kill(-child,SIGTERM);usleep(300000);kill(-child,SIGKILL);waitpid(child,&status,0);puts("GNSS_TRIAL_TIMEOUT");return 124;}usleep(100000);
 }
 printf("GNSS_CHILD_STATUS=%d\n",status);return WIFEXITED(status)?WEXITSTATUS(status):128+WTERMSIG(status);
}
