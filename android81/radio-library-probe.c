/* Load vendor libraries with Android's linker, without calling RIL_Init. */
extern void *dlopen(const char *, int);
extern void *dlsym(void *, const char *);
extern char *dlerror(void);
extern int printf(const char *, ...);
int main(int argc, char **argv) {
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
