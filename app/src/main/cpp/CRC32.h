#ifndef CRC32_H
#define CRC32_H

#include <stdint.h>
#include <stddef.h>

static uint32_t crc32_table[256];
static int crc32_table_computed = 0;

static void generate_crc32_table() {
    uint32_t c;
    for (int n = 0; n < 256; n++) {
        c = (uint32_t)n;
        for (int k = 0; k < 8; k++) {
            if (c & 1)
                c = 0xEDB88320L ^ (c >> 1);
            else
                c = c >> 1;
        }
        crc32_table[n] = c;
    }
    crc32_table_computed = 1;
}

static uint32_t crc32_update(uint32_t crc, const uint8_t *buf, size_t len) {
    if (!crc32_table_computed)
        generate_crc32_table();
        
    uint32_t c = crc;
    for (size_t n = 0; n < len; n++) {
        c = crc32_table[(c ^ buf[n]) & 0xff] ^ (c >> 8);
    }
    return c;
}

static uint32_t crc32(const uint8_t *buf, size_t len) {
    return crc32_update(0xffffffffL, buf, len) ^ 0xffffffffL;
}

#endif // CRC32_H
