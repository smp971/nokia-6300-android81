/* Read-only NAS preference probe. Client-local subscription binding only.
 * No system-selection, data-subscription, provisioning or NV writes.
 * Protocol: pinned Qualcomm network_access_service_v01.{h,c}, messages 0x34/0x45/0x5c.
 */
extern void *dlopen(const char *, int);
extern void *dlsym(void *, const char *);
extern char *dlerror(void);
extern int printf(const char *, ...);
extern int strcmp(const char *, const char *);
static int result(const unsigned char *b,unsigned n) {
    for(unsigned i=0;i+3<=n;){unsigned tag=b[i],len=b[i+1]|b[i+2]<<8;i+=3;if(len>n-i)return -1;
        if(tag==2 && len==4)return (b[i]|b[i+1]<<8)?(b[i+2]|b[i+3]<<8):0;i+=len;}
    return -1;
}
static void fields(const unsigned char *b,unsigned n,int standby) {
    for(unsigned i=0;i+3<=n;){unsigned tag=b[i],len=b[i+1]|b[i+2]<<8;i+=3;if(len>n-i)return;
        int show=standby?(tag>=0x10 && tag<=0x15):(tag==0x11 || tag==0x18 || tag==0x20 || tag==0x21 || tag==0x22);
        if(show && len<=8){unsigned long long v=0;for(unsigned j=0;j<len;j++)v|=(unsigned long long)b[i+j]<<(8*j);printf("field=0x%02x value=0x%llx\n",tag,v);}i+=len;}
}
int main(int argc,char **argv) {
    if(argc!=1)return 2;
    void *cci=dlopen("libqmi_cci.so",2),*svc=dlopen("libqmiservices.so",2);
    if(!cci||!svc)return 1;
    void *(*get)(int,int,int)=dlsym(svc,"nas_get_service_object_internal_v01");
    int (*lookup)(void *,unsigned,void *)=dlsym(cci,"qmi_client_get_service_instance");
    int (*init)(void *,void *,void *,void *,void *,void **)=dlsym(cci,"qmi_client_init");
    int (*send)(void *,unsigned,void *,unsigned,void *,unsigned,unsigned *,unsigned)=dlsym(cci,"qmi_client_send_raw_msg_sync");
    int (*release)(void *)=dlsym(cci,"qmi_client_release");
    if(!get||!lookup||!init||!send||!release)return 1;
    void *obj=0,*client=0;for(int minor=0;minor<512&&!obj;minor++)obj=get(1,minor,6);
    unsigned info[32]={0};unsigned long long os[64]={0};
    if(!obj||lookup(obj,0,info)||init(info,obj,0,0,os,&client))return 1;
    unsigned char response[2048];unsigned size=0;
    int rc=send(client,0x5c,0,0,response,sizeof(response),&size,5000);
    printf("standby transport=%d result=%d\n",rc,rc?-1:result(response,size));if(!rc&&!result(response,size))fields(response,size,1);
    for(unsigned slot=0;slot<2;slot++){
        /* Bind only this new diagnostic client. Does not change the modem's DDS. */
        unsigned char bind[]={1,1,0,(unsigned char)slot};size=0;
        rc=send(client,0x45,bind,sizeof(bind),response,sizeof(response),&size,5000);
        printf("slot=%u bind transport=%d result=%d\n",slot,rc,rc?-1:result(response,size));if(rc||result(response,size))continue;
        size=0;rc=send(client,0x34,0,0,response,sizeof(response),&size,5000);
        printf("slot=%u selection transport=%d result=%d\n",slot,rc,rc?-1:result(response,size));if(!rc&&!result(response,size))fields(response,size,0);
    }
    release(client);return 0;
}
