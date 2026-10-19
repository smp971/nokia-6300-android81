/* Bounded up/down input probe. Measures Android frame stats, not LCD photon latency. */
/* ARM32 Linux input ABI, matching this project's headerless diagnostic toolchain. */
extern int open(const char*,int,...),close(int),ioctl(int,unsigned long,...);
extern long write(int,const void*,unsigned long);
extern int puts(const char*),strcmp(const char*,const char*),usleep(unsigned);
extern void perror(const char*);
extern void *memset(void*,int,unsigned long);
extern unsigned alarm(unsigned);
struct input_event { long sec,usec;unsigned short type,code;int value; };
typedef char require_arm32_input_event[sizeof(struct input_event)==16?1:-1];
#define O_WRONLY 1
#define O_CLOEXEC 0x80000
#define EVIOCGNAME(length) (0x80004506UL|((unsigned long)(length)<<16))
#define EV_KEY 1
#define EV_SYN 0
#define SYN_REPORT 0
#define KEY_DOWN 108
#define KEY_UP 103

static int event(int fd,unsigned short type,unsigned short code,int value){
    struct input_event e;memset(&e,0,sizeof(e));e.type=type;e.code=code;e.value=value;
    return write(fd,&e,sizeof(e))==(long)sizeof(e)?0:-1;
}
int main(void){
    alarm(15);
    int fd=open("/dev/input/event1",O_WRONLY|O_CLOEXEC);if(fd<0){perror("input open");return 1;}
    char name[80]={0};if(ioctl(fd,EVIOCGNAME(sizeof(name)),name)<0||strcmp(name,"matrix_keypad")){puts("Unexpected input device");close(fd);return 2;}
    for(int n=0;n<40;n++){
        unsigned short key=n%2?KEY_UP:KEY_DOWN;
        if(event(fd,EV_KEY,key,1)||event(fd,EV_SYN,SYN_REPORT,0)){close(fd);return 3;}
        usleep(60000);
        if(event(fd,EV_KEY,key,0)||event(fd,EV_SYN,SYN_REPORT,0)){close(fd);return 3;}
        usleep(60000);
    }
    close(fd);puts("40 bounded D-pad events completed");return 0;
}
