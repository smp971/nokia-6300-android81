/* Bounded stock daemon trial with init-style inherited control socket.
 * No persistent install or automatic boot; parent reaps child and removes socket.
 */
struct sockaddr { unsigned short family; char data[14]; };
struct sockaddr_un { unsigned short sun_family; char sun_path[108]; };
extern int socket(int,int,int),bind(int,const struct sockaddr *,unsigned),close(int);
extern int chmod(const char *,unsigned),chown(const char *,unsigned,unsigned);
extern int setenv(const char *,const char *,int),fork(void),setgroups(unsigned,const unsigned *);
extern int setgid(unsigned),setuid(unsigned),execv(const char *,char *const []);
extern int waitpid(int,int *,int),kill(int,int),unlink(const char *),strcmp(const char *,const char *);
extern unsigned sleep(unsigned);
extern void *memset(void *,int,unsigned);
extern int snprintf(char *,unsigned,const char *,...),printf(const char *,...);
extern void perror(const char *),_exit(int);
extern int atoi(const char *);
int main(int argc,char **argv){
    if(argc!=2 && argc!=3)return 2;
    unsigned duration=argc==3?(unsigned)atoi(argv[2]):20;
    if(duration<1 || duration>180)return 2;
    const char *name,*binary;
    if(!strcmp(argv[1],"qmi")){name="ims_qmid";binary="/tmp/leo-ims/imsqmidaemon";}
    else if(!strcmp(argv[1],"data")){name="ims_datad";binary="/tmp/leo-ims/imsdatadaemon";}
    else return 2;
    struct sockaddr_un address;memset(&address,0,sizeof(address));address.sun_family=1;
    snprintf(address.sun_path,sizeof(address.sun_path),"/dev/socket/%s",name);
    int fd=socket(1,1,0);if(fd<0){perror("socket");return 1;}
    /* Never remove an existing service's socket to make this test succeed. */
    if(bind(fd,(struct sockaddr *)&address,sizeof(address))){perror("bind");close(fd);return 1;}
    if(chmod(address.sun_path,0660) || chown(address.sun_path,1000,1001)){
        perror("socket permissions");close(fd);unlink(address.sun_path);return 1;
    }
    char env[64],value[16];snprintf(env,sizeof(env),"ANDROID_SOCKET_%s",name);snprintf(value,sizeof(value),"%d",fd);
    if(setenv(env,value,1)){close(fd);unlink(address.sun_path);return 1;}
    int child=fork(),status=0,result=1;
    if(child==0){
        unsigned groups[]={1000,1001,1007,3003,3004,3005};
        if(setgroups(6,groups) || setgid(1000) || setuid(1000))_exit(125);
        char *args[]={(char *)binary,0};execv(binary,args);perror("exec");_exit(126);
    }
    if(child>0){
        int stopped=0;
        for(unsigned n=0;n<duration;n++){
            if(waitpid(child,&status,1)==child){stopped=1;break;}sleep(1);
        }
        if(!stopped){
            printf("TRIAL_ALIVE_AFTER_%uS %s\n",duration,name);result=0;kill(child,15);
            sleep(1);if(waitpid(child,&status,1)!=child){kill(child,9);waitpid(child,&status,0);}
        }else printf("TRIAL_EARLY_EXIT %s wait_status=%d\n",name,status);
    }else perror("fork");
    close(fd);unlink(address.sun_path);return result;
}
