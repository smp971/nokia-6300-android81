/* Renew only the diagnostic adbd, retaining its FunctionFS mount namespace.
 * Called by the independent root USB service after UDC is disconnected.
 * ARM32 declarations match the retained diagnostic toolchain.
 */
extern int open(const char*,int,...),close(int),setns(int,int),kill(int,int);
extern int fork(void),setsid(void),dup2(int,int),execv(const char*,char*const[]);
extern long read(int,void*,unsigned long);
extern int atoi(const char*),strcmp(const char*,const char*),snprintf(char*,unsigned long,const char*,...),printf(const char*,...);
extern int setenv(const char*,const char*,int),usleep(unsigned);
extern void _exit(int);
int main(int argc,char **argv){
    if(argc!=2)return 2;
    int target=atoi(argv[1]);if(target<=1)return 2;
    char path[96],comm[32]={0};
    snprintf(path,sizeof(path),"/proc/%d/comm",target);
    int fd=open(path,0x80000);if(fd<0)return 3;
    long n=read(fd,comm,sizeof(comm)-1);close(fd);
    if(n!=5||strcmp(comm,"adbd\n"))return 3;
    snprintf(path,sizeof(path),"/proc/%d/ns/mnt",target);
    int ns=open(path,0x80000);if(ns<0)return 4;
    int log=open("/data/local/tmp/leo-adbd-renew.log",1|64|1024,0600);
    if(log<0){close(ns);return 4;}
    int child=fork();if(child<0){close(ns);close(log);return 5;}
    if(child==0){
        if(setsid()<0||setns(ns,0x20000)<0)_exit(6);
        close(ns);dup2(log,1);dup2(log,2);close(log);
        int nullfd=open("/dev/null",0);if(nullfd>=0){dup2(nullfd,0);if(nullfd>2)close(nullfd);}
        for(int f=3;f<1024;f++)close(f);
        if(kill(target,15))_exit(7);
        int tries=0;while(kill(target,0)==0&&tries++<40)usleep(100000);
        if(kill(target,0)==0)_exit(8);
        setenv("LEO_EARLY_ADB","1",1);setenv("ADB_TRACE","usb,transport",1);
        char *args[]={"/system/bin/adbd",0};execv(args[0],args);_exit(9);
    }
    close(ns);close(log);printf("%d\n",child);return 0;
}
