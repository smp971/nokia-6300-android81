/* Map the stock single-modem QMUX instance to the observed IPC-router instance.
 * Experimental, process-local LD_PRELOAD; never inject into other services. */
extern void *dlopen(const char *, int);
extern void *dlsym(void *, const char *);
extern int __android_log_print(int, const char *, const char *, ...);
static unsigned mapped(unsigned instance) {
    if (instance == 128) {
        __android_log_print(4, "LeoRadio", "QMI instance 128 -> IPC instance 0");
        return 0;
    }
    return instance;
}
int qmi_client_get_service_instance(void *object, unsigned instance, void *info) {
    void *library = dlopen("libqmi_cci.so", 2);
    int (*original)(void *, unsigned, void *) = library ?
        dlsym(library, "qmi_client_get_service_instance") : 0;
    return original ? original(object, mapped(instance), info) : -2;
}
int qmi_client_init_instance(void *object, unsigned instance, void *callback,
        void *data, void *os, unsigned timeout, void **client) {
    void *library = dlopen("libqmi_cci.so", 2);
    int (*original)(void *, unsigned, void *, void *, void *, unsigned, void **) =
        library ? dlsym(library, "qmi_client_init_instance") : 0;
    return original ? original(object, mapped(instance), callback, data, os,
                              timeout, client) : -2;
}
