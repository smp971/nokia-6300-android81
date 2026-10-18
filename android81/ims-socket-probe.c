/* Read-only Qualcomm legacy IMS protocol probe. No registration/set/call requests.
 * Wire format reference: Qualcomm imsIF.proto (MsgTag, requests 1 and 29).
 */
/* ARM32 Linux/Bionic ABI, matching the headerless bring-up toolchain. */
#define AF_UNIX 1
#define SOCK_STREAM 1
#define SOL_SOCKET 1
#define SO_RCVTIMEO 20
struct timeval { long tv_sec, tv_usec; };
struct sockaddr { unsigned short family; char data[14]; };
struct sockaddr_un { unsigned short sun_family; char sun_path[108]; };
extern int socket(int,int,int),setsockopt(int,int,int,const void *,unsigned);
extern int connect(int,const struct sockaddr *,unsigned),read(int,void *,unsigned);
extern int write(int,const void *,unsigned),close(int),puts(const char *);
extern int printf(const char *,...),snprintf(char *,unsigned,const char *,...);
extern int strcmp(const char *,const char *);
extern void perror(const char *);
extern void *memset(void *,int,unsigned),*memcpy(void *,const void *,unsigned);

static int exact(int fd,void *buffer,unsigned length) {
    unsigned done=0;
    while(done<length){int n=read(fd,(char *)buffer+done,length-done);if(n<=0)return -1;done+=n;}
    return 0;
}
static unsigned varint(const unsigned char *b,unsigned end,unsigned *p) {
    unsigned value=0,shift=0;
    while(*p<end && shift<35){unsigned c=b[(*p)++];value|=(c&127)<<shift;if(!(c&128))return value;shift+=7;}
    *p=end;return 0;
}
static unsigned scalar(const unsigned char *b,unsigned length,unsigned field) {
    unsigned p=0;
    while(p<length){unsigned key=varint(b,length,&p),wire=key&7,v=0;
        if(wire==0)v=varint(b,length,&p);
        else if(wire==5){if(length-p<4)break;memcpy(&v,b+p,4);p+=4;}
        else if(wire==2){unsigned size=varint(b,length,&p);if(size>length-p)break;p+=size;}
        else break;
        if((key>>3)==field && (wire==0 || wire==5))return v;
    }
    return 0xffffffffu;
}
static void services(const unsigned char *b,unsigned length) {
    unsigned p=0;
    while(p<length){unsigned key=varint(b,length,&p);
        if((key&7)!=2)break;
        unsigned size=varint(b,length,&p);if(size>length-p)break;
        if((key>>3)==2){
            printf(" service(call_type=%u",scalar(b+p,size,3));
            unsigned at=p,end=p+size;
            while(at<end){unsigned k=varint(b,end,&at),wire=k&7;
                if(wire==0){varint(b,end,&at);}
                else if(wire==5){if(end-at<4)break;at+=4;}
                else if(wire==2){unsigned n=varint(b,end,&at);if(n>end-at)break;
                    if((k>>3)==7)printf(",network=%u,status=%u",scalar(b+at,n,1),scalar(b+at,n,2));
                    at+=n;
                }else break;
            }
            printf(")");
        }
        p+=size;
    }
}
int main(int argc,char **argv) {
    if(argc!=2 || (strcmp(argv[1],"0") && strcmp(argv[1],"1")))return 2;
    int fd=socket(AF_UNIX,SOCK_STREAM,0);if(fd<0){perror("socket");return 1;}
    struct timeval timeout={5,0};setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
    struct sockaddr_un address;memset(&address,0,sizeof(address));address.sun_family=AF_UNIX;
    snprintf(address.sun_path,sizeof(address.sun_path),"/dev/socket/qmux_radio/rild_ims%s",argv[1]);
    if(connect(fd,(struct sockaddr *)&address,sizeof(address))){perror("connect");close(fd);return 1;}
    unsigned requests[]={1,29};
    for(unsigned q=0;q<2;q++){
        unsigned char packet[]={0,0,0,12,11,13,1,0,0,0,16,1,24,1,32,16};
        packet[6]=q+1;packet[13]=requests[q];
        if(write(fd,packet,sizeof(packet))!=sizeof(packet)){perror("write");return 1;}
        int found=0;
        for(unsigned attempt=0;attempt<24;attempt++){
            unsigned char size[4],frame[8192];
            if(exact(fd,size,4)){perror("read header");return 1;}
            unsigned length=((unsigned)size[0]<<24)|((unsigned)size[1]<<16)|((unsigned)size[2]<<8)|size[3];
            if(length<1 || length>sizeof(frame) || exact(fd,frame,length) || frame[0]>length-1){puts("Invalid frame");return 1;}
            unsigned tag=frame[0],type=scalar(frame+1,tag,2),id=scalar(frame+1,tag,3);
            if(type!=2 || id!=requests[q] || scalar(frame+1,tag,1)!=q+1)continue;
            unsigned error=scalar(frame+1,tag,4),payload=length-1-tag;
            printf("slot=%s query=%u error=%u payload_bytes=%u",argv[1],id,error,payload);
            if(id==1 && !error)printf(" registration_state=%u error_code=%u radio_tech=%u",scalar(frame+1+tag,payload,1),scalar(frame+1+tag,payload,2),scalar(frame+1+tag,payload,4));
            if(id==29 && !error)services(frame+1+tag,payload);
            puts("");found=1;break;
        }
        if(!found){puts("No matching response");return 1;}
    }
    close(fd);return 0;
}
