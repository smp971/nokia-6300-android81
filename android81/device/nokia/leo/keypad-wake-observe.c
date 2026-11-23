/* Bounded, passive wake diagnostic for ARM32 Oreo. No input grab/injection,
 * wake lock or wake alarm. Records timing and device kind, never key codes.
 * The BOOTTIME timer expires after 20 minutes but cannot wake the system.
 */
extern int open(const char*,int,...),close(int),ioctl(int,unsigned long,...);
extern long read(int,void*,unsigned long),write(int,const void*,unsigned long);
extern int strcmp(const char*,const char*),atoi(const char*);
extern int snprintf(char*,unsigned long,const char*,...);
struct timespec {long sec,nsec;};
struct itimerspec {struct timespec interval,value;};
extern int clock_gettime(int,struct timespec*),usleep(unsigned);
extern int timerfd_create(int,int),timerfd_settime(int,int,const struct itimerspec*,void*);
struct pollfd {int fd;short events,revents;};
extern int poll(struct pollfd*,unsigned long,int);
struct input_event {long sec,usec;unsigned short type,code;int value;};
typedef char arm32_event[sizeof(struct input_event)==16?1:-1];
static long long ms(int clock){struct timespec t={0,0};if(clock_gettime(clock,&t))return -1;return t.sec*1000LL+t.nsec/1000000;}
static int lcd(void){char b[32]={0};int f=open("/sys/class/leds/lcd-backlight/brightness",0x80000);if(f<0)return -1;long n=read(f,b,sizeof(b)-1);close(f);return n>0?atoi(b):-1;}
static void report(int id,const char *kind,long long event,long long seen,long long boot,long long lit,long long active,int value){
    char row[320];int n=snprintf(row,sizeof(row),"wake=%d source=%s event_mono_ms=%lld seen_mono_ms=%lld seen_boot_ms=%lld lcd_delay_boot_ms=%lld lcd_delay_awake_ms=%lld brightness=%d\n",id,kind,event,seen,boot,lit,active,value);
    if(n>0&&(unsigned)n<sizeof(row))write(1,row,(unsigned)n);
}
int main(void){
    struct pollfd fds[4];int kind[3],count=0;
    for(int i=0;i<16&&count<3;i++){
        char path[40],name[80]={0};snprintf(path,sizeof(path),"/dev/input/event%d",i);
        int fd=open(path,0x80000);if(fd<0)continue;
        int k=-1,clock=1;
        if(ioctl(fd,0x80004506UL|(sizeof(name)<<16),name)>=0){
            if(!strcmp(name,"matrix_keypad"))k=0;
            else if(!strcmp(name,"gpio-keys"))k=1;
            else if(!strcmp(name,"qpnp_pon"))k=2;
        }
        if(k<0||ioctl(fd,0x400445a0UL,&clock)){close(fd);continue;}
        fds[count]=(struct pollfd){fd,1,0};kind[count++]=k;
    }
    if(!count)return 1;
    int timer=timerfd_create(7,0x80000);
    struct itimerspec limit={{0,0},{1200,0}};
    if(timer<0||timerfd_settime(timer,0,&limit,(void*)0))return 2;
    fds[count]=(struct pollfd){timer,1,0};
    static const char ready[]="ready: passive; no wake lock; 20-minute non-waking deadline\n";
    write(1,ready,sizeof(ready)-1);
    int samples=0;
    while(samples<8){
        if(poll(fds,(unsigned)count+1,-1)<0)break;
        if(fds[count].revents)break;
        for(int i=0;i<count;i++){
            if(!(fds[i].revents&1))continue;
            struct input_event e;
            if(read(fds[i].fd,&e,sizeof(e))!=sizeof(e))continue;
            if(e.type!=1||e.value!=1||lcd()!=0)continue;
            long long seen=ms(1),boot=ms(7),event=e.sec*1000LL+e.usec/1000;
            if(seen<0||boot<0)continue;
            int value=0;long long now=boot,active=seen;
            // Bound sampling to six wall-clock seconds, including suspend.
            while(now-boot<6000){
                value=lcd();now=ms(7);active=ms(1);
                if(value!=0)break;
                usleep(10000);
            }
            report(++samples,kind[i]==0?"matrix":kind[i]==1?"gpio":"pon",
                event,seen,boot,value>0?now-boot:-1,active-seen,value);
        }
    }
    for(int i=0;i<=count;i++)close(fds[i].fd);
    return 0;
}
