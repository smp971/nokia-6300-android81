/* Query DMS directly through the existing Qualcomm IPC transport.
 * Default is read-only. --online explicitly requests operating mode ONLINE.
 * Message layout: libqmi/data/qmi-service-dms.json (Get/Set Operating Mode).
 * QCCI signatures: qualcomm/qmi-framework/include/qmi_cci.h. */
extern void *dlopen(const char *, int);
extern void *dlsym(void *, const char *);
extern char *dlerror(void);
extern int printf(const char *, ...);
extern int strcmp(const char *, const char *);
int main(int argc, char **argv) {
    int online = argc == 2 && !strcmp(argv[1], "--online");
    if (argc > 1 && !online) return 2;
    void *cci = dlopen("libqmi_cci.so", 2);
    void *services = dlopen("libqmiservices.so", 2);
    if (!cci || !services) { printf("%s\n", dlerror()); return 1; }
    void *(*get)(int, int, int) = dlsym(services, "dms_get_service_object_internal_v01");
    int (*lookup)(void *, unsigned, void *) = dlsym(cci, "qmi_client_get_service_instance");
    int (*init)(void *, void *, void *, void *, void *, void **) = dlsym(cci, "qmi_client_init");
    int (*send)(void *, unsigned, void *, unsigned, void *, unsigned, unsigned *, unsigned) =
        dlsym(cci, "qmi_client_send_raw_msg_sync");
    int (*release)(void *) = dlsym(cci, "qmi_client_release");
    if (!get || !lookup || !init || !send || !release) return 1;
    void *object = 0, *client = 0;
    for (int minor = 0; minor < 512 && !object; minor++) object = get(1, minor, 6);
    if (!object) return 1;
    unsigned info[32] = {0};
    unsigned long long os[64] = {0};
    int rc = lookup(object, 0, info);
    printf("DMS lookup=%d\n", rc);
    if (rc) return 1;
    rc = init(info, object, 0, 0, os, &client);
    printf("DMS init=%d\n", rc);
    if (rc) return 1;
    unsigned char response[512], request[] = {1, 1, 0, 0};
    unsigned size = 0;
    if (online) {
        rc = send(client, 0x2e, request, sizeof(request), response, sizeof(response), &size, 5000);
        printf("Set ONLINE transport=%d, bytes=%u\n", rc, size);
        if (rc) { release(client); return 1; }
        for (unsigned i = 0; i < size; i++) printf("%02x ", response[i]);
        printf("\n");
    }
    rc = send(client, 0x2d, 0, 0, response, sizeof(response), &size, 5000);
    printf("Get Operating Mode transport=%d, bytes=%u\n", rc, size);
    if (!rc) for (unsigned i = 0; i+3 <= size;) {
        unsigned tag = response[i], length = response[i+1] | response[i+2]<<8;
        i += 3;
        if (length > size-i) break;
        if (tag == 1 && length >= 1) printf("Operating mode=%u (0=ONLINE, 1=LOW_POWER)\n", response[i]);
        if (tag == 2 && length >= 4) printf("QMI result=%u error=%u\n",
            response[i] | response[i+1]<<8, response[i+2] | response[i+3]<<8);
        i += length;
    }
    release(client);
    return rc != 0;
}
