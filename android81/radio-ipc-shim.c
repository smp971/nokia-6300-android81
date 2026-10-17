/* Map the stock single-modem QMUX instance to the observed IPC-router instance.
 * Experimental, process-local LD_PRELOAD; never inject into other services. */
extern void *dlopen(const char *, int);
extern void *dlsym(void *, const char *);
extern int __android_log_print(int, const char *, const char *, ...);
extern void *malloc(unsigned);
extern void free(void *);
typedef void (*indication_fn)(void *, unsigned, void *, unsigned, void *);
struct indication_context { indication_fn callback; void *data; unsigned service; };
static void indication(void *client, unsigned message, void *buffer, unsigned length, void *data) {
    struct indication_context *context = data;
    __android_log_print(4, "LeoRadio", "QMI indication service=%u message=0x%x length=%u",
                        context->service, message, length);
    context->callback(client, message, buffer, length, context->data);
}
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
    if (!original) return -2;
    struct indication_context *context = callback ? malloc(sizeof(*context)) : 0;
    if (callback && !context) return -2;
    if (context) {
        int (*service_id)(void *, unsigned *) = dlsym(library, "qmi_idl_get_service_id");
        context->callback = (indication_fn)callback;
        context->data = data;
        context->service = 0;
        if (service_id) service_id(object, &context->service);
    }
    int result = original(object, mapped(instance), context ? (void *)indication : callback,
                          context ? context : data, os, timeout, client);
    __android_log_print(4, "LeoRadio", "QMI init service=%u instance=%u result=%d",
                        context ? context->service : 0, instance, result);
    if (result && context) free(context);
    /* Successful callback contexts live until this bounded trial process exits. */
    return result;
}
