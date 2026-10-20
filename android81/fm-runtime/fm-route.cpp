// Route only the primary music output; leave Android policy preferences unchanged.
#include <media/AudioSystem.h>
#include <utils/String8.h>
#include <stdio.h>
#include <string.h>
using namespace android;
int main(int argc,char **argv){
    if(argc!=2)return 2;
    audio_devices_t devices=AudioSystem::getDevicesForStream(AUDIO_STREAM_MUSIC);
    int target=!strcmp(argv[1],"restore")?((devices&AUDIO_DEVICE_OUT_WIRED_HEADSET)?4:(devices&AUDIO_DEVICE_OUT_WIRED_HEADPHONE)?8:2):!strcmp(argv[1],"2")?2:!strcmp(argv[1],"4")?4:0;
    if(!target)return 2;
    audio_io_handle_t output=AudioSystem::getOutput(AUDIO_STREAM_MUSIC,48000,AUDIO_FORMAT_PCM_16_BIT,AUDIO_CHANNEL_OUT_STEREO,AUDIO_OUTPUT_FLAG_PRIMARY);
    if(output==AUDIO_IO_HANDLE_NONE)return 3;
    char routing[32];snprintf(routing,sizeof(routing),"routing=%d",target);
    status_t stream=AudioSystem::setParameters(output,String8(routing));
    status_t fm=AudioSystem::setParameters(String8(routing));
    AudioSystem::releaseOutput(output,AUDIO_STREAM_MUSIC,AUDIO_SESSION_NONE);
    printf("FM_ROUTE target=%d stream=%d fm=%d\n",target,stream,fm);
    return stream==NO_ERROR&&fm==NO_ERROR?0:1;
}
