#define _GNU_SOURCE
/* Bounded receive-only Iris trial. No transmit mode, mixer, module or boot changes.
 * Private controls confirmed through QUERYCTRL on this phone before use.
 * Parent survives ADB hangup and supervises its own worker process group.
 */
#include <stdio.h>
#include <stdint.h>
#include <dirent.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <stddef.h>
#include <poll.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <errno.h>
#include <time.h>
#include <sys/file.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <linux/videodev2.h>
#define BASE "/data/leo-fm"
#define TRANSPORT "/sys/module/radio_iris_transport/parameters/fmsmd_set"
#define STATE (V4L2_CID_PRIVATE_BASE+4)
static volatile sig_atomic_t interrupted;
static int scan_mode,listen_mode,route_check,session_mode,session_fd=-1;
static unsigned selected_khz=100000;
static void stopped(int number){(void)number;interrupted=1;}
static double now(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec+t.tv_nsec/1e9;}
static int transport(int enabled){int fd=open(TRANSPORT,O_WRONLY|O_CLOEXEC);if(fd<0){perror("transport open");return -1;}char value=enabled?'1':'0';int result=write(fd,&value,1)==1?0:-1;if(result)perror("transport write");close(fd);printf("transport=%d result=%d\n",enabled,result);return result;}
static int control(int fd,unsigned id,int value){struct v4l2_control c={0};c.id=id;c.value=value;int result=ioctl(fd,VIDIOC_S_CTRL,&c);printf("control=0x%x value=%d result=%d errno=%d\n",id,value,result,result?errno:0);return result;}
static int audio_command(const char *action){
    pid_t child=fork();if(child<0)return -1;
    if(!child){prctl(PR_SET_PDEATHSIG,SIGKILL);setenv("CLASSPATH",BASE "/audio.jar",1);execl("/system/bin/app_process","app_process","/system/bin","ro.leo.fm.FmAudioCommand",action,(char*)NULL);_exit(95);}
    int status;while(waitpid(child,&status,0)<0){if(errno!=EINTR)return -1;}
    int result=WIFEXITED(status)?WEXITSTATUS(status):-1;printf("audio_action=%s result=%d\n",action,result);return result;
}
static int valid_frequency(const char *text,unsigned *value){char *end;unsigned long f=strtoul(text,&end,10);if(*end||f<87500||f>108000||f%100)return -1;*value=(unsigned)f;return 0;}
static int line(int fd,char *text,unsigned capacity,int timeout){unsigned used=0;double deadline=now()+timeout/1000.0;while(used+1<capacity){struct pollfd p={fd,POLLIN,0};int remaining=(int)((deadline-now())*1000);if(remaining<=0||poll(&p,1,remaining)<=0)return -1;char c;if(read(fd,&c,1)!=1)return -1;if(c=='\n'){text[used]=0;return 0;}text[used++]=c;}return -1;}
static int session_connect(void){
    int allowed_fd=open("/system/etc/leo-terminal-root.uid",O_RDONLY|O_NOFOLLOW|O_CLOEXEC);if(allowed_fd<0)return -1;struct stat st;char uid[32]={0};int count=read(allowed_fd,uid,sizeof(uid)-1);int trusted=!fstat(allowed_fd,&st)&&S_ISREG(st.st_mode)&&st.st_uid==0&&!(st.st_mode&0022);close(allowed_fd);char *end;unsigned long allowed=strtoul(uid,&end,10);if(!trusted||count<=0||allowed<10000||allowed>19999||(*end!='\n'&&*end))return -1;
    int server=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(server<0)return -1;struct sockaddr_un address={0};address.sun_family=AF_UNIX;const char endpoint[]="leo.fm.session.v1";memcpy(address.sun_path+1,endpoint,sizeof(endpoint));if(bind(server,(struct sockaddr*)&address,offsetof(struct sockaddr_un,sun_path)+1+strlen(endpoint))||listen(server,1)){close(server);return -1;}
    struct pollfd ready={server,POLLIN,0};if(poll(&ready,1,10000)<=0){close(server);return -1;}int fd=accept4(server,NULL,NULL,SOCK_CLOEXEC);close(server);if(fd<0)return -1;struct ucred peer;socklen_t size=sizeof(peer);if(getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&peer,&size)||(peer.uid!=0&&peer.uid!=allowed)){close(fd);return -1;}char command[64];if(line(fd,command,sizeof(command),10000)||command[0]!='T'||valid_frequency(command+1,&selected_khz)){close(fd);return -1;}return fd;
}
static int scan_peer_alive(void){char byte;ssize_t result=recv(session_fd,&byte,1,MSG_PEEK|MSG_DONTWAIT);if(result==0)return 0;if(result<0&&errno!=EAGAIN&&errno!=EWOULDBLOCK)return 0;return 1;}
static int session_scan(int radio){
    if(control(radio,V4L2_CID_AUDIO_MUTE,3))return 1;
    if(dprintf(session_fd,"SCANNING\n")<0)return 1;
    int previous=-128,before=-128;unsigned previous_khz=0;
    for(unsigned khz=87500;khz<=108000;khz+=100){
        if(!scan_peer_alive())return 1;
        struct pollfd incoming={session_fd,POLLIN,0};while(poll(&incoming,1,0)>0&&(incoming.revents&POLLIN)){char command[64];if(line(session_fd,command,sizeof(command),100)||!strcmp(command,"Q"))return 1;incoming.revents=0;}
        struct v4l2_frequency f={0};f.type=V4L2_TUNER_RADIO;f.frequency=khz*16;if(ioctl(radio,VIDIOC_S_FREQUENCY,&f)){dprintf(session_fd,"ERROR scan\n");return 1;}usleep(40000);struct v4l2_tuner tuner={0};if(ioctl(radio,VIDIOC_G_TUNER,&tuner)){dprintf(session_fd,"ERROR scan\n");return 1;}
        int rssi=(int)(int8_t)tuner.signal;
        if(previous_khz&&previous>=before&&previous>rssi&&previous>=-85)if(dprintf(session_fd,"FOUND %u %d\n",previous_khz,previous)<0)return 1;
        before=previous;previous=rssi;previous_khz=khz;
        if(khz%1000==0)if(dprintf(session_fd,"PROGRESS %u\n",khz)<0)return 1;
    }
    if(previous>=before&&previous>=-85)if(dprintf(session_fd,"FOUND %u %d\n",previous_khz,previous)<0)return 1;
    if(!scan_peer_alive())return 1;
    struct v4l2_frequency restore={0};restore.type=V4L2_TUNER_RADIO;restore.frequency=selected_khz*16;if(ioctl(radio,VIDIOC_S_FREQUENCY,&restore))return 1;usleep(100000);struct v4l2_tuner tuner={0};int rssi=ioctl(radio,VIDIOC_G_TUNER,&tuner)?-128:(int)(int8_t)tuner.signal;
    if(control(radio,V4L2_CID_AUDIO_MUTE,0))return 1;
    return dprintf(session_fd,"DONE %u %d\n",selected_khz,rssi)<0?1:0;
}
/* Metadata FIFOs are nonblocking; never dequeue the blocking event FIFO (1). */
static int rds_enabled;
static int rds_buffer(int radio,unsigned index,unsigned char *data){
    struct v4l2_buffer b={0};b.type=V4L2_BUF_TYPE_PRIVATE;b.memory=V4L2_MEMORY_USERPTR;b.index=index;b.length=128;b.m.userptr=(unsigned long)data;
    if(ioctl(radio,VIDIOC_DQBUF,&b))return -1;
    return b.bytesused<=128?(int)b.bytesused:-1;
}
static void rds_clear(int radio){unsigned char data[128];for(unsigned index=2;index<=3;index++)for(int n=0;n<4;n++)if(rds_buffer(radio,index,data)<=0)break;}
static void rds_poll(int radio){
    if(!rds_enabled)return;
    for(unsigned index=2;index<=3;index++){
        unsigned char data[128];int size=rds_buffer(radio,index,data);if(size<5)continue;
        unsigned length=index==3?(unsigned)(data[0]&15)*8:data[0];
        if(length>(unsigned)(size-5)||!length||length>(index==3?96u:64u))continue;
        /* PS can contain several eight-character versions: show only the first. */
        if(index==3)length=8;
        printf("RDS_DATA kind=%s frequency=%u bytes=%u\n",index==3?"PS":"RT",selected_khz,length);
        dprintf(session_fd,"%s %u ",index==3?"PS":"RT",selected_khz);
        for(unsigned n=0;n<length;n++)dprintf(session_fd,"%02x",data[5+n]);
        dprintf(session_fd,"\n");
    }
}
static int session_seek(int radio,int direction){
    if(control(radio,V4L2_CID_AUDIO_MUTE,3))return 1;
    if(dprintf(session_fd,"SEEKING\n")<0)return 1;
    unsigned origin=selected_khz,khz=origin,previous_khz=origin,target=origin;int before=-128,previous=-128,found=0;
    for(int step=0;step<206;step++){
        if(!scan_peer_alive())return 1;
        struct pollfd incoming={session_fd,POLLIN,0};while(poll(&incoming,1,0)>0&&(incoming.revents&POLLIN)){char command[64];if(line(session_fd,command,sizeof(command),100)||!strcmp(command,"Q"))return 1;incoming.revents=0;}
        khz=direction>0?(khz>=108000?87500:khz+100):(khz<=87500?108000:khz-100);
        struct v4l2_frequency f={0};f.type=V4L2_TUNER_RADIO;f.frequency=khz*16;if(ioctl(radio,VIDIOC_S_FREQUENCY,&f))return 1;
        usleep(40000);struct v4l2_tuner t={0};if(ioctl(radio,VIDIOC_G_TUNER,&t))return 1;int rssi=(int)(int8_t)t.signal;
        int distance=abs((int)previous_khz-(int)origin);if(distance>10250)distance=20600-distance;
        if(step>=2&&distance>=300&&previous>=-85&&previous>=before&&previous>rssi){target=previous_khz;found=1;break;}
        before=previous;previous=rssi;previous_khz=khz;
    }
    struct v4l2_frequency f={0};f.type=V4L2_TUNER_RADIO;f.frequency=target*16;if(ioctl(radio,VIDIOC_S_FREQUENCY,&f))return 1;
    selected_khz=target;usleep(300000);rds_clear(radio);struct v4l2_tuner t={0};int rssi=ioctl(radio,VIDIOC_G_TUNER,&t)?-128:(int)(int8_t)t.signal;
    if(control(radio,V4L2_CID_AUDIO_MUTE,0))return 1;
    return dprintf(session_fd,"SEEKDONE %u %d %d\n",target,rssi,found)<0?1:0;
}
static int session_loop(int radio){
    rds_enabled=!control(radio,V4L2_CID_PRIVATE_BASE+0xf,1)&&!control(radio,V4L2_CID_PRIVATE_BASE+0x10,7);
    printf("RDS_ENABLED=%d\n",rds_enabled);rds_clear(radio);
    struct v4l2_tuner tuner={0};int measured=ioctl(radio,VIDIOC_G_TUNER,&tuner)?-128:(int)(int8_t)tuner.signal;if(dprintf(session_fd,"READY %u %d\n",selected_khz,measured)<0)return -1;
    for(;;){char command[64];if(line(session_fd,command,sizeof(command),18000))return 0;
        if(!strcmp(command,"P")){rds_poll(radio);continue;}
        if(!strcmp(command,"S")){if(session_scan(radio))return 0;rds_clear(radio);continue;}
        if(!strcmp(command,"U")||!strcmp(command,"D")){if(session_seek(radio,command[0]=='U'?1:-1))return 0;continue;}
        if(!strcmp(command,"Q"))return 0;unsigned khz;if(command[0]!='T'||valid_frequency(command+1,&khz)){dprintf(session_fd,"ERROR invalid-command\n");continue;}
        struct v4l2_frequency f={0};f.type=V4L2_TUNER_RADIO;f.frequency=khz*16;if(ioctl(radio,VIDIOC_S_FREQUENCY,&f)){dprintf(session_fd,"ERROR tune\n");continue;}usleep(300000);selected_khz=khz;rds_clear(radio);memset(&tuner,0,sizeof(tuner));measured=ioctl(radio,VIDIOC_G_TUNER,&tuner)?-128:(int)(int8_t)tuner.signal;if(dprintf(session_fd,"STATUS %u %d\n",khz,measured)<0)return 0;
    }
}
static int worker(void){
    if(transport(1))return 10;
    pid_t utility=fork();if(utility<0)return 11;
    if(!utility){prctl(PR_SET_PDEATHSIG,SIGKILL);setenv("LD_LIBRARY_PATH",BASE "/lib:/system/vendor/leo-radio/lib:/system/lib",1);setenv("LD_PRELOAD",BASE "/trace.so",1);execl(BASE "/fm_qsoc_patches","fm_qsoc_patches","199217","0",(char*)NULL);perror("exec calibration utility");_exit(12);}
    int status;while(waitpid(utility,&status,0)<0){if(errno!=EINTR)return 13;}
    printf("calibration_status=%d\n",WIFEXITED(status)?WEXITSTATUS(status):-1);
    if(!WIFEXITED(status)||WEXITSTATUS(status))return 14;
    /* Calibration closes its own radio FD. Re-open the transport if needed. */
    if(transport(1))return 15;
    int fd=open("/dev/radio0",O_RDWR|O_NONBLOCK|O_CLOEXEC);if(fd<0){perror("radio open");return 16;}
    int result=17;
    if(control(fd,STATE,1))goto finish;
    if(control(fd,V4L2_CID_AUDIO_MUTE,3))goto finish;
    if(control(fd,V4L2_CID_PRIVATE_BASE+0xc,1)||control(fd,V4L2_CID_PRIVATE_BASE+0xe,1))goto finish;
    struct v4l2_tuner tuner={0};tuner.type=V4L2_TUNER_RADIO;tuner.rangelow=87500*16;tuner.rangehigh=108000*16;tuner.audmode=V4L2_TUNER_MODE_STEREO;
    if(ioctl(fd,VIDIOC_S_TUNER,&tuner)){perror("S_TUNER");goto finish;}
    if(scan_mode){
        int best=-128;unsigned best_khz=0;
        for(unsigned khz=87500;khz<=108000;khz+=100){
            struct v4l2_frequency f={0};f.type=V4L2_TUNER_RADIO;f.frequency=khz*16;
            if(ioctl(fd,VIDIOC_S_FREQUENCY,&f)){perror("scan tune");goto finish;}
            usleep(40000);
            if(ioctl(fd,VIDIOC_G_TUNER,&tuner)){perror("scan signal");goto finish;}
            int rssi=(int)(int8_t)tuner.signal;
            if(rssi>best){best=rssi;best_khz=khz;printf("SCAN_BEST khz=%u rssi=%d stereo=%u\n",khz,rssi,tuner.audmode);}
        }
        printf("SCAN_COMPLETE best_khz=%u rssi=%d\n",best_khz,best);result=0;goto finish;
    }
    struct v4l2_frequency frequency={0};frequency.type=V4L2_TUNER_RADIO;frequency.frequency=selected_khz*16;
    if(ioctl(fd,VIDIOC_S_FREQUENCY,&frequency)){perror("S_FREQUENCY");goto finish;}
    usleep(300000);
    if(ioctl(fd,VIDIOC_G_TUNER,&tuner)){perror("G_TUNER");goto finish;}
    if(ioctl(fd,VIDIOC_G_FREQUENCY,&frequency)){perror("G_FREQUENCY");goto finish;}
    printf("RECEIVE_OK frequency_khz=%u range_khz=%u..%u signal_raw=%u stereo=%u\n",frequency.frequency/16,tuner.rangelow/16,tuner.rangehigh/16,tuner.signal,tuner.audmode);
    if(listen_mode){
        if(audio_command(route_check||session_mode?"start-muted":"start"))goto finish;
        if(!route_check&&control(fd,V4L2_CID_AUDIO_MUTE,0))goto finish;
        printf("AUDIO_TRIAL frequency_khz=%u duration_seconds=%d fm_volume=%s\n",selected_khz,session_mode?3600:route_check?2:15,route_check||session_mode?"0":"0.08");
        if(session_mode)session_loop(fd);else sleep(route_check?2:15);
        DIR *devices=opendir("/proc/asound/card0");if(devices){struct dirent *entry;while((entry=readdir(devices))){if(strncmp(entry->d_name,"pcm",3))continue;char path[384];snprintf(path,sizeof(path),"/proc/asound/card0/%s/sub0/status",entry->d_name);FILE *stream=fopen(path,"r");if(stream){char line[160];if(fgets(line,sizeof(line),stream)&&strncmp(line,"closed",6))printf("PCM_ACTIVE %s %s",entry->d_name,line);fclose(stream);}}closedir(devices);}
        control(fd,V4L2_CID_AUDIO_MUTE,3);if(audio_command("stop"))goto finish;
    }
    result=0;
finish:
    control(fd,STATE,0);close(fd);return result;
}
/* Always reap only children created here. A userspace deadline cannot repair a kernel hang. */
static int supervise(int seconds,int cleanup){
    pid_t child=fork();if(child<0)return 90;
    if(!child){setpgid(0,0);prctl(PR_SET_PDEATHSIG,SIGKILL);signal(SIGTERM,SIG_DFL);if(cleanup){int audio_result=listen_mode?audio_command("stop"):0;int transport_result=transport(0);_exit(audio_result||transport_result?91:0);}if(seconds==1){sleep(3);_exit(92);}_exit(worker());}
    setpgid(child,child);double deadline=now()+seconds;int status=0;
    for(;;){pid_t done=waitpid(child,&status,WNOHANG);if(done==child)return WIFEXITED(status)?WEXITSTATUS(status):93;if(done<0){perror("waitpid");return 94;}if(now()>=deadline||(!cleanup&&interrupted)){printf("watchdog: terminating own worker group %d\n",child);kill(-child,SIGKILL);for(int n=0;n<20;n++){if(waitpid(child,&status,WNOHANG)==child)return 124;usleep(100000);}printf("worker not reaped; possible kernel wait\n");return 125;}usleep(100000);}
}
int main(int argc,char **argv){
    route_check=argc==2&&!strcmp(argv[1],"--route-check");
    session_mode=argc==2&&!strcmp(argv[1],"--session");
    listen_mode=argc==3&&!strcmp(argv[1],"--listen");
    if(listen_mode){char *end;unsigned long f=strtoul(argv[2],&end,10);if(*end||f<87500||f>108000||f%100){fprintf(stderr,"Frequency must be 87500..108000 kHz, 100 kHz step\n");return 2;}selected_khz=(unsigned)f;}
    else if(argc!=2||(strcmp(argv[1],"--receive")&&strcmp(argv[1],"--watchdog-test")&&strcmp(argv[1],"--scan")&&!route_check&&!session_mode)){fprintf(stderr,"Usage: fm-trial --receive | --scan | --listen KHZ | --watchdog-test\n");return 2;}
    if(route_check||session_mode)listen_mode=1;
    int test=!strcmp(argv[1],"--watchdog-test");scan_mode=!strcmp(argv[1],"--scan");
    int lock=open(BASE "/session.lock",O_RDWR|O_CREAT|O_NOFOLLOW|O_CLOEXEC,0600);if(lock<0)return 3;int locked=0;for(int n=0;n<(session_mode?80:1);n++){if(!flock(lock,LOCK_EX|LOCK_NB)){locked=1;break;}usleep(100000);}if(!locked){perror("exclusive session");return 3;}
    int log=open(BASE "/trial.log",O_WRONLY|O_CREAT|O_TRUNC|O_NOFOLLOW|O_CLOEXEC,0600);if(log<0)return 4;dup2(log,1);dup2(log,2);close(log);setvbuf(stdout,NULL,_IONBF,0);
    signal(SIGHUP,SIG_IGN);signal(SIGPIPE,SIG_IGN);signal(SIGTERM,stopped);signal(SIGINT,stopped);setsid();
    printf("FM_TRIAL begin uptime=%.3f mode=%s\n",now(),test?"watchdog-test":scan_mode?"muted scan":listen_mode?"headset audio":"muted receive");
    if(session_mode){session_fd=session_connect();if(session_fd<0){printf("session connection rejected or timed out\n");return 5;}}
    int result=supervise(test?1:session_mode?3600:scan_mode?45:listen_mode?30:25,0);printf("worker_result=%d\n",result);
    if(!test){int cleanup=supervise(6,1);printf("cleanup_result=%d\n",cleanup);if(!result&&cleanup)result=cleanup;}
    printf("FM_TRIAL end uptime=%.3f result=%d\n",now(),result);if(session_fd>=0)close(session_fd);close(lock);return test?(result==124?0:1):result;
}
