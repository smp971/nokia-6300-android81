/* Leo keypad illumination and wake: observe physical input without grabbing or recording keys.
 * ARM32 Linux ABI, like keypad-frame-bench.c. No idle polling outside the
 * eight-second illumination window. Engineering build uses init's context.
 */
extern int open(const char*,int,...),close(int),ioctl(int,unsigned long,...);
extern long read(int,void*,unsigned long),write(int,const void*,unsigned long);
extern int strcmp(const char*,const char*),snprintf(char*,unsigned long,const char*,...);
extern int atoi(const char*),__system_property_get(const char*,char*);
extern int fork(void),execl(const char*,const char*,...);
extern void _exit(int);
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
static int physicalKey(int kind,unsigned code){
    if(kind==1)return code==522;
    if(kind==2)return code==523; /* POWER already has Android's native wake/sleep policy. */
    switch(code){case 2:case 3:case 4:case 5:case 6:case 7:case 8:case 9:case 10:case 11:
        case 14:case 103:case 105:case 106:case 107:case 108:case 139:case 158:
        case 212:case 231:case 352:case 522:case 523:return 1;default:return 0;}
}
static void wake(long long *next){
    if(now()<*next||number("/sys/class/leds/lcd-backlight/brightness")!=0)return;
    char ready[92]={0};if(__system_property_get("sys.boot_completed",ready)<=0||strcmp(ready,"1"))return;
    int child=fork();
    if(child==0){execl("/system/bin/sh","sh","/system/bin/input","keyevent","224",(char*)0);_exit(127);}
    if(child>0)*next=now()+2000;
}
int main(void){
    struct pollfd inputs[3];int kinds[3],count=0;
    for(int index=0;index<16&&count<3;index++){
        char path[40],name[80]={0};snprintf(path,sizeof(path),"/dev/input/event%d",index);
        int fd=open(path,0x80000);if(fd<0)continue;
        int kind=-1;
        if(ioctl(fd,0x80004506UL|(sizeof(name)<<16),name)>=0){
            if(!strcmp(name,"matrix_keypad"))kind=0;
            else if(!strcmp(name,"gpio-keys"))kind=1;
            else if(!strcmp(name,"qpnp_pon"))kind=2;
        }
        if(kind<0){close(fd);continue;}
        inputs[count]=(struct pollfd){fd,1,0};kinds[count++]=kind;
    }
    if(!count)return 1;
    signal(15,stop);signal(2,stop);
    signal(17,(void (*)(int))1); /* SIGCHLD ignored: reap the bounded wake helper. */
    long long until=now()+8000,nextWake=0;int previous=-1;
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
        int result=poll(inputs,(unsigned)count,remaining>0?250:-1);
        if(result<0){if(!running)break;continue;}
        if(result>0)for(int index=0;index<count;index++){
            if(!inputs[index].revents)continue;
            if(inputs[index].revents&~1){running=0;break;}
            struct input_event e;
            if(read(inputs[index].fd,&e,sizeof(e))!=sizeof(e)){running=0;break;}
            if(e.type==1&&physicalKey(kinds[index],e.code)&&(e.value==1||e.value==2)){
                until=now()+8000;
                if(e.value==1)wake(&nextWake);
            }
        }
    }
    brightness(0);for(int index=0;index<count;index++)close(inputs[index].fd);return running?3:0;
}
