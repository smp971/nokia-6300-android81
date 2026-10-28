#include "policy.h"
#include <cassert>
#include <iostream>
int main() {
    using namespace leo;
    assert(mountReadWrite("rw,nosuid,nodev,noexec"));
    assert(!mountReadWrite("ro,nosuid,nodev"));
    assert(correctPhone("console=x androidboot.serialno=695d quiet"));
    assert(!correctPhone("androidboot.serialno=695devil"));
    assert(!correctPhone("xandroidboot.serialno=695d"));
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
    std::cout<<"Recovery policy checks passed\n";
}
