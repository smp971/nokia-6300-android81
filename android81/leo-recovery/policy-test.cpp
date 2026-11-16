#include "policy.h"
#include "rgb565.h"
#include <cassert>
#include <iostream>
int main() {
    using namespace leo;
    assert(mountReadWrite("rw,nosuid,nodev,noexec"));
    assert(!mountReadWrite("ro,nosuid,nodev"));
    const char* ordinary="1 0 0:1 / / rw - tmpfs tmpfs rw\n";
    const char* systemMount="2 1 179:30 / /mnt/leo-mount/system ro,nodev - ext4 /dev/system ro\n";
    const char* externalMount="3 1 179:65 / /sdcard rw,nosuid - vfat /dev/card rw\n";
    assert(mountState(ordinary,"179:30","/mnt/leo-mount/system")==MountState::Unmounted);
    assert(mountState(systemMount,"179:30","/mnt/leo-mount/system")==MountState::ReadOnly);
    assert(mountState(systemMount,"179:30","/system")==MountState::Other);
    assert(mountedTargets(systemMount,true));
    assert(mountState(externalMount,"179:65","/sdcard")==MountState::ReadWrite);
    assert(mountState(externalMount,"179:66","/sdcard")==MountState::Other);
    assert(mountState(std::string(externalMount)+externalMount,"179:65","/sdcard")==MountState::Other);
    assert(mountState("2 1 179:30 /sub /mnt/leo-mount/system ro","179:30","/mnt/leo-mount/system")==MountState::Other);
    assert(mountState("bad mount entry","179:30","/mnt/leo-mount/system")==MountState::Unknown);
    assert(mountState("","179:30","/mnt/leo-mount/system")==MountState::Unknown);
    assert(correctPhone("console=x androidboot.serialno=695d quiet"));
    assert(!correctPhone("androidboot.serialno=695devil"));
    assert(!correctPhone("xandroidboot.serialno=695d"));
    const std::string checksum(64,'a');
    const std::string previous(64,'b');
    assert(matchesSdSystem(checksum,checksum,previous));
    assert(matchesSdSystem(previous,checksum,previous));
    assert(!matchesSdSystem(previous,checksum,""));
    assert(!matchesSdSystem(std::string(64,'c'),checksum,previous));
    assert(!matchesSdSystem("","",""));
    const auto backup=checksum+"  boot.img\r\n"+checksum+"  system.img\n";
    assert(backupChecksum(backup,"boot")==checksum);
    assert(backupChecksum(backup,"cache").empty());
    assert(backupChecksum(backup+checksum+"  boot.img\n","boot").empty());
    assert(backupChecksum(std::string(64,'x')+"  boot.img\n","boot").empty());
    assert(mountedTargets("1 0 179:30 / /system ro - ext4 x ro\n",false));
    assert(mountedTargets("1 0 259:7 / /data rw - ext4 x rw\n",true));
    assert(!mountedTargets("1 0 259:7 / /data rw - ext4 x rw\n",false));
    assert(!mountedTargets("1 0 179:65 / /sdcard rw - vfat x rw\n",true));
    assert(mountedTargets("invalid mount line\n",true));
    Image images[]={{"system",30,179,30,838860800,""},
                    {"userdata",39,259,7,2566897152ULL,""},
                    {"boot",29,179,29,33554432,""}};
    Bundle fresh={"fresh","x.zip","",0,true,images,3};
    assert(bootLast(fresh));assert(stagingBytes(fresh)>0xffffffffULL/2);
    assert(stagingBytes(fresh)==3472866816ULL);
    fresh.images=images+1;fresh.count=2;assert(!bootLast(fresh));
    assert(key(352)==Key::Accept && key(103)==Key::Up && key(108)==Key::Down);
    assert(key(107)==Key::Back && key(523)==Key::None);
    assert(rgb565(255,0,0)==0xf800 && rgb565(0,255,0)==0x07e0 && rgb565(0,0,255)==0x001f);
    assert(rgb565(255,255,255)==0xffff && rgb565(0,0,0)==0);
    assert(framebufferPageFits(327680,512,320,320,240));
    assert(!framebufferPageFits(327680,512,321,320,240));
    assert(!framebufferPageFits(327680,479,0,320,240));
    assert(!framebufferPageFits(327680,512,0xffffffffu,320,240));
    std::cout<<"Recovery policy checks passed\n";
}
