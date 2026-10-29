/* Selected SIM 2 IMS setting trial. QMI layout: libqmi qmi-service-ims.json.
 * Only Bind(0x98), Get(0x90), Set(0x8f) WFC-enable TLV 0x14 are used.
 * No APN, LTE preference, provisioning flag, carrier profile or raw NV access.
 * WFC user settings may persist: use trial-wfc.py, never enable without rollback.
 */
extern void *dlopen(const char *,int);
extern void *dlsym(void *,const char *);
extern int printf(const char *,...);
extern int strcmp(const char *,const char *);
static int field(const unsigned char *b,unsigned n,unsigned wanted,unsigned *value) {
    for(unsigned i=0;i+3<=n;){unsigned tag=b[i],len=b[i+1]|b[i+2]<<8;i+=3;
        if(len>n-i)return -1;
        if(tag==wanted&&len>0&&len<=4){*value=0;for(unsigned j=0;j<len;j++)*value|=(unsigned)b[i+j]<<(8*j);return 0;}
        i+=len;
    }return -1;
}
static int good(const unsigned char *b,unsigned n){unsigned v=1;return !field(b,n,2,&v)&&v==0;}
int main(int argc,char **argv){
    int set=-1;
    if(argc==2&&!strcmp(argv[1],"--enable-trial"))set=1;
    else if(argc==2&&!strcmp(argv[1],"--restore-disabled"))set=0;
    else if(argc!=1)return 2;
    void *lib=dlopen("libqmi_cci.so",2),*svc=dlopen("libqmiservices.so",2);
    if(!lib||!svc)return 3;
    void *(*get)(int,int,int)=dlsym(svc,"imss_get_service_object_internal_v01");
    int (*lookup)(void *,unsigned,void *)=dlsym(lib,"qmi_client_get_service_instance");
    int (*init)(void *,void *,void *,void *,void *,void **)=dlsym(lib,"qmi_client_init");
    int (*send)(void *,unsigned,void *,unsigned,void *,unsigned,unsigned *,unsigned)=dlsym(lib,"qmi_client_send_raw_msg_sync");
    int (*release)(void *)=dlsym(lib,"qmi_client_release");
    if(!get||!lookup||!init||!send||!release)return 3;
    void *obj=0,*client=0;for(int minor=0;minor<512&&!obj;minor++)obj=get(1,minor,6);
    unsigned info[32]={0};unsigned long long os[64]={0};
    if(!obj||lookup(obj,0,info)||init(info,obj,0,0,os,&client))return 4;
    unsigned char b[2048],bind[]={1,4,0,1,0,0,0};unsigned size=0,wfc=9,voice=9,reg=9;
    int rc=send(client,0x98,bind,sizeof(bind),b,sizeof(b),&size,5000);
    if(rc||!good(b,size)){release(client);return 5;}
    size=0;rc=send(client,0x90,0,0,b,sizeof(b),&size,5000);
    if(rc||!good(b,size)||field(b,size,0x15,&wfc)){release(client);return 6;}
    field(b,size,0x11,&voice);field(b,size,0x19,&reg);
    printf("binding=1 wfc=%u voice=%u registration_enabled=%u\n",wfc,voice,reg);
    if(wfc>1){release(client);return 7;}
    if(set==1&&(wfc!=0||voice!=1||reg!=1)){release(client);return 7;}
    if(set>=0){
        unsigned char request[]={0x14,1,0,(unsigned char)set};size=0;
        rc=send(client,0x8f,request,sizeof(request),b,sizeof(b),&size,5000);
        printf("set_wfc=%d transport=%d accepted=%u\n",set,rc,!rc&&good(b,size));
        if(rc||!good(b,size)){release(client);return 8;}
        size=0;rc=send(client,0x90,0,0,b,sizeof(b),&size,5000);
        if(rc||!good(b,size)||field(b,size,0x15,&wfc)||wfc!=(unsigned)set){release(client);return 9;}
        printf("verified_wfc=%u\n",wfc);
    }
    release(client);client=0;
    get=dlsym(svc,"imsa_get_service_object_internal_v01");obj=0;
    if(get)for(int minor=0;minor<512&&!obj;minor++)obj=get(1,minor,6);
    if(!obj||lookup(obj,0,info)||init(info,obj,0,0,os,&client))return set==0?0:10;
    unsigned char appbind[]={0x10,4,0,1,0,0,0};size=0;
    rc=send(client,0x33,appbind,sizeof(appbind),b,sizeof(b),&size,5000);
    if(rc||!good(b,size)){release(client);return set==0?0:11;}
    for(unsigned request=0x20;request<=0x21;request++){
        size=0;rc=send(client,request,0,0,b,sizeof(b),&size,5000);
        printf("imsa request=0x%x transport=%d success=%u\n",request,rc,!rc&&good(b,size));
        if(!rc&&good(b,size))for(unsigned tag=0x10;tag<=0x14;tag++){
            if(request==0x20&&tag!=0x12&&tag!=0x14)continue;
            if(request==0x21&&tag!=0x11&&tag!=0x14)continue;
            unsigned value;if(!field(b,size,tag,&value))printf("imsa field=0x%x value=%u\n",tag,value);
        }
    }
    release(client);return 0;
}
