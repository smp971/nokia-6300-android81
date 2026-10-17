/* Investigation only: deny device opens and ALL ioctls inside this process.
 * No mode permits hardware access. This is not a graphics compatibility shim.
 */
extern void *dlopen(const char *, int);
extern void *dlsym(void *, const char *);
extern int strncmp(const char *, const char *, unsigned);
extern int snprintf(char *, unsigned, const char *, ...);
extern int write(int, const void *, unsigned);
extern int fsync(int);
extern int *__errno(void);
extern char *getenv(const char *);
extern int strcmp(const char *, const char *);
static void denied(const char *operation, const char *path, unsigned request) {
    char buffer[512];
    int n = snprintf(buffer, sizeof(buffer), "GUARD_DENIED %s path=%.300s request=0x%x\n",
                     operation, path ? path : "-", request);
    if (n > 0 && (unsigned)n < sizeof(buffer)) write(2, buffer, (unsigned)n);
    fsync(2);
    *__errno() = 19; /* ENODEV */
}
int open(const char *path, int flags, ...) {
    int (*original)(const char *, int, ...) = dlsym(dlopen("libc.so", 2), "open");
    if (path && !strncmp(path, "/dev/", 5)) {
        const char *fake = getenv("LEO_ADRENO_FAKE_DEVICES");
        if (fake && !strcmp(fake, "1") &&
                (!strcmp(path, "/dev/kgsl-3d0") || !strcmp(path, "/dev/ion"))) {
            denied("fake-regular-file", path, 0);
            return original("/data/local/tmp/leo-adreno-fake-device", 66, 0600);
        }
        denied("open", path, 0); return -1;
    }
    unsigned mode = 0;
    if (flags & 64) {
        __builtin_va_list ap; __builtin_va_start(ap, flags);
        mode = __builtin_va_arg(ap, unsigned); __builtin_va_end(ap);
    }
    return original(path, flags, mode);
}
int ioctl(int fd, unsigned request, ...) {
    (void)fd;
    denied("ioctl", 0, request); return -1;
}
void *fopen(const char *path, const char *mode) {
    if (path && !strncmp(path, "/dev/", 5)) {
        denied("fopen", path, 0); return 0;
    }
    void *(*original)(const char *, const char *) = dlsym(dlopen("libc.so", 2), "fopen");
    return original(path, mode);
}
