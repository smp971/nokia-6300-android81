/* Camera bring-up process only: private hardware-library and socket mounts.
 * No global library replacement or global device permission changes.
 * Engineering root process; not a production privilege/security boundary. */
#define _GNU_SOURCE
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <errno.h>
#define DIAGNOSTIC_BASE "/data/local/tmp/leo-camera-integration"
int main(int argc,char **argv) {
 const char *base=getenv("LEO_CAMERA_ROOT");if(!base)base="/vendor/leo-camera";
 if(strcmp(base,DIAGNOSTIC_BASE)&&strcmp(base,"/vendor/leo-camera"))return 2;
 char hw[256],sockets[256],libs[512],executable[256];
 snprintf(hw,sizeof(hw),"%s/hw",base);
 snprintf(sockets,sizeof(sockets),"%s",!strcmp(base,DIAGNOSTIC_BASE)?DIAGNOSTIC_BASE "/sockets":"/data/leo-camera/sockets");
 snprintf(libs,sizeof(libs),"%s/lib:/vendor/lib:/system/lib",base);
 if(argc!=3 || (strcmp(argv[1],"daemon")&&strcmp(argv[1],"provider")))return 2;
 char *end;long seconds=strtol(argv[2],&end,10);if(*end||seconds<0||(seconds>0&&seconds<15)||seconds>900)return 2;
 pid_t parent=getpid();pid_t child=fork();if(child<0)return 3;
 if(child==0){
  if(setpgid(0,0)||prctl(PR_SET_PDEATHSIG,SIGKILL)||getppid()!=parent)_exit(4);
  if(unshare(CLONE_NEWNS)||mount(NULL,"/",NULL,MS_REC|MS_PRIVATE,NULL))_exit(5);
  if(mount(hw,"/vendor/lib/hw",NULL,MS_BIND,NULL)||mount(sockets,"/data/misc/camera",NULL,MS_BIND,NULL))_exit(6);
  setenv("LD_LIBRARY_PATH",libs,1);
  setenv("LEO_CAMERA_ROOT",base,1);
  snprintf(executable,sizeof(executable),"%s/%s",base,!strcmp(argv[1],"daemon")?"mm-qcamera-daemon":"camera-provider");
  execl(executable,executable,(char*)NULL);perror("camera exec");_exit(7);
 }
 int status;struct timespec start,now;clock_gettime(CLOCK_MONOTONIC,&start);
 for(;;){pid_t done=waitpid(child,&status,WNOHANG);if(done==child)break;if(done<0&&errno!=EINTR)return 8;
  clock_gettime(CLOCK_MONOTONIC,&now);if(seconds>0&&now.tv_sec-start.tv_sec>=seconds){
   kill(-child,SIGTERM);usleep(300000);kill(-child,SIGKILL);kill(child,SIGKILL);
   for(int n=0;n<10;n++){if(waitpid(child,&status,WNOHANG)==child){printf("CAMERA_TIMEOUT_REAPED\n");return 124;}usleep(100000);}
   printf("CAMERA_TIMEOUT_NOT_REAPED\n");return 125;
  }usleep(100000);
 }
 printf("CAMERA_CHILD_STATUS=%d\n",status);return WIFEXITED(status)?WEXITSTATUS(status):128+WTERMSIG(status);
}
