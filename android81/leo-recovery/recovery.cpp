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
#include <linux/reboot.h>
#include <sys/syscall.h>
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <poll.h>
#include <signal.h>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <iterator>
#include <sstream>
#include <string>
#include <vector>
#include <algorithm>

namespace {
using leo::Image;using leo::Bundle;
const char* CARD="/sdcard";
const char* ROOT="/sdcard/LeoRecovery";
const char* MOUNT_ROOT="/mnt/leo-mount";
struct InspectPartition {
    const char* label;
    const char* path;
    Image image;
};
const InspectPartition internalMounts[]={
    {"System","/mnt/leo-mount/system",{"system",30,179,30,838860800ULL,""}},
    {"Data","/mnt/leo-mount/data",{"userdata",39,259,7,2566897152ULL,""}},
    {"Cache","/mnt/leo-mount/cache",{"cache",32,259,0,209715200ULL,""}},
};
std::string error;
const GRFont* font;
int width,height,fh,fw;
std::vector<int> inputs;
bool graphics=false;
bool backupCompleted=false;
bool cardPresent();

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
    const bool sd=cardPresent();
    const int x=width-23;
    gr_color(sd?65:105,sd?223:123,sd?207:133,255);
    gr_fill(x+4,7,x+15,10);gr_fill(x,10,x+15,23);
    gr_color(20,43,51,255);
    if(sd) {
        for(int pin=5;pin<=11;pin+=3)gr_fill(x+pin,11,x+pin+1,15);
    } else {
        gr_fill(x+2,12,x+13,21);gr_fill(x+4,9,x+13,12);
    }
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
leo::Key waitKey(bool watchCard=false) {
    std::vector<pollfd> watches;for(int fd:inputs)watches.push_back({fd,POLLIN,0});
    const bool card=watchCard&&cardPresent();
    for(;;) {
        if(poll(watches.data(),watches.size(),1000)<0&&errno!=EINTR)return leo::Key::Back;
        if(watchCard&&cardPresent()!=card)return leo::Key::None;
        for(auto watch:watches)if(watch.revents&POLLIN){input_event event;
            while(read(watch.fd,&event,sizeof(event))==sizeof(event))
                if(event.type==EV_KEY&&event.value==0){auto key=leo::key(event.code);if(key!=leo::Key::None)return key;}}
    }
}
void wrappedText(int x,int y,int bottom,const std::string& body) {
    const auto columns=static_cast<unsigned>(std::max(1,(width-x-8)/fw));
    size_t at=0;
    while(at<body.size()&&y+fh<=bottom) {
        size_t count=std::min<size_t>(columns,body.size()-at);
        if(at+count<body.size()) {
            auto space=body.rfind(' ',at+count);
            if(space!=std::string::npos&&space>at)count=space-at;
        }
        text(x,y,body.substr(at,count));
        y+=fh+3;at+=count;
        while(at<body.size()&&body[at]==' ')++at;
    }
}
int menu(const std::string& title,const std::vector<std::string>& items,
         const std::vector<std::string>& hints={},int initial=0,bool refreshOnCardChange=false) {
    if(items.empty())return -1;
    int selected=std::max(0,std::min(initial,static_cast<int>(items.size())-1));
    drainKeys();
    for(;;) {
        frame(title,"OK Select   Back");
        const int footer=height-fh-6;
        const int panel=footer-2*(fh+3)-6;
        const bool showHint=hints.size()==items.size()&&panel>=65+2*(fh+6);
        const int step=fh+6;
        const int rows=std::max(1,((showHint?panel:footer)-65)/step);
        int first=(selected/rows)*rows;
        for(int i=first;i<(int)items.size()&&i<first+rows;++i) {
            int y=65+(i-first)*step;
            if(i==selected){gr_color(30,89,96,255);gr_fill(4,y-1,width-4,y+fh+4);}
            gr_color(i==selected?102:211,i==selected?245:222,i==selected?221:232,255);
            text(9,y,items[i],i==selected);
        }
        if(showHint) {
            gr_color(20,36,46,255);gr_fill(0,panel,width,footer);
            gr_color(195,207,216,255);wrappedText(8,panel+4,footer-2,hints[selected]);
        }
        gr_flip();auto key=waitKey(true);
        if(key==leo::Key::None&&refreshOnCardChange)return -2;
        if(key==leo::Key::Up)selected=(selected+(int)items.size()-1)%items.size();
        if(key==leo::Key::Down)selected=(selected+1)%items.size();
        if(key==leo::Key::Back)return -1;
        if(key==leo::Key::Accept)return selected;
    }
}
void message(const std::string& title,const std::string& body) {
    if(!graphics){log(title+": "+body);return;}
    drainKeys();
    for(;;) {
        frame(title,"BACK / OK to return");gr_color(224,233,240,255);
        wrappedText(8,70,height-fh-8,body);
        gr_flip();
        const auto key=waitKey(true);
        if(key==leo::Key::Back||key==leo::Key::Accept)return;
    }
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
        return fail("Unmount the target in Mount partitions first");
    for(unsigned i=0;i<bundle.count;++i)if(!deviceNode(bundle.images[i]))return false;
    return true;
}
using leo::MountState;
struct MountTables {
    std::string local=readText("/proc/self/mountinfo");
    std::string init=readText("/proc/1/mountinfo");
};
MountState visibleMount(const MountTables& tables,const std::string& dev,const char* path) {
    auto here=leo::mountState(tables.local,dev,path);
    auto parent=leo::mountState(tables.init,dev,path);
    if(here==MountState::Unknown||parent==MountState::Unknown)return MountState::Unknown;
    if(parent!=MountState::Unmounted&&parent!=here)return MountState::Other;
    return here;
}
std::string deviceId(const Image& image) {
    return std::to_string(image.major)+":"+std::to_string(image.minor);
}
bool sdMounted() {
    return visibleMount(MountTables{},"179:65",CARD)==MountState::ReadWrite;
}
bool cardPresent() {
    const auto info=readText("/sys/class/block/mmcblk1p1/uevent");
    return leo::token(info,"MAJOR=179")&&leo::token(info,"MINOR=65");
}
bool mountCard() {
    // mountinfo options are comma-separated. Check a real external SD mount;
    // never stage to a same-named directory on userdata or ramdisk.
    if(!cardPresent())return fail("External SD not found");
    if(!directory(CARD))return false;
    if(!sdMounted()) {
        if(visibleMount(MountTables{},"179:65",CARD)!=MountState::Unmounted)
            return fail("Check External SD in Mount partitions");
        const char* alias="/dev/leo-recovery-card";struct stat st;dev_t dev=makedev(179,65);
        if(mknod(alias,S_IFBLK|0600,dev)&&errno!=EEXIST)return fail("Cannot create SD alias");
        if(lstat(alias,&st)||!S_ISBLK(st.st_mode)||st.st_rdev!=dev)return fail("Unsafe SD alias");
        if(mount(alias,CARD,"vfat",MS_NODEV|MS_NOSUID|MS_NOEXEC,"utf8")&&
           mount(alias,CARD,"ext4",MS_NODEV|MS_NOSUID|MS_NOEXEC,""))return fail("Cannot mount SD; not formatted");
    }
    return sdMounted()&&directory(ROOT);
}
bool releaseCard() {
    const auto state=visibleMount(MountTables{},"179:65",CARD);
    if(state==MountState::Other||state==MountState::Unknown)
        return fail("Check External SD in Mount partitions");
    sync();
    return state==MountState::Unmounted||!umount(CARD)||fail("SD busy; close its operation first");
}
const char* mountLabel(MountState state) {
    switch(state) {
        case MountState::Unmounted:return "off";
        case MountState::ReadOnly:return "RO";
        case MountState::ReadWrite:return "RW";
        case MountState::Other:return "busy";
        case MountState::Unknown:return "?";
    }
    return "?";
}
const char* mountHint(MountState state,bool external) {
    switch(state) {
        case MountState::Unmounted:return external?"OK mounts the external SD.":"OK mounts read-only.";
        case MountState::ReadOnly:
        case MountState::ReadWrite:return "OK unmounts this volume.";
        case MountState::Other:return "Mounted elsewhere or path busy.";
        case MountState::Unknown:return "Mount state unavailable.";
    }
    return "Mount state unavailable.";
}
bool toggleInternalMount(const InspectPartition& part) {
    if(!deviceNode(part.image))return false;
    const auto state=visibleMount(MountTables{},deviceId(part.image),part.path);
    if(state==MountState::ReadOnly||state==MountState::ReadWrite)
        return !umount(part.path)||fail("Partition busy; close its operation");
    if(state!=MountState::Unmounted)return fail("Partition or mount path is busy");
    if(!directory("/mnt")||!directory(MOUNT_ROOT)||!directory(part.path))return false;
    if(mount(node(part.image).c_str(),part.path,"ext4",
        MS_RDONLY|MS_NODEV|MS_NOSUID|MS_NOEXEC,"noload"))
        return fail("Read-only ext4 mount failed");
    if(visibleMount(MountTables{},deviceId(part.image),part.path)!=MountState::ReadOnly)
        return fail("Check the new mount status");
    log(std::string("Mounted read-only: ")+part.path);
    return true;
}
void mountPartitions() {
    if(getuid()!=0||!leo::correctPhone(readText("/proc/cmdline"))||!noAndroid()) {
        message("Mount unavailable","Start this device in root recovery.");
        return;
    }
    int choice=0;
    for(;;) {
        const MountTables tables;
        const auto sd=visibleMount(tables,"179:65",CARD);
        const bool present=cardPresent();
        std::vector<std::string> rows={"Back",std::string("External SD ")+(present?mountLabel(sd):"absent")};
        std::vector<std::string> hints={"Return to main menu.",present?mountHint(sd,true):"Insert an SD card."};
        for(const auto& part:internalMounts) {
            const auto state=visibleMount(tables,deviceId(part.image),part.path);
            rows.push_back(std::string(part.label)+"  "+mountLabel(state));
            hints.push_back(mountHint(state,false));
        }
        choice=menu("Mount partitions",rows,hints,choice);
        if(choice<1)return;
        const bool changed=choice==1
            ? (sd==MountState::Unmounted?mountCard():releaseCard())
            : toggleInternalMount(internalMounts[choice-2]);
        if(!changed)message("Mount unavailable",error);
    }
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
bool extractImage(const Bundle& bundle,Archive& zip,const Image& image,const std::string& path) {
    ZipEntry entry{};bool pinned=false;
    // Use metadata from the exact verified archive, including ZIP64 LFHs.
    for(const auto& pin:kZipImages)if(!strcmp(pin.archiveSha,bundle.sha)&&!strcmp(pin.name,image.name)){
        if(pin.uncompressed!=image.bytes||pin.offset>bundle.archiveBytes||pin.compressed>bundle.archiveBytes-pin.offset)return fail("Pinned ZIP bounds mismatch");
        entry.offset=pin.offset;entry.compressed_length=pin.compressed;entry.uncompressed_length=pin.uncompressed;
        entry.crc32=pin.crc;entry.method=pin.method;entry.has_data_descriptor=0;pinned=true;break;
    }
    if(!pinned)return fail("ZIP image metadata not pinned");
    Fd output(open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600));
    if(output.fd<0)return fail("Staging file already exists");
    progress("Extracting",image.name);
    if(ExtractEntryToFile(zip.handle,&entry,output.fd)||fsync(output.fd))return fail("ZIP extraction failed");
    return checkFile(path,image.bytes,image.sha);
}
bool extract(const Bundle& bundle,Archive& zip,const std::string& stage) {
    if(!freeSpace(leo::stagingBytes(bundle)))return false;
    for(unsigned i=0;i<bundle.count;++i) {
        const auto& image=bundle.images[i];
        if(!extractImage(bundle,zip,image,stage+"/"+image.name+".img"))return false;
    }
    return true;
}
void installBundle(const Bundle& bundle) {
    const std::string title=bundle.wipe?"Install Leo":"Restore KaiOS";
    const std::string consequence=bundle.wipe?"Erases internal data.":"Writes boot and system.";
    int action=0;
    for(;;) {
        action=menu(title,{"Back","Check archive",bundle.wipe?"Review install":"Review restore","Package location"},
            {"Return to previous menu.","Check the ZIP on SD.",consequence,"Where to copy the ZIP."},action);
        if(action<1)return;
        if(action==1) {
            Archive zip;
            if(mountCard()&&openPackage(bundle,&zip))message("Verified","Approved archive on SD.");
            else message("Verification failed",error);
        }
        if(action==2&&menu(bundle.wipe?"Erase phone data?":"Restore boot/system",
            {"Cancel",bundle.wipe?"Erase & install":"Write KaiOS"},
            {consequence,bundle.wipe?consequence:"Then reset data."})==1)break;
        if(action==3)message("Package location",
            std::string("Place ")+bundle.filename+" in /sdcard/LeoRecovery/packages.");
    }
    if(!mountCard()||!targetGuards(bundle)||!powerReady()||!stockBackups()){
        message("Installation refused",error);return;
    }
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
void installFromSD() {
    int choice=0;
    for(;;) {
        choice=menu("Install from SD",{"Back","Leo","KaiOS","postmarketOS"},
            {"Return to Install.","Clean install. Erases data.","Restore boot and system.",
             "Installer unavailable."},choice);
        if(choice<1)return;
        if(choice==1)installBundle(kFresh);
        if(choice==2)installBundle(kStock);
        if(choice==3)message("Install unavailable",
            "postmarketOS needs a validated bootloader and SD image for this Nokia. "
            "See Boot and power > Boot from SD for the boot check.");
    }
}
void installMenu() {
    int choice=0;
    while((choice=menu("Install",{"Back","Install from SD"},
        {"Return to main menu.","Choose a system."},choice))==1)installFromSD();
}
bool partitionReady(const Image& image,bool writing) {
    if(!deviceNode(image))return false;
    const MountTables tables;
    const auto path=std::string(MOUNT_ROOT)+"/"+(std::string(image.name)=="userdata"?"data":image.name);
    const auto state=visibleMount(tables,deviceId(image),path.c_str());
    if(state==MountState::Unmounted||(!writing&&state==MountState::ReadOnly))return true;
    return fail(std::string("Unmount ")+image.name+" before continuing.");
}

void backup(const Image* images,size_t count,bool confirmed=false) {
    if(!mountCard()||getuid()!=0||!leo::correctPhone(readText("/proc/cmdline"))||!noAndroid()) {message("Backup unavailable",error);return;}
    uint64_t bytes=1024u*1024u;
    for(size_t i=0;i<count;++i)bytes+=images[i].bytes;
    if(!freeSpace(bytes)){message("Backup unavailable",error);return;}
    const std::string detail=count==1?std::string("Copy ")+images[0].name+" to microSD.":"Boot, system, recovery.";
    if(!confirmed&&menu("Partition backup",{"Cancel","Copy to SD"},
        {"Return to Backup / Restore.",detail})!=1)return;
    for(size_t i=0;i<count;++i) {
        if(!partitionReady(images[i],false)){message("Backup unavailable",error);return;}
    }
    std::string dir=std::string(ROOT)+"/backup-"+std::to_string(time(nullptr))+"-"+std::to_string(getpid());
    if(mkdir(dir.c_str(),0700)){message("Backup failed","Existing directory retained");return;}
    const auto pending=dir+"/SHA256SUMS.pending";
    Fd report(open(pending.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC,0600));
    if(report.fd<0){message("Backup failed","Cannot create checksum report");return;}
    for(size_t i=0;i<count;++i) {
        const auto& image=images[i];
        if(!partitionReady(image,false)){message("Backup failed",error);return;}
        Fd source(open(node(image).c_str(),O_RDONLY|O_NOFOLLOW|O_CLOEXEC));
        std::string path=dir+"/"+image.name+".img";
        Fd dest(open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600));
        if(source.fd<0||dest.fd<0||!copyFd(source.fd,dest.fd,image.bytes,image.name)){message("Backup incomplete",error);return;}
        std::string digest,again;
        if(!hashFd(source.fd,image.bytes,&digest,image.name)||!checkFile(path,image.bytes,digest)||
           !hashFd(source.fd,image.bytes,&again,image.name)||again!=digest){message("Backup incomplete","Source changed or readback failed");return;}
        if(dprintf(report.fd,"%s  %s.img\n",digest.c_str(),image.name)<0){message("Backup incomplete","Cannot record checksum");return;}
    }
    if(fsync(report.fd)||rename(pending.c_str(),(dir+"/SHA256SUMS").c_str())){
        message("Backup incomplete","Checksum sync failed");return;
    }
    sync();backupCompleted=true;log("Backup complete: "+dir);message("Backup complete","Verified raw partition images saved in "+dir);
}
std::string displayName(std::string name) {
    for(char& value:name)if(value<' '||value>'~')value='?';
    return name;
}

void viewRecoveryLog() {
    const auto logText=readText("/tmp/leo-recovery.log");
    if(logText.empty()){message("Recovery log","No recovery log available.");return;}
    const int columns=std::max(1,(width-16)/fw);
    const int rows=std::max(1,(height-fh-6-65)/(fh+3));
    std::istringstream input(logText);
    std::vector<std::string> lines;
    std::string line;
    while(std::getline(input,line)) {
        if(line.empty())lines.emplace_back();
        for(size_t start=0;start<line.size();start+=columns)
            lines.push_back(displayName(line.substr(start,columns)));
    }
    if(lines.empty())return;
    const size_t last=(lines.size()-1)/rows;
    size_t page=last;
    drainKeys();
    for(;;) {
        frame("Log "+std::to_string(page+1)+"/"+std::to_string(last+1),"UP/DOWN      Back");
        gr_color(224,233,240,255);
        for(size_t row=0;row<static_cast<size_t>(rows)&&page*rows+row<lines.size();++row)
            text(8,65+row*(fh+3),lines[page*rows+row]);
        gr_flip();
        const auto key=waitKey(true);
        if(key==leo::Key::Back||key==leo::Key::Accept)return;
        if(key==leo::Key::Up&&page>0)--page;
        if(key==leo::Key::Down&&page<last)++page;
    }
}

void diagnostics() {
    int choice=menu("Diagnostics",{"Back","View recovery log","Save recovery log","Device information"},
        {"Return to Tools.","Read the log on screen.","Copy the recovery log to SD.","Show model and recovery state."});
    if(choice==1)viewRecoveryLog();
    if(choice==2){if(!mountCard()){message("Log not saved",error);return;}
        auto data=readText("/tmp/leo-recovery.log");auto path=std::string(ROOT)+"/recovery-"+std::to_string(time(nullptr))+".log";
        Fd out(open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600));
        bool ok=out.fd>=0&&write(out.fd,data.data(),data.size())==(ssize_t)data.size()&&!fsync(out.fd);
        message(ok?"Log saved":"Log failed",ok?"See LeoRecovery on SD":"Cannot write recovery log");}
    if(choice==3)message("Device information",std::string("Nokia 6300 TA-1286. Root ADB. No Android framework. ")+(leo::correctPhone(readText("/proc/cmdline"))?"Serial matches 695d.":"WRONG SERIAL - DO NOT FLASH."));
}
bool successfulChild(pid_t child) {
    if(child<0)return false;
    int status=0;while(waitpid(child,&status,0)<0){if(errno!=EINTR)return false;}
    return WIFEXITED(status)&&WEXITSTATUS(status)==0;
}
bool probeSd(bool leoBoot) {
    progress(leoBoot?"Leo SD":"KaiOS SD","Checking SD images...");
    pid_t child=fork();if(child<0)return fail("Cannot start verifier");
    if(child==0){execl("/sbin/leo-boot-selector","leo-boot-selector",leoBoot?"--probe-leo-sd":"--probe-sd",(char*)nullptr);_exit(127);}
    return successfulChild(child)||fail("SD images missing/invalid. See root ADB log.");
}
void requestSdBoot(bool leoBoot) {
    const auto label=leoBoot?"Boot Leo once":"Boot KaiOS once";
    if(menu("Boot once from SD?",{"Cancel",label})!=1)return;
    const auto base=std::string(CARD)+(leoBoot?"/LeoAndroid":"/LeoKaiOS");
    const auto other=std::string(CARD)+(leoBoot?"/LeoKaiOS":"/LeoAndroid");
    struct stat st;
    if(!lstat((other+"/boot-once").c_str(),&st)) {
        message("Boot not requested","Another OS has an SD boot request.");return;
    }
    auto path=base+"/boot-once";
    Fd f(open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600));
    const char* flag=leoBoot?"LEO_ANDROID_SD_ONCE_V1_695d\n":"LEO_KAIOS_SD_ONCE_V1_695d\n";
    if(f.fd<0){message("Boot not requested","Cannot create one-shot SD request.");return;}
    if(write(f.fd,flag,strlen(flag))!=(ssize_t)strlen(flag)||fsync(f.fd)) {
        unlink(path.c_str());sync();
        message("Boot not requested","Cannot save one-shot SD request.");return;
    }
    close(f.fd);f.fd=-1;
    if(!releaseCard()){unlink(path.c_str());sync();message("Boot not requested",error);return;}
    log(std::string(leoBoot?"Leo":"KaiOS")+" SD one-shot reboot requested; SD unmounted");
    syscall(__NR_reboot,LINUX_REBOOT_MAGIC1,LINUX_REBOOT_MAGIC2,LINUX_REBOOT_CMD_RESTART2,"recovery");
    bool removed=mountCard()&&!unlink(path.c_str());sync();
    message("Reboot failed",removed?"Boot request removed. Use root ADB.":"SD request may remain. Normal reboot still starts Leo.");
}
void kaiosSD() {
    int choice=menu("KaiOS from SD",{"Back","Verify SD images","Boot KaiOS once"},
        {"Return to SD boot.","Check the three images.","Start KaiOS once."});if(choice<1)return;
    if(!mountCard()||!probeSd(false)){message("KaiOS SD unavailable",error);return;}
    if(choice==1){message("SD images verified","System, data and cache images found.");return;}
    requestSdBoot(false);
}

bool prepareLeoDisk(const std::string& dir,const char* name,uint64_t bytes) {
    const auto templateDir=std::string("/leo-android-root/leo-templates/");
    const auto path=dir+"/"+name+".img";
    const auto hash=readText(templateDir+name+".sha256");
    Fd output(open(path.c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600));
    if(output.fd<0||hash.size()!=64)return fail("Cannot create scratch image");
    progress("Preparing Leo SD",name);
    const auto archive=templateDir+name+".gz";
    pid_t child=fork();
    if(child==0){if(dup2(output.fd,STDOUT_FILENO)<0)_exit(126);
        execl("/system/bin/busybox","busybox","gzip","-dc",archive.c_str(),(char*)nullptr);_exit(127);}
    if(!successfulChild(child)||fsync(output.fd))return fail("Scratch image extraction failed");
    return checkFile(path,bytes,hash);
}

bool prepareLeoSd() {
    if(!mountCard()||!powerReady()||getuid()!=0||!leo::correctPhone(readText("/proc/cmdline"))||!noAndroid())return false;
    if(access("/leo-android-root/init",X_OK))return fail("Leo SD runtime missing in this build");
    const auto target=std::string(CARD)+"/LeoAndroid";
    struct stat info;
    if(!lstat(target.c_str(),&info))return fail("LeoAndroid already exists. Verify that copy first.");
    const uint64_t data=512ULL*1024*1024,cache=64ULL*1024*1024;
    const auto& system=kFresh.images[0];
    if(strcmp(system.name,"system"))return fail("Approved Leo system image missing");
    if(!freeSpace(system.bytes+data+cache+8*1024*1024))return false;
    Archive zip;if(!openPackage(kFresh,&zip))return false;
    std::string stage=target+".pending-XXXXXX";
    std::vector<char> folder(stage.begin(),stage.end());folder.push_back('\0');
    if(!mkdtemp(folder.data()))return fail("Cannot create Leo staging folder");
    stage=folder.data();
    if(!extractImage(kFresh,zip,system,stage+"/system.img")||
       !prepareLeoDisk(stage,"data",data)||!prepareLeoDisk(stage,"cache",cache))return false;
    Fd owner(open((stage+"/owner").c_str(),O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600));
    const char marker[]="LEO_ANDROID_SD_STORAGE_V1_695d\n";
    if(owner.fd<0||write(owner.fd,marker,sizeof(marker)-1)!=sizeof(marker)-1||fsync(owner.fd)||
       rename(stage.c_str(),target.c_str()))return fail("Cannot publish verified Leo SD images");
    sync();return true;
}

void leoSD() {
    const int choice=menu("Leo from SD",{"Back","Prepare card","Verify SD images","Boot Leo once"},
        {"Return to SD boot.","Create an isolated copy.","Check the three images.","Start the SD copy once."});
    if(choice<1)return;
    if(choice==1) {
        if(menu("Prepare Leo SD?",{"Cancel","Create SD images"},
            {"Return to Leo SD.","Uses about 1.4 GiB on the card."})==1) {
            const bool ok=prepareLeoSd();
            message(ok?"Leo SD ready":"Preparation failed",ok?"Select Boot Leo once when ready.":error);
        }
        return;
    }
    if(!mountCard()||!probeSd(true)){message("Leo SD unavailable",error);return;}
    if(choice==2){message("SD images verified","System, data and cache images found.");return;}
    requestSdBoot(true);
}
void powerAction(bool shutdown) {
    if(menu(shutdown?"Power off?":"Restart phone?",
        {"Cancel",shutdown?"Power off":"Restart"},
        {"Return to boot menu.",shutdown?"Turn off the phone.":"Boot internal system."})!=1)return;
    if(!releaseCard()){message("Cannot unmount SD",error);return;}
    reboot(shutdown?RB_POWER_OFF:RB_AUTOBOOT);
    message("Power action failed","Use root ADB or the physical power key.");
}
void rebootBootloader() {
    if(menu("Reboot bootloader?",{"Cancel","Enter bootloader"},
        {"Return to boot menu.","Restart to bootloader mode."})!=1)return;
    if(!releaseCard()){message("Cannot unmount SD",error);return;}
    syscall(__NR_reboot,LINUX_REBOOT_MAGIC1,LINUX_REBOOT_MAGIC2,LINUX_REBOOT_CMD_RESTART2,"bootloader");
    message("Reboot failed","The bootloader restart request was refused.");
}
void postmarketInfo() {
    if(menu("postmarketOS",{"Back","Check boot support"},
        {"Return to SD boot.","Nokia 6300 armv7."})!=1)return;
    if(!leo::correctPhone(readText("/proc/cmdline"))) {
        message("Wrong device","This profile is for Nokia 6300 4G.");
        return;
    }
    if(readText("/sys/kernel/kexec_loaded").empty()) {
        message("Boot unavailable","The running recovery exposes no kexec interface. Prepare a compatible Linux boot path for postmarketOS.");
        return;
    }
    message("Boot unavailable","Kernel handoff is visible. A validated Nokia 6300 loader and SD system image are still required.");
}
void sdBoot() {
    int choice=0;
    for(;;) {
        std::vector<std::string> rows={"Back","KaiOS","postmarketOS"};
        std::vector<std::string> hints={"Return to boot menu.","Verify images or start KaiOS once.","Check Linux boot support."};
        if(!access("/leo-android-root/init",X_OK)){rows.push_back("Leo");hints.push_back("Prepare or boot Leo once.");}
        choice=menu("Boot from SD",rows,hints,choice);
        if(choice<1)return;
        if(choice==1)kaiosSD();
        if(choice==2)postmarketInfo();
        if(choice==3)leoSD();
    }
}
void bootOptions() {
    int choice=0;
    for(;;) {
        choice=menu("Boot and power",{"Back","Boot internal OS","Boot from SD","Power off","Reboot bootloader"},
            {"Return to main menu.","Restart from internal storage.",
             "Use an SD system.","Turn off the phone.","Enter bootloader mode."},choice);
        if(choice<1)return;
        if(choice==1)powerAction(false);
        if(choice==2)sdBoot();
        if(choice==3)powerAction(true);
        if(choice==4)rebootBootloader();
    }
}
struct FileEntry {
    std::string name;
    bool folder;
    uint64_t bytes;
};

bool scanDirectory(int fd,dev_t device,std::vector<FileEntry>* entries,bool* limited) {
    Fd copy(dup(fd));
    if(copy.fd<0)return fail("Cannot open directory stream");
    DIR* dir=fdopendir(copy.fd);
    if(!dir)return fail("Cannot open directory stream");
    copy.fd=-1; // closedir owns the duplicated descriptor.
    constexpr size_t maxEntries=4096;
    int readError=0;
    for(;;) {
        errno=0;
        const dirent* item=readdir(dir);
        if(!item){readError=errno;break;}
        const std::string name=item->d_name;
        if(name=="."||name=="..")continue;
        struct stat info;
        if(fstatat(fd,name.c_str(),&info,AT_SYMLINK_NOFOLLOW)||info.st_dev!=device)continue;
        if(!S_ISDIR(info.st_mode)&&!S_ISREG(info.st_mode))continue;
        if(entries->size()==maxEntries){*limited=true;break;}
        entries->push_back({name,S_ISDIR(info.st_mode),static_cast<uint64_t>(info.st_size)});
    }
    closedir(dir);
    if(readError)return fail("Directory read failed");
    std::sort(entries->begin(),entries->end(),[](const FileEntry& a,const FileEntry& b) {
        if(a.folder!=b.folder)return a.folder;
        return a.name<b.name;
    });
    return true;
}

void restorePartition(const Image& image) {
    if(!mountCard()||!partitionReady(image,true)){
        message("Restore unavailable",error);return;
    }
    Fd folder(open(ROOT,O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC));
    std::vector<FileEntry> entries;
    bool limited=false;
    if(folder.fd<0||!scanDirectory(folder.fd,makedev(179,65),&entries,&limited)) {
        message("Restore unavailable","Cannot read backup folders.");return;
    }
    std::vector<std::string> rows={"Back"},hints={limited?"Folder list limited.":"Return to partition list."};
    std::vector<std::string> paths;
    for(auto it=entries.rbegin();it!=entries.rend();++it) {
        if(!it->folder||it->name.compare(0,7,"backup-")!=0)continue;
        auto path=std::string(ROOT)+"/"+it->name;
        if(leo::backupChecksum(readText(path+"/SHA256SUMS"),image.name).empty())continue;
        rows.push_back(displayName(it->name.substr(7)));
        hints.push_back(displayName(it->name));
        paths.push_back(path);
    }
    if(paths.empty()){message("No partition backup","Only completed backups on this card are listed.");return;}
    const int choice=menu("Choose backup",rows,hints);
    if(choice<1)return;
    const auto& path=paths[choice-1];
    const auto expected=leo::backupChecksum(readText(path+"/SHA256SUMS"),image.name);
    if(menu("Overwrite partition?",{"Cancel","Restore partition"},
        {"Return to backup list.",std::string("Overwrites internal ")+image.name+"."})!=1)return;
    Fd source(open((path+"/"+image.name+".img").c_str(),O_RDONLY|O_NOFOLLOW|O_CLOEXEC));
    struct stat info;
    std::string actual;
    if(expected.empty()||source.fd<0||fstat(source.fd,&info)||!S_ISREG(info.st_mode)||
        info.st_dev!=makedev(179,65)||static_cast<uint64_t>(info.st_size)!=image.bytes||
        !hashFd(source.fd,image.bytes,&actual,image.name)||actual!=expected) {
        message("Restore refused","Backup size or checksum failed. No partition written.");return;
    }
    if(!sdMounted()||!powerReady()||!partitionReady(image,true)){
        message("Restore refused",error.empty()?"External SD disconnected.":error);return;
    }
    Fd target(open(node(image).c_str(),O_RDWR|O_NOFOLLOW|O_CLOEXEC));
    if(target.fd<0||!copyFd(source.fd,target.fd,image.bytes,image.name)||
        !hashFd(target.fd,image.bytes,&actual,image.name)||actual!=expected) {
        message("STOP - keep recovery","Partition write or readback failed. Keep recovery open.");return;
    }
    sync();log(std::string("Restored partition: ")+image.name);
    message("Restore complete","Partition readback matches the backup checksum.");
}

void backupRestoreMenu() {
    if(!cardPresent()||getuid()!=0||!leo::correctPhone(readText("/proc/cmdline"))||!noAndroid()) {
        message("Unavailable","This action requires a card in root recovery.");return;
    }
    const Image* parts[]={&kBackupImages[0],&kBackupImages[1],&kBackupImages[2],
        &internalMounts[1].image,&internalMounts[2].image};
    int action=0;
    while(cardPresent()) {
        action=menu("Backup / Restore",{"Back","Backup partition","Restore partition"},
            {"Return to main menu.","Save a verified raw image.","Restore from a card backup."},action,true);
        if(action<1)return;
        const int part=menu("Select partition",{"Back","Boot","System","Recovery","Data","Cache"},
            {"Return to Backup / Restore.","Internal boot partition.","Internal system partition.",
             "Internal recovery partition.","Apps and private user data.","Internal cache partition."},0,true);
        if(part<1)continue;
        if(action==1)backup(parts[part-1],1);
        if(action==2)restorePartition(*parts[part-1]);
    }
}

void browseFiles(const char* path,dev_t device,const std::string& title) {
    struct Location {std::string name;int selection;};
    std::vector<Location> locations={{"",0}};
    for(;;) {
        Fd folder(open(path,O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC));
        for(size_t i=0;i<locations.size();++i) {
            if(i>0&&folder.fd>=0) {
                int next=openat(folder.fd,locations[i].name.c_str(),O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);
                close(folder.fd);folder.fd=next;
            }
            struct stat info;
            if(folder.fd<0||fstat(folder.fd,&info)||info.st_dev!=device){
                message("Folder unavailable","Volume changed or the folder could not be opened.");
                return;
            }
        }
        std::vector<FileEntry> entries;
        bool limited=false;
        if(!scanDirectory(folder.fd,device,&entries,&limited)){message("Folder unavailable",error);return;}
        const bool atRoot=locations.size()==1;
        std::vector<std::string> rows={atRoot?"Back":"[..] Up a folder"};
        std::vector<std::string> hints={limited?"Showing up to 4096 entries.":"Browse files and folders."};
        for(const auto& entry:entries) {
            const auto name=displayName(entry.name);
            rows.push_back(entry.folder?"[+] "+name:name);
            hints.push_back(name);
        }
        auto& current=locations.back();
        const int choice=menu(atRoot?title:displayName(current.name),rows,hints,current.selection);
        if(choice<1) {
            if(atRoot)return;
            locations.pop_back();
            continue;
        }
        current.selection=choice;
        const auto& entry=entries[choice-1];
        if(entry.folder)locations.push_back({entry.name,0});
        else message("File details",std::to_string(entry.bytes)+" bytes. "+displayName(entry.name));
    }
}

void fileManager() {
    int choice=0;
    for(;;) {
        choice=menu("File manager",{"Back","External SD","System","Data","Cache"},
            {"Return to Tools.","Browse mounted microSD.","Browse mounted System.",
             "Browse mounted Data.","Browse mounted Cache."},choice);
        if(choice<1)return;
        if(choice==1) {
            const auto state=visibleMount(MountTables{},"179:65",CARD);
            if(state==MountState::ReadOnly||state==MountState::ReadWrite)
                browseFiles(CARD,makedev(179,65),"External SD");
            else message("Mount External SD","Use Mount partitions first.");
            continue;
        }
        const auto& part=internalMounts[choice-2];
        if(visibleMount(MountTables{},deviceId(part.image),part.path)==MountState::ReadOnly)
            browseFiles(part.path,makedev(part.image.major,part.image.minor),part.label);
        else message("Volume unmounted","Use Mount partitions to mount this volume read-only.");
    }
}

bool waitMtpHelper(pid_t child, int* status, unsigned attempts) {
    for(unsigned i=0;i<attempts;++i) {
        pid_t result=waitpid(child,status,WNOHANG);
        if(result==child)return true;
        if(result<0&&errno!=EINTR)return false;
        usleep(100000);
    }
    return false;
}

bool mtpInstalled() {
    return !access("/sbin/leo-mtp-session",X_OK)&&!access("/sbin/leo-mtp-responder",X_OK);
}

void shareMtpVolume(const char* volume) {
    char state[]="/tmp/leo-mtp-XXXXXX";
    if(!mkdtemp(state)){message("MTP unavailable","Cannot create USB session directory.");return;}
    const auto child=fork();
    if(child<0){rmdir(state);message("MTP unavailable","Cannot start USB session.");return;}
    if(child==0) {
        Fd output(open("/tmp/leo-recovery.log",O_WRONLY|O_APPEND|O_CLOEXEC|O_NOFOLLOW));
        if(output.fd>=0){dup2(output.fd,STDOUT_FILENO);dup2(output.fd,STDERR_FILENO);}
        execl("/system/bin/sh","sh","/sbin/leo-mtp-session",state,volume,(char*)nullptr);
        _exit(127);
    }
    const std::string active=std::string(state)+"/active";
    const std::string responderLog=std::string(state)+"/responder.log";
    int status=0;
    bool finished=false;
    bool ready=false;
    progress("USB MTP","Starting session...");
    for(unsigned i=0;i<80;++i) {
        finished=waitMtpHelper(child,&status,1);
        if(finished)break;
        ready=readText(active)=="active\n";
        if(ready)break;
    }
    if(ready)menu("USB MTP active",{"Stop MTP"},{"Stop sharing and restore ADB."});
    if(!finished) {
        kill(child,SIGTERM);
        progress("USB MTP","Restoring ADB...");
        finished=waitMtpHelper(child,&status,80);
    }
    if(!finished){message("USB switch busy","Restart recovery to restore ADB.");return;}
    if(!ready || !WIFEXITED(status) || WEXITSTATUS(status)!=0) {
        const auto details=readText(responderLog);
        if(!details.empty())log(details);
        message("MTP unavailable","Check the recovery log. Restart recovery if ADB did not return.");
    } else log("MTP ended; ADB gadget restored.");
    unlink(responderLog.c_str());
    unlink(active.c_str());
    rmdir(state);
}

void mtpMenu() {
    const int choice=menu("USB MTP",{"Back","System (read-only)","Data (read-only)",
        "Internal storage","microSD"},
        {"Return to Tools.","Share mounted System.","Includes private app files.",
         "Share public media read-only.","Copy files in either direction."});
    if(choice<1)return;
    const MountTables tables;
    if(choice==4) {
        if(visibleMount(tables,"179:65",CARD)!=MountState::ReadWrite) {
            message("Mount External SD","Use Mount partitions first.");return;
        }
    } else {
        const auto& part=internalMounts[choice==1?0:1];
        if(visibleMount(tables,deviceId(part.image),part.path)!=MountState::ReadOnly) {
            message("Volume unmounted","Mount System or Data read-only first.");return;
        }
    }
    if(choice==3) {
        struct stat media,internal;
        if(lstat("/mnt/leo-mount/data/media",&media)||!S_ISDIR(media.st_mode)||
            lstat("/mnt/leo-mount/data/media/0",&internal)||!S_ISDIR(internal.st_mode)) {
            message("Storage unavailable","Shared Android storage is missing.");return;
        }
    }
    if(menu("Start USB MTP?",{"Cancel","Share selected"},
        {"Keep the ADB connection.",choice==2?"Expose private data to PC.":"ADB pauses while sharing."})!=1)return;
    const char* volumes[]={"system","data","internal","sd"};
    shareMtpVolume(volumes[choice-1]);
}

bool stockVolume(const Image& image) {
    if(!deviceNode(image))return false;
    const auto path=std::string("/dev/block/bootdevice/by-name/")+image.name;
    struct stat info;
    if(stat(path.c_str(),&info)||!S_ISBLK(info.st_mode)||info.st_rdev!=makedev(image.major,image.minor))
        return fail(std::string("Wrong stock volume: ")+image.name);
    return true;
}

enum class StockTool { Menu, FactoryReset, WipeCache };
void openStockRecovery(StockTool tool) {
    const bool reset=tool==StockTool::FactoryReset;
    const bool wipe=tool!=StockTool::Menu;
    const char* title=reset?"Factory reset":wipe?"Wipe cache":"Open stock tools?";
    const char* accept=reset?"Erase data + cache":wipe?"Erase cache":"Open stock tools";
    const char* hint=reset?"Erases apps and internal data.":wipe?"Clears the cache partition.":"Open original recovery.";
    if(menu(title,{"Cancel",accept},{"Return to Leo tools.",hint})!=1)return;
    if(getuid()!=0||!leo::correctPhone(readText("/proc/cmdline"))||!noAndroid()) {
        message("Action unavailable","Start this device in root recovery.");
        return;
    }
    const MountTables tables;
    if(std::any_of(std::begin(internalMounts),std::end(internalMounts),
        [&](const InspectPartition& part) {
            return visibleMount(tables,deviceId(part.image),part.path)!=MountState::Unmounted;
        })) {
        message("Unmount first","Unmount System, Data and Cache in Mount partitions before opening stock recovery.");
        return;
    }
    if(wipe&&(!powerReady()||!stockVolume(internalMounts[2].image)||
        (reset&&!stockVolume(internalMounts[1].image))||!releaseCard())) {
        message("Wipe refused",error);
        return;
    }
    const char* command=reset?"--wipe_data":wipe?"--wipe_cache":"--show_text";
    log(std::string("Running stock recovery: ")+command);
    gr_exit();
    execl("/sbin/recovery-stock","recovery",command,(char*)nullptr);
    log("Cannot start stock tools");
    _exit(1);
}

void tools() {
    int choice=0;
    for(;;) {
        std::vector<std::string> rows={"Back","File manager","Factory reset","Wipe cache",
            "Diagnostics","Stock recovery","Keypad help"};
        std::vector<std::string> hints={"Return to main menu.","Browse mounted volumes.",
            "Erase data with stock recovery.","Erase only Cache.","View system info or logs.",
            "Open original recovery.","Arrows, select and back."};
        if(mtpInstalled()){rows.push_back("USB MTP");hints.push_back("Share a mounted volume.");}
        choice=menu("Tools",rows,hints,choice);
        if(choice<1)return;
        if(choice==1)fileManager();
        if(choice==2)openStockRecovery(StockTool::FactoryReset);
        if(choice==3)openStockRecovery(StockTool::WipeCache);
        if(choice==4)diagnostics();
        if(choice==5)openStockRecovery(StockTool::Menu);
        if(choice==6)message("Keypad help","Up and Down move. OK, green key, or left softkey selects. Back, red key, or right softkey returns.");
        if(choice==7)mtpMenu();
    }
}
}

int main(int argc,char** argv) {
    setvbuf(stdout,nullptr,_IONBF,0);setvbuf(stderr,nullptr,_IONBF,0);
    // Do not process Android OTA/BCB/cache commands or auto-wipe on startup.
    mkdir("/tmp",0755);log("Leo Recovery 0.2; no automatic writes");
    if(argc==2&&!strcmp(argv[1],"--backup-sd")){
        backup(kBackupImages,sizeof(kBackupImages)/sizeof(kBackupImages[0]),true);return backupCompleted?0:1;
    }
    if(argc==2&&!strcmp(argv[1],"--verify-fresh")){
        if(!mountCard()||!targetGuards(kFresh))return 1;
        Archive zip;if(!openPackage(kFresh,&zip))return 1;
        std::string stage=std::string(ROOT)+"/verify-"+std::to_string(time(nullptr))+"-"+std::to_string(getpid());
        if(mkdir(stage.c_str(),0700)||!extract(kFresh,zip,stage))return 1;
        log("Full package extracted and verified. NO partitions written.");return 0;
    }
    if(argc==2&&!strcmp(argv[1],"--prepare-leo-sd"))return prepareLeoSd()?0:1;
    if(argc!=1){log("Unsupported arguments ignored; no automatic operation");}
    if(gr_init()!=0){log("Framebuffer unavailable. Root ADB remains available.");for(;;)pause();}
    graphics=true;font=gr_sys_font();gr_font_size(font,&fw,&fh);width=gr_fb_width();height=gr_fb_height();
    log("RGB565 framebuffer ready: "+std::to_string(width)+"x"+std::to_string(height));
    if(width<160||height<200){log("Unsupported display geometry");for(;;)pause();}
    gr_fb_blank(false);openInputs();
    if(inputs.empty())log("No input nodes; root ADB remains available");
    int selected=0;
    for(;;) {
        error.clear();
        const bool card=cardPresent();
        std::vector<std::string> rows={"Install","Mount partitions","Boot and power","Tools"};
        std::vector<std::string> hints={"Check or install a ZIP from SD.","Manage SD and internal mounts.",
            "Restart, boot from SD or turn off.","Logs, help and stock recovery."};
        if(card) {
            rows.insert(rows.begin()+1,"Backup / Restore");
            hints.insert(hints.begin()+1,"Back up or restore partitions.");
        }
        const int visibleSelection=card?selected:(selected>1?selected-1:0);
        int choice=menu("Choose an action",rows,hints,visibleSelection,true);
        if(choice<0)continue;
        if(!card&&choice>0)++choice;
        selected=choice;
        if(choice==0)installMenu();
        if(choice==1)backupRestoreMenu();
        if(choice==2)mountPartitions();
        if(choice==3)bootOptions();
        if(choice==4)tools();
    }
}
