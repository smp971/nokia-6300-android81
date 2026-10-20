/* Explicit local root shell for the owner's Leo terminal. No network listener.
 * The unprivileged client receives only shell pipes; root credentials stay in
 * the init-owned broker. Other application UIDs are rejected with SO_PEERCRED.
 */
#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <signal.h>
#include <poll.h>

static const char endpoint[]="leo.terminal.root.v1";
static int transfer(int fd,void *buffer,size_t size,int writing){char *p=buffer;while(size){ssize_t n=writing?write(fd,p,size):read(fd,p,size);if(n<0&&errno==EINTR)continue;if(n<=0)return -1;p+=n;size-=n;}return 0;}
static socklen_t address(struct sockaddr_un *a){memset(a,0,sizeof(*a));a->sun_family=AF_UNIX;memcpy(a->sun_path+1,endpoint,sizeof(endpoint));return offsetof(struct sockaddr_un,sun_path)+1+sizeof(endpoint);}
static uid_t allowed_uid(void){
    int fd=open("/system/etc/leo-terminal-root.uid",O_RDONLY|O_NOFOLLOW|O_CLOEXEC);if(fd<0)return (uid_t)-1;
    struct stat s;char value[32]={0};ssize_t n=read(fd,value,sizeof(value)-1);int ok=!fstat(fd,&s)&&S_ISREG(s.st_mode)&&s.st_uid==0&&!(s.st_mode&0022);close(fd);
    char *end;unsigned long uid=strtoul(value,&end,10);if(!ok||n<=0||uid<10000||uid>19999||(*end!='\n'&&*end!=0))return (uid_t)-1;return (uid_t)uid;
}
static void shell_connection(int fd){
    struct ucred peer;socklen_t bytes=sizeof(peer);uid_t allowed=allowed_uid();
    if(getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&peer,&bytes)||allowed==(uid_t)-1||(peer.uid!=allowed&&peer.uid!=0)){close(fd);_exit(1);}
    alarm(5);uint32_t length;if(transfer(fd,&length,4,0))_exit(1);if(length>4096)_exit(1);char command[4097]={0};if(length&&transfer(fd,command,length,0))_exit(1);alarm(0);
    int input[2],output[2];if(pipe2(input,O_CLOEXEC)||pipe2(output,O_CLOEXEC))_exit(1);
    signal(SIGCHLD,SIG_DFL);pid_t child=fork();if(child<0)_exit(1);
    if(!child){setsid();dup2(input[0],0);dup2(output[1],1);dup2(output[1],2);close(input[0]);close(input[1]);close(output[0]);close(output[1]);close(fd);umask(077);signal(SIGPIPE,SIG_DFL);unsetenv("LD_PRELOAD");unsetenv("IFS");unsetenv("ENV");setenv("PATH","/system/bin:/system/xbin:/vendor/bin",1);if(length)execl("/system/bin/sh","sh","-c",command,(char*)NULL);else execl("/system/bin/sh","sh",(char*)NULL);_exit(127);}
    close(input[0]);close(output[1]);
    int pipes[2]={output[0],input[1]};char control[CMSG_SPACE(sizeof(pipes))];memset(control,0,sizeof(control));char ack='L';struct iovec io={&ack,1};struct msghdr msg;memset(&msg,0,sizeof(msg));msg.msg_iov=&io;msg.msg_iovlen=1;msg.msg_control=control;msg.msg_controllen=sizeof(control);struct cmsghdr *c=CMSG_FIRSTHDR(&msg);c->cmsg_level=SOL_SOCKET;c->cmsg_type=SCM_RIGHTS;c->cmsg_len=CMSG_LEN(sizeof(pipes));memcpy(CMSG_DATA(c),pipes,sizeof(pipes));
    if(sendmsg(fd,&msg,MSG_NOSIGNAL)!=1)kill(child,SIGTERM);close(output[0]);close(input[1]);
    int status;
    for(;;){pid_t done=waitpid(child,&status,WNOHANG);if(done==child)break;if(done<0&&errno!=EINTR)_exit(1);struct pollfd watch={fd,POLLIN,0};int polled=poll(&watch,1,200);char probe;int lost=polled>0&&((watch.revents&(POLLHUP|POLLERR))||((watch.revents&POLLIN)&&recv(fd,&probe,1,MSG_PEEK|MSG_DONTWAIT)==0));if(lost){kill(-child,SIGTERM);kill(child,SIGTERM);usleep(100000);kill(-child,SIGKILL);kill(child,SIGKILL);while(waitpid(child,&status,0)<0)if(errno!=EINTR)_exit(1);break;}}
    unsigned char result=WIFEXITED(status)?WEXITSTATUS(status):128+WTERMSIG(status);transfer(fd,&result,1,1);close(fd);_exit(0);
}
static int serve(int background){
    if(getuid()!=0||geteuid()!=0||allowed_uid()==(uid_t)-1)return 1;
    int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(fd<0)return 1;struct sockaddr_un addr;socklen_t size=address(&addr);
    if(bind(fd,(struct sockaddr*)&addr,size)||listen(fd,4)){close(fd);return 1;}
    if(background&&daemon(0,0))return 1;signal(SIGPIPE,SIG_IGN);signal(SIGCHLD,SIG_IGN);
    for(;;){int connection=accept4(fd,NULL,NULL,SOCK_CLOEXEC);if(connection<0){if(errno==EINTR)continue;return 1;}pid_t child=fork();if(child==0){close(fd);shell_connection(connection);}close(connection);}
}
static int client(int argc,char **argv){
    const char *command=NULL;if(argc==3&&!strcmp(argv[1],"-c"))command=argv[2];else if(argc!=1){fprintf(stderr,"usage: leo-su [-c command]\n");return 2;}
    size_t length=command?strlen(command):0;if(length>4096)return 2;
    int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);struct sockaddr_un addr;socklen_t size=address(&addr);
    if(fd<0||connect(fd,(struct sockaddr*)&addr,size)){fprintf(stderr,"Leo root indisponibil\n");return 1;}
    struct ucred peer;socklen_t bytes=sizeof(peer);if(getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&peer,&bytes)||peer.uid!=0)return 1;
    uint32_t n=(uint32_t)length;if(transfer(fd,&n,4,1)||(length&&transfer(fd,(void*)command,length,1)))return 1;
    char control[CMSG_SPACE(sizeof(int)*2)],ack;struct iovec io={&ack,1};struct msghdr msg;memset(&msg,0,sizeof(msg));msg.msg_iov=&io;msg.msg_iovlen=1;msg.msg_control=control;msg.msg_controllen=sizeof(control);
    if(recvmsg(fd,&msg,0)!=1||ack!='L'){fprintf(stderr,"Leo root: acces refuzat\n");return 1;}
    struct cmsghdr *c=CMSG_FIRSTHDR(&msg);if(!c||c->cmsg_level!=SOL_SOCKET||c->cmsg_type!=SCM_RIGHTS||c->cmsg_len!=CMSG_LEN(sizeof(int)*2))return 1;int pipes[2];memcpy(pipes,CMSG_DATA(c),sizeof(pipes));
    signal(SIGPIPE,SIG_IGN);int input_open=command==NULL;unsigned char result=1;int completed=0;if(!input_open)close(pipes[1]);
    for(;;){struct pollfd fds[3]={{pipes[0],POLLIN,0},{input_open?0:-1,POLLIN,0},{fd,POLLIN,0}};if(poll(fds,3,-1)<0){if(errno==EINTR)continue;break;}
        char data[4096];if(fds[0].revents&(POLLIN|POLLHUP)){ssize_t count=read(pipes[0],data,sizeof(data));if(count>0){if(transfer(1,data,count,1))break;}else{if(!completed)transfer(fd,&result,1,0);completed=1;break;}}
        if(input_open&&(fds[1].revents&(POLLIN|POLLHUP))){ssize_t count=read(0,data,sizeof(data));if(count>0){if(transfer(pipes[1],data,count,1))break;}else{close(pipes[1]);input_open=0;}}
        if(fds[2].revents&(POLLIN|POLLHUP)){if(!transfer(fd,&result,1,0))completed=1;fcntl(pipes[0],F_SETFL,O_NONBLOCK);ssize_t count;int limit=16;while(limit--&&(count=read(pipes[0],data,sizeof(data)))>0)transfer(1,data,count,1);break;}
    }
    if(input_open)close(pipes[1]);close(pipes[0]);close(fd);return completed?result:1;
}
int main(int argc,char **argv){if(argc==2&&(!strcmp(argv[1],"--serve")||!strcmp(argv[1],"--daemon")))return serve(!strcmp(argv[1],"--daemon"));return client(argc,argv);}
