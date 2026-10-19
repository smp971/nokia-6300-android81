/* Offline object only: encode layout facts as symbol sizes, never load this. */
#include <linux/stddef.h>
#include <linux/platform_device.h>
#include <sound/soc.h>

char size__snd_soc_card[sizeof(struct snd_soc_card)];
char size__snd_soc_dai_link[sizeof(struct snd_soc_dai_link)];
char size__platform_driver[sizeof(struct platform_driver)];
char size__snd_soc_ops[sizeof(struct snd_soc_ops)];
char size__snd_soc_codec_conf[sizeof(struct snd_soc_codec_conf)];
char size__snd_soc_aux_dev[sizeof(struct snd_soc_aux_dev)];
#define OFFSET(type, member) char offset__##type##__##member[offsetof(struct type, member) + 1]
OFFSET(snd_soc_card, name);
OFFSET(snd_soc_card, dev);
OFFSET(snd_soc_card, owner);
OFFSET(snd_soc_card, dai_link);
OFFSET(snd_soc_card, num_links);
OFFSET(snd_soc_card, dapm_widgets);
OFFSET(snd_soc_card, num_dapm_widgets);
OFFSET(snd_soc_card, fully_routed);
OFFSET(snd_soc_card, drvdata);
OFFSET(platform_driver, probe);
OFFSET(platform_driver, driver);
