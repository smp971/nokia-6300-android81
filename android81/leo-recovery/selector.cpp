// SPDX-License-Identifier: Apache-2.0
// PID 1 only: consume a one-shot SD request, then exec the original init.
// No internal filesystem is mounted or formatted by this program.
#include "policy.h"
#include <openssl/sha.h>
#include <linux/loop.h>
#include <linux/fs.h>
#include <sys/ioctl.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace {
const char* card="/leo-early-card";
const char* root="/kaios-root";
const char* request="LEO_KAIOS_SD_ONCE_V1_695d\n";
const char* systemSha="a16d027dba2702fc9114ef3269770c30595a8e9b595329e5b7527588345fbdd6";
int loopFds[3]={-1,-1,-1};bool attached[3]={false,false,false};
std::vector<std::pair<std::string,int>> protectedNodes;
void log(const std::string& s){int f=open("/leo-selector.log",O_WRONLY|O_CREAT|O_APPEND|O_CLOEXEC,0600);if(f>=0){dprintf(f,"%s\n",s.c_str());close(f);}printf("%s\n",s.c_str());}
std::string readText(const std::string& path){int f=open(path.c_str(),O_RDONLY|O_NOFOLLOW|O_CLOEXEC);if(f<0)return "";char b[8192];ssize_t n=read(f,b,sizeof(b));close(f);return n>0&&n<(ssize_t)sizeof(b)?std::string(b,n):"";}
bool node(const std::string& p,unsigned ma,unsigned mi){dev_t d=makedev(ma,mi);if(mknod(p.c_str(),S_IFBLK|0600,d)&&errno!=EEXIST)return false;struct stat st;return !lstat(p.c_str(),&st)&&S_ISBLK(st.st_mode)&&st.st_rdev==d;}
bool image(int i,bool hash) {
    const char* names[]={"system","data","cache"};uint64_t sizes[]={838860800ULL,536870912ULL,67108864ULL};
    std::string p=std::string(card)+"/LeoKaiOS/"+names[i]+".img";
    int f=open(p.c_str(),(i?O_RDWR:O_RDONLY)|O_NOFOLLOW|O_CLOEXEC);if(f<0)return false;
    struct stat st;unsigned char magic[2];bool ok=!fstat(f,&st)&&S_ISREG(st.st_mode)&&(uint64_t)st.st_size==sizes[i]&&pread(f,magic,2,1080)==2&&magic[0]==0x53&&magic[1]==0xef;
    if(ok&&hash&&i==0){SHA256_CTX ctx;SHA256_Init(&ctx);std::vector<unsigned char> b(262144);ssize_t n;
        while((n=read(f,b.data(),b.size()))>0)SHA256_Update(&ctx,b.data(),n);
        unsigned char raw[32];char text[65];SHA256_Final(raw,&ctx);for(int j=0;j<32;++j)snprintf(text+j*2,3,"%02x",raw[j]);ok=n==0&&std::string(text)==systemSha;
    }
    close(f);return ok;
}
bool mountCard(){
    mkdir(card,0700);if(!node("/leo-sd-block",179,65))return false;
    for(int i=0;i<40;++i){if(!mount("/leo-sd-block",card,"vfat",MS_NOSUID|MS_NODEV|MS_NOEXEC,"utf8")||!mount("/leo-sd-block",card,"ext4",MS_NOSUID|MS_NODEV|MS_NOEXEC,""))return true;usleep(200000);}return false;
}
bool prepared(bool hash){
    if(readText(std::string(card)+"/LeoKaiOS/owner")!="LEO_KAIOS_SD_STORAGE_V1_695d\n")return false;
    for(int i=0;i<3;++i)if(!image(i,hash))return false;
    return true;
}
bool attach(){
    const char* names[]={"system","data","cache"};
    for(int i=0;i<3;++i){std::string p="/leo-loop"+std::to_string(i);if(!node(p,7,i))return false;
        loopFds[i]=open(p.c_str(),O_RDWR|O_CLOEXEC);if(loopFds[i]<0)return false;
        loop_info64 status{};errno=0;if(ioctl(loopFds[i],LOOP_GET_STATUS64,&status)==0||errno!=ENXIO)return false;
        std::string img=std::string(card)+"/LeoKaiOS/"+names[i]+".img";int f=open(img.c_str(),(i?O_RDWR:O_RDONLY)|O_NOFOLLOW|O_CLOEXEC);if(f<0)return false;
        bool ok=ioctl(loopFds[i],LOOP_SET_FD,f)==0;close(f);if(!ok)return false;attached[i]=true;
        status={};status.lo_flags=i?0:LO_FLAGS_READ_ONLY;
        if(ioctl(loopFds[i],LOOP_SET_STATUS64,&status))return false;
    }return true;
}
void detach(){for(int i=2;i>=0;--i)if(loopFds[i]>=0){if(attached[i])ioctl(loopFds[i],LOOP_CLR_FD,0);close(loopFds[i]);loopFds[i]=-1;}}
bool protectInternal(){
    const unsigned parts[]={29,30,32,39},majors[]={179,179,259,259},minors[]={29,30,0,7};
    for(unsigned i=0;i<4;++i){std::string base="/sys/class/block/mmcblk0p"+std::to_string(parts[i]);
        if(readText(base+"/dev")!=std::to_string(majors[i])+":"+std::to_string(minors[i])+"\n")return false;
        std::string p="/leo-protect-"+std::to_string(parts[i]);if(!node(p,majors[i],minors[i]))return false;
        int f=open(p.c_str(),O_RDONLY|O_CLOEXEC),one=1,actual=0;if(f<0)return false;
        if(ioctl(f,BLKROGET,&actual)){close(f);return false;}protectedNodes.push_back({p,actual});
        bool ok=!ioctl(f,BLKROSET,&one)&&!ioctl(f,BLKROGET,&actual)&&actual==1;close(f);if(!ok)return false;
    }return true;
}
void unprotect(){for(auto item:protectedNodes){int f=open(item.first.c_str(),O_RDONLY|O_CLOEXEC);if(f>=0){ioctl(f,BLKROSET,&item.second);close(f);}}protectedNodes.clear();}
bool bootKaiOS(){
    if(!prepared(true)){log("SD images/owner invalid; recovery fallback");return false;}
    if(access("/kaios-root/init",X_OK)||!attach()){log("Cannot attach OS loops");detach();return false;}
    // The card lives outside /dev and /mnt, which stock init remounts.
    mkdir("/kaios-root/leo-sd",0700);
    if(!protectInternal()||mount(card,"/kaios-root/leo-sd",nullptr,MS_MOVE,nullptr)){log("Cannot protect internal OS or move SD");unprotect();detach();return false;}
    // Logs stay on the card; the boot request has already been consumed.
    int f=open("/kaios-root/leo-sd/LeoKaiOS/selector-last.log",O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC,0600);
    if(f>=0){dprintf(f,"KaiOS SD: loops attached; internal boot/system/cache/userdata read-only.\n");fsync(f);close(f);}
    sync();int previousRoot=open("/",O_RDONLY|O_DIRECTORY|O_CLOEXEC);
    if(previousRoot<0||chdir(root)||chroot(".")){log("chroot failed");if(previousRoot>=0)close(previousRoot);chdir("/");mount("/kaios-root/leo-sd",card,nullptr,MS_MOVE,nullptr);unprotect();detach();return false;}
    chdir("/");char* args[]={const_cast<char*>("/init"),nullptr};execv("/init",args);
    // Recover the original RAM root if exec fails; never run a second init.
    if(fchdir(previousRoot)||chroot(".")){for(;;)sleep(5);}
    close(previousRoot);chdir("/");log("KaiOS init exec failed; recovery fallback");
    mount("/kaios-root/leo-sd",card,nullptr,MS_MOVE,nullptr);unprotect();detach();return false;
}
}
int main(int argc,char** argv){
    setvbuf(stdout,nullptr,_IONBF,0);
    bool loops=argc==2&&!strcmp(argv[1],"--probe-loops");
    bool probe=argc==2&&(!strcmp(argv[1],"--probe-sd")||loops);
    if(probe){
        if(getuid()!=0||!leo::correctPhone(readText("/proc/cmdline")))return 2;
        auto marker=readText("/tmp/leo-debug-init");
        if(loops&&marker!="root-adb-v1"&&marker!="root-adb-v1\n")return 2;
        if(!mountCard()){puts("SD unavailable");return 3;}bool ok=prepared(true);
        if(ok&&loops){
            ok=attach()&&protectInternal();
            if(ok){ok=mount(card,"/kaios-root/leo-sd",nullptr,MS_MOVE,nullptr)==0;
                if(ok)ok=mount("/kaios-root/leo-sd",card,nullptr,MS_MOVE,nullptr)==0;}
            const char* paths[]={"/tmp/leo-system-probe","/tmp/leo-data-probe","/tmp/leo-cache-probe"};
            for(int i=0;ok&&i<3;++i){mkdir(paths[i],0700);std::string n="/leo-loop"+std::to_string(i);
                ok=mount(n.c_str(),paths[i],"ext4",MS_NOSUID|MS_NODEV|(i?0:MS_RDONLY),i?"":"noload")==0;
                if(ok){printf("Loop %d ext4 mount OK\n",i);if(umount(paths[i]))ok=false;}}
            unprotect();detach();
        }
        umount(card);
        if(loops){puts(ok?"OS loops, ext4 mounts, SD move and internal RO guards verified; guards restored":"OS loop probe failed; inspect selector log");return ok?0:5;}
        puts(ok?"KaiOS SD images verified; no loops attached, no boot request":"KaiOS SD image verification failed");return ok?0:4;
    }
    if(getpid()!=1)return 2;
    // Kernel command-line words such as "gpt" can be passed as extra init
    // arguments. PID 1 is authoritative; argc==1 would silently skip SD boot.
    if(getpid()==1){
        log("Early selector entered; argc="+std::to_string(argc));
        mkdir("/proc",0555);mkdir("/sys",0555);mount("proc","/proc","proc",0,nullptr);mount("sysfs","/sys","sysfs",0,nullptr);
        if(leo::correctPhone(readText("/proc/cmdline"))&&mountCard()){
            std::string flag=std::string(card)+"/LeoKaiOS/boot-once";
            bool requested=readText(flag)==request;
            if(!requested)log("No valid one-shot SD request; recovery");
            if(requested){if(!unlink(flag.c_str())){sync();log("Consumed KaiOS SD request");bootKaiOS();}else log("Cannot consume boot request");}
            umount(card);
        }else log("Serial/SD unavailable; recovery fallback");
        // Original recovery init recreates these mounts itself.
        umount("/sys");umount("/proc");
    }
    // Restore the original /init IN RAM before exec. Its SELinux relabel and
    // second-stage re-exec then use the original inode, as on the known base.
    // The separate /sbin/leo-boot-selector remains available for SD verification.
    if(rename("/init-recovery","/init")){log("Cannot restore original RAM init");for(;;)sleep(5);}
    execv("/init",argv);for(;;)sleep(5);
}
