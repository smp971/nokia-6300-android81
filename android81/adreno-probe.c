/* Off-screen stock GPU-driver trial. No window, display takeover or properties. */
extern void *dlopen(const char *, int);
extern void *dlsym(void *, const char *);
extern char *dlerror(void);
extern int printf(const char *, ...);
extern int puts(const char *);
extern void *stdout;
extern void setbuf(void *, char *);
extern unsigned alarm(unsigned);
extern int fsync(int);
extern int open(const char *, int, ...);
extern int *__errno(void);
extern int strcmp(const char *, const char *);
static void stage(const char *name) { puts(name); fsync(1); }
#define LOAD(lib, result, name, args) \
    result (*name) args = dlsym(lib, #name); \
    if (!name) { printf("Missing %s: %s\n", #name, dlerror()); return 2; }
int main(int argc, char **argv) {
    setbuf(stdout, 0);
    alarm(20);
    if (argc > 1) {
        if (argc != 2 || strcmp(argv[1], "--guarded")) return 8;
        /* This path does not exist: testing interposition cannot open real KGSL. */
        int fd = open("/dev/leo-adreno-denied-selftest", 2);
        if (fd != -1 || *__errno() != 19) {
            puts("GUARD_SELFTEST_FAILED: refusing EGL"); return 8;
        }
        stage("GUARD_SELFTEST_OK");
    }
    stage("stage=load-egl");
    void *egl = dlopen("libEGL_adreno.so", 2);
    if (!egl) { printf("EGL load: %s\n", dlerror()); return 1; }
    stage("stage=load-gles");
    void *gl = dlopen("libGLESv2_adreno.so", 2);
    if (!gl) { printf("GLES load: %s\n", dlerror()); return 1; }
    LOAD(egl, void *, eglGetDisplay, (void *));
    LOAD(egl, unsigned, eglInitialize, (void *, int *, int *));
    LOAD(egl, int, eglGetError, (void));
    LOAD(egl, const char *, eglQueryString, (void *, int));
    LOAD(egl, unsigned, eglChooseConfig, (void *, const int *, void **, int, int *));
    LOAD(egl, void *, eglCreatePbufferSurface, (void *, void *, const int *));
    LOAD(egl, void *, eglCreateContext, (void *, void *, void *, const int *));
    LOAD(egl, unsigned, eglMakeCurrent, (void *, void *, void *, void *));
    LOAD(egl, unsigned, eglDestroyContext, (void *, void *));
    LOAD(egl, unsigned, eglDestroySurface, (void *, void *));
    LOAD(egl, unsigned, eglTerminate, (void *));
    LOAD(gl, const char *, glGetString, (unsigned));
    LOAD(gl, void, glClearColor, (float, float, float, float));
    LOAD(gl, void, glClear, (unsigned));
    LOAD(gl, void, glFinish, (void));
    LOAD(gl, void, glReadPixels, (int, int, int, int, unsigned, unsigned, void *));
    LOAD(gl, unsigned, glGetError, (void));
    stage("stage=get-display");
    void *display = eglGetDisplay(0);
    int major = 0, minor = 0;
    stage("stage=initialize");
    if (!eglInitialize(display, &major, &minor)) {
        printf("eglInitialize failed 0x%x\n", eglGetError()); return 3;
    }
    printf("EGL %d.%d vendor=%s version=%s\n", major, minor,
           eglQueryString(display, 0x3053), eglQueryString(display, 0x3054));
    const int attrs[] = {0x3033, 1, 0x3040, 4, 0x3024, 8, 0x3023, 8,
                         0x3022, 8, 0x3038};
    void *config = 0; int count = 0;
    stage("stage=choose-config");
    if (!eglChooseConfig(display, attrs, &config, 1, &count) || count < 1) {
        printf("Config failed 0x%x\n", eglGetError()); return 4;
    }
    const int size[] = {0x3057, 16, 0x3056, 16, 0x3038};
    const int contextAttrs[] = {0x3098, 2, 0x3038};
    stage("stage=create-pbuffer");
    void *surface = eglCreatePbufferSurface(display, config, size);
    stage("stage=create-context");
    void *context = eglCreateContext(display, config, 0, contextAttrs);
    stage("stage=make-current");
    if (!surface || !context || !eglMakeCurrent(display, surface, surface, context)) {
        printf("Context failed 0x%x\n", eglGetError()); return 5;
    }
    printf("GL vendor=%s renderer=%s version=%s\n", glGetString(0x1f00),
           glGetString(0x1f01), glGetString(0x1f02));
    stage("stage=clear-readback");
    glClearColor(0.25f, 0.5f, 0.75f, 1.0f);
    glClear(0x4000); glFinish();
    unsigned char pixel[4] = {0};
    glReadPixels(8, 8, 1, 1, 0x1908, 0x1401, pixel);
    unsigned error = glGetError();
    printf("pixel=%u,%u,%u,%u gl_error=0x%x\n", pixel[0], pixel[1], pixel[2], pixel[3], error);
    int ok = !error && pixel[0] >= 62 && pixel[0] <= 65 && pixel[1] >= 126 &&
             pixel[1] <= 129 && pixel[2] >= 189 && pixel[2] <= 193;
    eglMakeCurrent(display, 0, 0, 0);
    eglDestroyContext(display, context); eglDestroySurface(display, surface);
    eglTerminate(display);
    puts(ok ? "LEO_ADRENO_PBUFFER_OK" : "LEO_ADRENO_PBUFFER_FAILED");
    return ok ? 0 : 6;
}
