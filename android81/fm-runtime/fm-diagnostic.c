/* Passive V4L2 capability probe. No tuner/controls/firmware/audio setters. */
#include <stdio.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>
#include <errno.h>
#include <time.h>
#include <sys/time.h>
#include <sys/ioctl.h>
#include <linux/videodev2.h>
static void expired(int signal_number){(void)signal_number;_exit(124);}
int main(void){signal(SIGALRM,expired);alarm(3);int fd=open("/dev/radio0",O_RDONLY|O_NONBLOCK);if(fd<0){printf("open: %s\n",strerror(errno));return 1;}struct v4l2_capability cap;memset(&cap,0,sizeof(cap));if(ioctl(fd,VIDIOC_QUERYCAP,&cap)<0){printf("QUERYCAP: %s\n",strerror(errno));close(fd);return 2;}printf("driver=%.16s card=%.32s version=%u capabilities=0x%x\n",cap.driver,cap.card,cap.version,cap.capabilities);struct v4l2_tuner tuner;memset(&tuner,0,sizeof(tuner));tuner.type=V4L2_TUNER_RADIO;if(!ioctl(fd,VIDIOC_G_TUNER,&tuner))printf("tuner=%.32s range=%u..%u capability=0x%x signal=%u\n",tuner.name,tuner.rangelow,tuner.rangehigh,tuner.capability,tuner.signal);else printf("G_TUNER: %s\n",strerror(errno));struct v4l2_frequency frequency;memset(&frequency,0,sizeof(frequency));frequency.type=V4L2_TUNER_RADIO;if(!ioctl(fd,VIDIOC_G_FREQUENCY,&frequency))printf("frequency_raw=%u\n",frequency.frequency);else printf("G_FREQUENCY: %s\n",strerror(errno));close(fd);puts("PASS passive queries complete; no enable, tune, firmware or audio command issued");return 0;}
