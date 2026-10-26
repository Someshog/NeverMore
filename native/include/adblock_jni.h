#ifndef NEVERMORE_ADBLOCK_JNI_H
#define NEVERMORE_ADBLOCK_JNI_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Rules are plain filter text or JSON list objects; resources_json is an adblock-rust Resource array. */
void *nevermore_adblock_create_engine(const char *filter_rules, const char *resources_json);

/* Returns 1 if blocked, 0 otherwise. All string arguments must be NUL-terminated UTF-8. */
int32_t nevermore_adblock_should_block(
    void *engine,
    const char *url,
    const char *source_url,
    const char *resource_type);

#ifdef __cplusplus
}
#endif

#endif /* NEVERMORE_ADBLOCK_JNI_H */
