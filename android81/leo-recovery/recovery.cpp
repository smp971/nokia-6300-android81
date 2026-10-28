// SPDX-License-Identifier: Apache-2.0
// Leo Recovery prototype. Standalone static recovery, no Android framework.
// Writes are restricted to compiled, locally audited package SHA-256 pins.
#include "policy.h"
#include "catalog_generated.h"
#include <minui/minui.h>
#include <ziparchive/zip_archive.h>
#include <openssl/sha.h>
#include <sys/mount.h>
#include <sys/reboot.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <sys/sysmacros.h>
#include <sys/wait.h>
#include <linux/input.h>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <poll.h>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <string>
#include <vector>
#include <algorithm>

namespace {
using leo::Image;using leo::Bundle;
const char* CARD="/sdcard";
const char* ROOT="/sdcard/LeoRecovery";
std::string error;
const GRFont* font;
int width,height,fh,fw;
std::vector<int> inputs;
bool graphics=false;

struct Fd {
    int fd;
    explicit Fd(int n=-1):fd(n){}
    ~Fd(){if(fd>=0)close(fd);}
    Fd(const Fd&)=delete;Fd& operator=(const Fd&)=delete;
};
void log(const std::string& text) {
    FILE* f=fopen("/tmp/leo-recovery.log","a");
    if(f){fprintf(f,"%s\n",text.c_str());fclose(f);}printf("%s\n",text.c_str());
}
bool fail(const std::string& text){error=text;log("ERROR: "+text);return false;}
std::string readText(const std::string& path) {
    Fd file(open(path.c_str(),O_RDONLY|O_CLOEXEC|O_NOFOLLOW));
    if(file.fd<0)return "";
    std::string result;char data[4096];ssize_t n;
    while((n=read(file.fd,data,sizeof(data)))>0){result.append(data,n);if(result.size()>262144)return "";}
    return n==0?result:"";
}
bool directory(const std::string& path) {
    if(mkdir(path.c_str(),0700)&&errno!=EEXIST)return fail("Cannot create "+path);
    struct stat info;
    return (!lstat(path.c_str(),&info)&&S_ISDIR(info.st_mode)) || fail("Unsafe directory");
}
void text(int x,int y,const std::string& value,bool bold=false) {
    unsigned limit=std::max(1,(width-x-4)/fw);
    gr_text(font,x,y,value.substr(0,limit).c_str(),bold);
}
void frame(const std::string& title,const std::string& footer="Select       Back") {
    gr_color(11,17,27,255);gr_clear();
    gr_color(20,43,51,255);gr_fill(0,0,width,56);
    gr_color(65,223,207,255);text(8,4,"LEO RECOVERY",true);
    gr_color(195,207,216,255);text(8,29,title);
    gr_color(28,44,57,255);gr_fill(0,height-fh-6,width,height);
    gr_color(163,210,216,255);text(5,height-fh-4,footer);
}
void progress(const std::string& title,const std::string& detail,uint64_t done=0,uint64_t total=0) {
    if(!graphics)return;
    frame(title,"Please wait...");
    gr_color(230,237,244,255);text(8,83,detail);
    gr_color(36,55,68,255);gr_fill(10,131,width-10,143);
    if(total){gr_color(65,223,207,255);gr_fill(10,131,10+(width-20)*std::min(done,total)/total,143);
        gr_color(180,197,208,255);text(8,162,std::to_string(done*100/total)+"%");}
    gr_flip();
}
void openInputs() {
    DIR* dir=opendir("/dev/input");if(!dir)return;dirent* item;
    while((item=readdir(dir)))if(!strncmp(item->d_name,"event",5)) {
        std::string path=std::string("/dev/input/")+item->d_name;
        int fd=open(path.c_str(),O_RDONLY|O_NONBLOCK|O_CLOEXEC|O_NOFOLLOW);
        if(fd>=0)inputs.push_back(fd);
    }
    closedir(dir);
}
void drainKeys(){for(int fd:inputs){input_event event;while(read(fd,&event,sizeof(event))==sizeof(event)){} }}
leo::Key waitKey() {
    std::vector<pollfd> watches;for(int fd:inputs)watches.push_back({fd,POLLIN,0});
    for(;;) {
        if(poll(watches.data(),watches.size(),1000)<0&&errno!=EINTR)return leo::Key::Back;
        for(auto watch:watches)if(watch.revents&POLLIN){input_event event;
            while(read(watch.fd,&event,sizeof(event))==sizeof(event))
                if(event.type==EV_KEY&&event.value==0){auto key=leo::key(event.code);if(key!=leo::Key::None)return key;}}
    }
}
int menu(const std::string& title,const std::vector<std::string>& items) {
    int selected=0;drainKeys();
    for(;;) {
        frame(title);int rows=std::max(1,(height-fh-72)/(fh+7));
        int first=(selected/rows)*rows;
        for(int i=first;i<(int)items.size()&&i<first+rows;++i) {
            int y=65+(i-first)*(fh+7);
            if(i==selected){gr_color(30,89,96,255);gr_fill(4,y-1,width-4,y+fh+4);}
            gr_color(i==selected?102:211,i==selected?245:222,i==selected?221:232,255);
            text(9,y,items[i],i==selected);
        }
        gr_flip();auto key=waitKey();
        if(key==leo::Key::Up)selected=(selected+(int)items.size()-1)%items.size();
        if(key==leo::Key::Down)selected=(selected+1)%items.size();
        if(key==leo::Key::Back)return -1;
        if(key==leo::Key::Accept)return selected;
    }
}
void message(const std::string& title,const std::string& body) {
    frame(title,"BACK / OK to return");gr_color(224,233,240,255);
    unsigned columns=std::max(1,(width-16)/fw);unsigned at=0;int y=70;
    while(at<body.size()&&y<height-fh-10){unsigned count=std::min(columns,(unsigned)body.size()-at);
        if(at+count<body.size()){auto space=body.rfind(' ',at+count);if(space!=std::string::npos&&space>at)count=space-at;}
        text(8,y,body.substr(at,count));y+=fh+3;at+=count;while(at<body.size()&&body[at]==' ')++at;}
    gr_flip();drainKeys();waitKey();
}
bool hashFd(int fd,uint64_t bytes,std::string* result,const std::string& title) {
    if(lseek64(fd,0,SEEK_SET)!=0)return fail("Cannot seek image");
    SHA256_CTX context;SHA256_Init(&context);std::vector<unsigned char> buffer(262144);
    uint64_t done=0;unsigned tick=0;
    while(done<bytes){ssize_t n=read(fd,buffer.data(),std::min<uint64_t>(buffer.size(),bytes-done));
        if(n<0&&errno==EINTR)continue;if(n<=0)return fail("Incomplete image read");
        SHA256_Update(&context,buffer.data(),n);done+=n;
        if((++tick%64)==0)progress("Verifying",title,done,bytes);
    }
    unsigned char raw[SHA256_DIGEST_LENGTH];SHA256_Final(raw,&context);char out[65];
    for(unsigned i=0;i<sizeof(raw);++i)snprintf(out+2*i,3,"%02x",raw[i]);
    *result=out;return true;
}
bool checkFile(const std::string& path,uint64_t bytes,const std::string& wanted) {
    Fd fd(open(path.c_str(),O_RDONLY|O_NOFOLLOW|O_CLOEXEC));struct stat st;
    if(fd.fd<0||fstat(fd.fd,&st)||!S_ISREG(st.st_mode)||(uint64_t)st.st_size!=bytes)return fail("Missing/wrong file: "+path.substr(path.find_last_of('/')+1));
    std::string actual;return hashFd(fd.fd,bytes,&actual,"Backup/image")&&(actual==wanted||fail("Image SHA-256 mismatch"));
}
bool geometry(const Image& image) {
    std::string base="/sys/class/block/mmcblk0p"+std::to_string(image.partition)+"/";
    auto info=readText(base+"uevent");auto size=readText(base+"size");
    if(info.empty()||size.empty()||!leo::token(info,"PARTNAME="+std::string(image.name))||
       !leo::token(info,"MAJOR="+std::to_string(image.major))||!leo::token(info,"MINOR="+std::to_string(image.minor))||
       strtoull(size.c_str(),nullptr,10)*512!=image.bytes)return fail("Partition geometry mismatch");
    return true;
}
std::string node(const Image& image) {
    return "/dev/leo-recovery-"+std::string(image.name);
}
bool deviceNode(const Image& image) {
    if(!geometry(image))return false;
    auto path=node(image);dev_t expected=makedev(image.major,image.minor);
    if(mknod(path.c_str(),S_IFBLK|0600,expected)&&errno!=EEXIST)return fail("Cannot create block alias");
    struct stat st;if(lstat(path.c_str(),&st)||!S_ISBLK(st.st_mode)||st.st_rdev!=expected)return fail("Wrong block alias");
    return true;
}
bool noAndroid() {
    DIR* proc=opendir("/proc");if(!proc)return fail("Cannot inspect processes");
    bool found=false;dirent* item;
    while((item=readdir(proc)))if(item->d_name[0]>='0'&&item->d_name[0]<='9') {
        auto name=readText(std::string("/proc/")+item->d_name+"/comm");
        if(name=="system_server\n"){found=true;break;}
    }
    closedir(proc);return !found||fail("Android is running");
}
bool targetGuards(const Bundle& bundle) {
    if(getuid()!=0||!leo::correctPhone(readText("/proc/cmdline")))return fail("Wrong phone or not root");
    if(!leo::bootLast(bundle)||!noAndroid())return fail("Unsafe install context");
    auto parent=readText("/proc/1/mountinfo"),own=readText("/proc/self/mountinfo");
    if(parent.empty()||own.empty()||leo::mountedTargets(parent,bundle.wipe)||leo::mountedTargets(own,bundle.wipe))
        return fail("Unmount system/data first");
    for(unsigned i=0;i<bundle.count;++i)if(!deviceNode(bundle.images[i]))return false;
    return true;
}
bool sdMounted() {
    std::istringstream rows(readText("/proc/self/mountinfo"));std::string line;
    while(std::getline(rows,line)){std::istringstream row(line);std::string id,parent,dev,root,path,options;
        if(row>>id>>parent>>dev>>root>>path>>options)
            if(path==CARD)return dev=="179:65"&&root=="/"&&leo::mountReadWrite(options);
    }
    return false;
}
bool mountCard() {
    // mountinfo options are comma-separated. Check a real external SD mount;
    // never stage to a same-named directory on userdata or ramdisk.
    auto info=readText("/sys/class/block/mmcblk1p1/uevent");
    if(!leo::token(info,"MAJOR=179")||!leo::token(info,"MINOR=65"))return fail("External SD not found");
    if(!directory(CARD))return false;
    if(!sdMounted()) {
        if(readText("/proc/self/mountinfo").find(" /sdcard ")!=std::string::npos)return fail("SD mount is read-only/wrong device");
        if(readText("/proc/self/mountinfo").find("179:65 ")!=std::string::npos)return fail("SD mounted elsewhere/read-only");
        const char* alias="/dev/leo-recovery-card";struct stat st;dev_t dev=makedev(179,65);
        if(mknod(alias,S_IFBLK|0600,dev)&&errno!=EEXIST)return fail("Cannot create SD alias");
        if(lstat(alias,&st)||!S_ISBLK(st.st_mode)||st.st_rdev!=dev)return fail("Unsafe SD alias");
        if(mount(alias,CARD,"vfat",MS_NODEV|MS_NOSUID|MS_NOEXEC,"utf8")&&
           mount(alias,CARD,"ext4",MS_NODEV|MS_NOSUID|MS_NOEXEC,""))return fail("Cannot mount SD; not formatted");
    }
    return sdMounted()&&directory(ROOT);
}
bool freeSpace(uint64_t bytes) {
    struct statvfs st;if(statvfs(CARD,&st))return fail("Cannot read free SD space");
    return (uint64_t)st.f_bavail*st.f_frsize>=bytes||fail("Not enough free SD space");
}
bool powerReady() {
    auto capacity=readText("/sys/class/power_supply/battery/capacity");
    auto online=readText("/sys/class/power_supply/usb/online");
    return online=="1\n"||(!capacity.empty()&&atoi(capacity.c_str())>=30)||fail("Charge battery above 30%");
}
bool stockBackups() {
    for(const auto& item:kOriginals)if(!checkFile(std::string(ROOT)+"/stock-backups/"+item.filename,item.bytes,item.sha))return false;
    return true;
}
bool copyFd(int source,int dest,uint64_t bytes,const std::string& title) {
    if(lseek64(source,0,SEEK_SET)!=0)return fail("Cannot seek source");
    std::vector<unsigned char> buffer(262144);uint64_t done=0;unsigned tick=0;
    while(done<bytes){ssize_t n=read(source,buffer.data(),std::min<uint64_t>(buffer.size(),bytes-done));
        if(n<0&&errno==EINTR)continue;if(n<=0)return fail("Source read failed");
        ssize_t at=0;while(at<n){ssize_t written=write(dest,buffer.data()+at,n-at);
            if(written<0&&errno==EINTR)continue;if(written<=0)return fail("Target write failed");at+=written;}
        done+=n;if((++tick%16)==0)progress("Copying",title,done,bytes);
    }
    return !fsync(dest)||fail("Target sync failed");
}
struct Archive {
    ZipArchiveHandle handle=nullptr;int fd=-1;
    ~Archive(){if(handle)CloseArchive(handle);if(fd>=0)close(fd);}
};
bool openPackage(const Bundle& bundle,Archive* zip) {
    auto path=std::string(ROOT)+"/packages/"+bundle.filename;
    Fd fd(open(path.c_str(),O_RDONLY|O_NOFOLLOW|O_CLOEXEC));struct stat st;
    if(fd.fd<0||fstat(fd.fd,&st)||!S_ISREG(st.st_mode)||(uint64_t)st.st_size!=bundle.archiveBytes)return fail("Package missing/wrong size");
    std::string actual;
    if(!hashFd(fd.fd,st.st_size,&actual,"Package")||actual!=bundle.sha)return fail("Unapproved package SHA-256");
    if(OpenArchiveFd(fd.fd,path.c_str(),&zip->handle,false)!=0)return fail("Cannot read package ZIP");
    zip->fd=fd.fd;fd.fd=-1;
    return true;
}
bool extract(const Bundle& bundle,Archive& zip,const std::string& stage) {
    if(!freeSpace(leo::stagingBytes(bundle)))return false;
    for(unsigned i=0;i<bundle.count;++i) {
        const auto& image=bundle.images[i];std::string name="images/"+std::string(image.name)+".img";
        ZipEntry entry;
        if(FindEntry(zip.handle,ZipString(name.c_str()),&entry)||entry.uncompressed_length!=image.bytes)return fail("ZIP image size mismatch");
        auto path=stage+"/"+image.name+".img";
        Fd output(open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600));
        if(output.fd<0)return fail("Staging file already exists");
        progress("Extracting",image.name);
        if(ExtractEntryToFile(zip.handle,&entry,output.fd)||fsync(output.fd))return fail("ZIP extraction failed");
        if(!checkFile(path,image.bytes,image.sha))return false;
    }
    return true;
}
void installBundle(const Bundle& bundle) {
    if(!mountCard()||!targetGuards(bundle)){message("Cannot install",error);return;}
    int action=menu(bundle.title,{"Back","Verify package","Install package"});
    if(action<1)return;
    if(action==1) {
        Archive zip;if(openPackage(bundle,&zip))message("Verified","Approved package. No partitions written.");else message("Verification failed",error);
        return;
    }
    if(menu(bundle.wipe?"Erase ALL data?":"Replace boot/system?",{"Cancel","Confirm installation"})!=1)return;
    if(!powerReady()||!stockBackups()){message("Installation refused",error);return;}
    Archive zip;
    if(!openPackage(bundle,&zip)){message("Installation refused",error);return;}
    std::string stage=std::string(ROOT)+"/stage-"+std::to_string(time(nullptr))+"-"+std::to_string(getpid());
    if(mkdir(stage.c_str(),0700)){message("Cannot stage","Existing staging retained. Reboot and inspect SD.");return;}
    if(!extract(bundle,zip,stage)||!targetGuards(bundle)){message("Nothing flashed",error);return;}
    // All images are extracted and checked BEFORE the first partition write.
    for(unsigned i=0;i<bundle.count;++i) {
        const auto& image=bundle.images[i];
        if(!targetGuards(bundle)){message("STOP - keep recovery",error);return;}
        Fd source(open((stage+"/"+image.name+".img").c_str(),O_RDONLY|O_NOFOLLOW|O_CLOEXEC));
        Fd target(open(node(image).c_str(),O_RDWR|O_NOFOLLOW|O_CLOEXEC));
        log("Writing "+std::string(image.name));
        if(source.fd<0||target.fd<0||!copyFd(source.fd,target.fd,image.bytes,image.name)){message("STOP - keep recovery",error);return;}
        std::string actual;
        if(!hashFd(target.fd,image.bytes,&actual,image.name)||actual!=image.sha){message("STOP - keep recovery","Written partition hash mismatch. Do not reboot.");return;}
        log("Verified "+std::string(image.name));
    }
    sync();log("Install completed. No automatic reboot.");
    message("Installation complete",bundle.wipe?"All images verified. Select Reboot when ready.":"Stock boot/system verified. Factory reset is still required in Stock tools.");
}
void backup() {
    if(!mountCard()||getuid()!=0||!leo::correctPhone(readText("/proc/cmdline"))||!noAndroid()) {message("Backup unavailable",error);return;}
    if(!freeSpace(940u*1024u*1024u)){message("Backup unavailable",error);return;}
    if(menu("BOOT/SYSTEM/RECOVERY",{"Cancel","Create backup on SD"})!=1)return;
    std::string dir=std::string(ROOT)+"/backup-"+std::to_string(time(nullptr))+"-"+std::to_string(getpid());
    if(mkdir(dir.c_str(),0700)){message("Backup failed","Existing directory retained");return;}
    Fd report(open((dir+"/SHA256SUMS").c_str(),O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0600));
    if(report.fd<0){message("Backup failed","Cannot create checksum report");return;}
    for(const auto& image:kBackupImages) {
        if(!deviceNode(image)){message("Backup failed",error);return;}
        Fd source(open(node(image).c_str(),O_RDONLY|O_NOFOLLOW|O_CLOEXEC));
        std::string path=dir+"/"+image.name+".img";
        Fd dest(open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600));
        if(source.fd<0||dest.fd<0||!copyFd(source.fd,dest.fd,image.bytes,image.name)){message("Backup incomplete",error);return;}
        std::string digest,again;
        if(!hashFd(source.fd,image.bytes,&digest,image.name)||!checkFile(path,image.bytes,digest)||
           !hashFd(source.fd,image.bytes,&again,image.name)||again!=digest){message("Backup incomplete","Source changed or readback failed");return;}
        dprintf(report.fd,"%s  %s.img\n",digest.c_str(),image.name);
    }
    if(fsync(report.fd)){message("Backup incomplete","Checksum sync failed");return;}
    sync();log("Backup complete: "+dir);message("Backup complete","Boot, system, recovery saved. User data and radio are NOT included.");
}
void diagnostics() {
    int choice=menu("Diagnostics",{"Back","Mount external SD","Save recovery log","Device information"});
    if(choice==1)message(mountCard()?"SD mounted":"SD unavailable",error.empty()?"/sdcard/LeoRecovery":error);
    if(choice==2){if(!mountCard()){message("Log not saved",error);return;}
        auto data=readText("/tmp/leo-recovery.log");auto path=std::string(ROOT)+"/recovery-"+std::to_string(time(nullptr))+".log";
        Fd out(open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600));
        bool ok=out.fd>=0&&write(out.fd,data.data(),data.size())==(ssize_t)data.size()&&!fsync(out.fd);
        message(ok?"Log saved":"Log failed",ok?"See LeoRecovery on SD":"Cannot write recovery log");}
    if(choice==3)message("Device information",std::string("Nokia 6300 TA-1286. Root ADB. No Android framework. ")+(leo::correctPhone(readText("/proc/cmdline"))?"Serial matches 695d.":"WRONG SERIAL - DO NOT FLASH."));
}
}

int main() {
    // Do not process Android OTA/BCB/cache commands or auto-wipe on startup.
    mkdir("/tmp",0755);log("Leo Recovery 0.1 prototype; no automatic writes");
    if(gr_init()!=0){log("Framebuffer unavailable. Root ADB remains available.");for(;;)pause();}
    graphics=true;font=gr_sys_font();gr_font_size(font,&fw,&fh);width=gr_fb_width();height=gr_fb_height();
    if(width<160||height<200){log("Unsupported display geometry");for(;;)pause();}
    gr_fb_blank(false);openInputs();
    if(inputs.empty())log("No input nodes; root ADB remains available");
    for(;;) {
        error.clear();int choice=menu("0.1 / KEYPAD EDITION",{"Install Leo","Backup partitions","Restore KaiOS","Diagnostics","Stock tools","Reboot system","Power off"});
        if(choice==0)installBundle(kFresh);
        if(choice==1)backup();
        if(choice==2)installBundle(kStock);
        if(choice==3)diagnostics();
        if(choice==4&&menu("OPEN STOCK TOOLS?",{"Cancel","Open stock recovery"})==1) {
            gr_exit();execl("/sbin/recovery-stock","recovery","--show_text",(char*)nullptr);
            log("Cannot start stock tools");return 1;
        }
        if((choice==5||choice==6)&&menu(choice==5?"REBOOT?":"POWER OFF?",{"Cancel","Confirm"})==1) {
            sync();reboot(choice==5?RB_AUTOBOOT:RB_POWER_OFF);message("Power action failed","Use root ADB or the physical power key.");
        }
    }
}
