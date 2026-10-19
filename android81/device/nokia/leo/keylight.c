/* Leo keypad illumination: observe input without grabbing or recording keys.
 * ARM32 Linux ABI, like keypad-frame-bench.c. No idle polling outside the
 * eight-second illumination window. Engineering build uses init's context.
 */
extern int open(const char*,int,...),close(int),ioctl(int,unsigned long,...);
extern long read(int,void*,unsigned long),write(int,const void*,unsigned long);
extern int strcmp(const char*,const char*),snprintf(char*,unsigned long,const char*,...);
extern int atoi(const char*),__system_property_get(const char*,char*);
extern void (*signal(int,void (*)(int)))(int);
struct timespec {long sec,nsec;};
extern int clock_gettime(int,struct timespec*);
struct pollfd {int fd;short events,revents;};
extern int poll(struct pollfd*,unsigned long,int);
struct input_event {long sec,usec;unsigned short type,code;int value;};
typedef char arm32_event[sizeof(struct input_event)==16?1:-1];
static volatile int running=1;
static void stop(int sig){(void)sig;running=0;}
static long long now(void){struct timespec t;clock_gettime(1,&t);return t.sec*1000LL+t.nsec/1000000;}
static int number(const char *path){char b[32]={0};int f=open(path,0x80000);if(f<0)return -1;long n=read(f,b,sizeof(b)-1);close(f);return n>0?atoi(b):-1;}
static int brightness(int value){char b[16];int n=snprintf(b,sizeof(b),"%d\n",value);int f=open("/sys/class/leds/button-backlight/brightness",1|0x80000);if(f<0)return -1;int ok=write(f,b,(unsigned)n)==n;close(f);return ok?0:-1;}
int main(void){
    int fd=open("/dev/input/event1",0x80000);if(fd<0)return 1;
    char name[80]={0};
    if(ioctl(fd,0x80004506UL|(sizeof(name)<<16),name)<0||strcmp(name,"matrix_keypad")){close(fd);return 2;}
    signal(15,stop);signal(2,stop);
    long long until=now()+8000;int previous=-1;
    struct pollfd p={fd,1,0};
    while(running){
        long long remaining=until-now();int level=0;
        if(remaining>0&&number("/sys/class/leds/lcd-backlight/brightness")>0){
            char value[92]={0};level=20;
            if(__system_property_get("persist.leo.keylight.level",value)>0)level=atoi(value);
            int max=number("/sys/class/leds/button-backlight/max_brightness");
            if(max<0)break;
            if(level<0)level=0;
            if(level>max)level=max;
        }
        if(level!=previous){if(brightness(level))break;previous=level;}
        int result=poll(&p,1,remaining>0?250:-1);
        if(result<0){if(!running)break;continue;}
        if(result>0){
            if(p.revents&~1)break;
            struct input_event e;
            if(read(fd,&e,sizeof(e))!=sizeof(e))break;
            if(e.type==1&&(e.value==1||e.value==2))until=now()+8000;
        }
    }
    brightness(0);close(fd);return running?3:0;
}
