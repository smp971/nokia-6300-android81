// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <cstdint>
namespace leo {
inline uint16_t rgb565(unsigned r,unsigned g,unsigned b) {
    return ((r>>3)<<11)|((g>>2)<<5)|(b>>3);
}
inline bool framebufferPageFits(uint64_t memory,unsigned pitch,unsigned y,unsigned height,unsigned width) {
    return width>0 && height>0 && pitch>=(uint64_t)width*2 &&
           ((uint64_t)y+height)*pitch<=memory;
}
}
