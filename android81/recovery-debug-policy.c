/* Partial reader linked with scoped policydb.c: print symbol IDs, never rewrite rules. */
#include <stdio.h>
#include <stdlib.h>
#include <sepol/policydb/policydb.h>
#include <sepol/policydb/hashtab.h>

int main(int argc, char **argv) {
    if (argc != 2) return 2;
    policydb_t db;
    policy_file_t pf;
    if (policydb_init(&db)) return 3;
    policy_file_init(&pf);
    pf.type = PF_USE_STDIO;
    pf.fp = fopen(argv[1], "rb");
    if (!pf.fp || policydb_read(&db, &pf, 0)) return 4;
    fclose(pf.fp);
    const char *domains[] = {"adbd", "shell"};
    for (unsigned i = 0; i < 2; i++) {
        type_datum_t *t = hashtab_search(db.p_types.table, domains[i]);
        if (!t || t->flavor != TYPE_TYPE) return 5;
        printf("%s %u\n", domains[i], t->s.value);
    }
    policydb_destroy(&db);
    return 0;
}
