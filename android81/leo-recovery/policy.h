// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <cstdint>
#include <sstream>
#include <string>

namespace leo {
struct Image {
    const char* name;
    unsigned partition, major, minor;
    uint64_t bytes;
    const char* sha;
};
struct Bundle {
    const char* title;
    const char* filename;
    const char* sha;
    uint64_t archiveBytes;
    bool wipe;
    const Image* images;
    unsigned count;
};
inline bool token(const std::string& text,const std::string& wanted) {
    std::istringstream stream(text);std::string word;
    while(stream>>word)if(word==wanted)return true;
    return false;
}
inline bool mountReadWrite(const std::string& options) {
    std::istringstream words(options);std::string word;
    while(std::getline(words,word,','))if(word=="rw")return true;
    return false;
}
inline bool correctPhone(const std::string& cmdline) {
    return token(cmdline,"androidboot.serialno=695d");
}
inline bool mountedTargets(const std::string& mounts,bool wipe) {
    if(mounts.empty())return true;
    std::istringstream stream(mounts);std::string line;
    while(std::getline(stream,line)) {
        std::istringstream row(line);std::string id,parent,device,root,path,options;
        if(!(row>>id>>parent>>device>>root>>path>>options))return true; // malformed input: fail closed
        if(device=="179:30" || (wipe && device=="259:7"))return true;
    }
    return false;
}
inline bool bootLast(const Bundle& bundle) {
    if(bundle.count!=(bundle.wipe?3u:2u))return false;
    if(std::string(bundle.images[0].name)!="system" ||
       std::string(bundle.images[bundle.count-1].name)!="boot")return false;
    return !bundle.wipe || std::string(bundle.images[1].name)=="userdata";
}
inline uint64_t stagingBytes(const Bundle& bundle) {
    uint64_t bytes=32u*1024u*1024u;
    for(unsigned i=0;i<bundle.count;++i)bytes+=bundle.images[i].bytes;
    return bytes;
}
// Recovery uses raw Linux EV_KEY codes, independent of Android keylayout files.
enum class Key { None,Up,Down,Accept,Back };
inline Key key(unsigned code) {
    switch(code) {
        case 103:return Key::Up;
        case 108:return Key::Down;
        case 352:case 28:case 231:case 139:return Key::Accept;
        case 158:case 14:case 107:return Key::Back;
        default:return Key::None;
    }
}
}
