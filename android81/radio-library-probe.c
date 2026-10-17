/* Load vendor libraries with Android's linker, without calling RIL_Init. */
extern void *dlopen(const char *, int);
extern void *dlsym(void *, const char *);
extern char *dlerror(void);
extern int printf(const char *, ...);
extern int strcmp(const char *, const char *);
extern int open(const char *, int, ...);
extern int close(int);
extern unsigned sleep(unsigned);
extern void perror(const char *);
int main(int argc, char **argv) {
    if (argc == 2 && !strcmp(argv[1], "--qmi-services")) {
        void *cci = dlopen("libqmi_cci.so", 2);
        void *services = dlopen("libqmiservices.so", 2);
        if (!cci || !services) { printf("%s\n", dlerror()); return 1; }
        int (*list)(void *, void *, unsigned *, unsigned *) =
            dlsym(cci, "qmi_client_get_service_list");
        void (*stop)(void *, void *) = dlsym(cci, "qmi_cci_xport_stop");
        void *qmux = dlsym(cci, "qmuxd_ops");
        /* Query the IPC-router transport independently of legacy QMUX/SMD. */
        if (!stop || !qmux) return 1;
        for (unsigned id = 0; id < 256; id++) stop(qmux, (void *)id);
        const char *names[] = {"voice_get_service_object_internal_v02",
            "uim_get_service_object_internal_v01", "nas_get_service_object_internal_v01"};
        if (!list) return 1;
        for (unsigned n = 0; n < 3; n++) {
            void *(*get)(int, int, int) = dlsym(services, names[n]);
            void *object = 0;
            if (!get) return 1;
            for (int minor = 0; minor < 512 && !object; minor++)
                for (int tool = 1; tool < 10 && !object; tool++)
                    object = get(n == 0 ? 2 : 1, minor, tool);
            unsigned entries = 0, count = 0;
            if (!object) { printf("No object for %s\n", names[n]); return 1; }
            int result = list(object, 0, &entries, &count);
            printf("%s: result=%d service_count=%u\n", names[n], result, count);
            int (*instance)(void *, unsigned, void *) =
                dlsym(cci, "qmi_client_get_service_instance");
            unsigned info[32];
            printf("  instance 0=%d, 128=%d, ANY=%d\n",
                instance(object, 0, info), instance(object, 128, info),
                instance(object, 0xffff, info));
        }
        return 0;
    }
    if (argc == 2 && !strcmp(argv[1], "--boot-modem")) {
        /* Hold the subsystem reference for a bounded firmware boot trial. */
        int fd = open("/dev/subsys_modem", 0);
        if (fd < 0) { perror("open subsys_modem"); return 1; }
        printf("Modem subsystem opened; holding reference for 180 seconds.\n");
        sleep(180);
        close(fd);
        return 0;
    }
    int failed = 0;
    for (int i = 1; i < argc; ++i) {
        void *handle = dlopen(argv[i], 2); /* RTLD_NOW, no radio operations */
        if (!handle) {
            printf("FAIL %s: %s\n", argv[i], dlerror());
            failed = 1;
        } else {
            printf("LOADED %s RIL_Init=%p\n", argv[i], dlsym(handle, "RIL_Init"));
        }
    }
    return failed;
}
