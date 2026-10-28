/* Partial reader linked with scoped policydb.c: print symbol IDs, never rewrite rules. */
#include <stdio.h>
#include <stdlib.h>
#include <sepol/policydb/policydb.h>
#include <sepol/policydb/hashtab.h>

int main(int argc, char **argv) {
    if (argc != 2 && argc != 3) return 2;
    policydb_t db;
    policy_file_t pf;
    if (policydb_init(&db)) return 3;
    policy_file_init(&pf);
    pf.type = PF_USE_STDIO;
    pf.fp = fopen(argv[1], "rb");
    if (!pf.fp || policydb_read(&db, &pf, 0)) return 4;
    fclose(pf.fp);
    const char *domains[] = {"adbd", "shell"};
    for (unsigned i = 0; i < (argc == 3 ? 1u : 2u); i++) {
        const char *name = argc == 3 ? argv[2] : domains[i];
        type_datum_t *t = hashtab_search(db.p_types.table, name);
        if (!t || t->flavor != TYPE_TYPE) return 5;
        printf("%s %u\n", name, t->s.value);
    }
    policydb_destroy(&db);
    return 0;
}
