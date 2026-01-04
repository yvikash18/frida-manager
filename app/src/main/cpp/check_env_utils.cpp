#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include "check_env_utils.h"
#include <ctype.h>

char *find_path_from_maps(const char *soname) {
    FILE *fp = fopen(self_maps, "r");
    if (fp == NULL) {
        return NULL;
    }

    char line[MAX_LINE];
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, soname)) {
            char *start = strchr(line, '/');
            if (start) {
                char *path = strdup(start);
                if (path != NULL) {
                    size_t len = strlen(path);
                    if (len > 0 && path[len - 1] == '\n') {
                        path[len - 1] = '\0';
                    }
                }
                fclose(fp);
                return path;
            }
        }
    }

    fclose(fp);
    return NULL;
}


struct map_info find_info_from_maps(const char *soname, const char *perm) {
    struct map_info info = {0, 0, 0, ""};
    FILE *maps = fopen(self_maps, "r");
    if (!maps) {
        return info;
    }

    char line[512];
    while (fgets(line, sizeof(line), maps)) {
        unsigned long start, end;
        off_t offset;
        char perms[5], pathname[256];
        // Initialize pathname to empty string to avoid garbage if sscanf doesn't match it
        pathname[0] = '\0';
        
        int fields = sscanf(line, "%lx-%lx %4s %lx %*s %*s %255s",
                            &start, &end, perms, &offset, pathname);

        if (fields >= 4 && // pathname might be empty for anon maps, but we check soname
            strstr(perms, perm) &&
            strstr(pathname, soname)) {
            info.start = start;
            info.size = end - start;
            info.offset = offset;
            strncpy(info.pathname, pathname, sizeof(info.pathname) - 1);
            info.pathname[sizeof(info.pathname) - 1] = '\0';
            break;
        }
    }

    fclose(maps);
    return info;
}


int is_linker_sensitive(const char* soname, const char** sensitive_words) {
    if (soname == NULL || sensitive_words == NULL) {
        return -1;
    }

    int i = 0;
    while (sensitive_words[i] != NULL) {
        if (strstr(soname, sensitive_words[i]) != NULL)
            return 1;
        i++;
    }

    return 0;
}


int is_maps_sensitive(const char** sensitive_words, const char* map_name) {
    FILE *fp;
    char line[MAX_LINE];
    int word_count = 0;

    if (sensitive_words == NULL) {
        return -1;
    }
    while (sensitive_words[word_count] != NULL) {
        word_count++;
    }
    if (word_count == 0) {
        return -1;
    }

    fp = fopen(map_name, "r");
    if (fp == NULL) {
        return -1;
    }

    while (fgets(line, sizeof(line), fp) != NULL) {
        for (int i = 0; i < word_count; i++) {
            if (strstr(line, sensitive_words[i]) != NULL) {
                fclose(fp);
                return 1;
            }
        }
    }

    fclose(fp);

    return 0;
}


int count_maps_sensitive(const char* sensitive_word, char is_count, const char* map_name) {
    FILE *fp;
    char line[MAX_LINE];
    int count = 0;

    fp = fopen(map_name, "r");
    if (fp == NULL) {
        return -1;
    }

    while (fgets(line, sizeof(line), fp) != NULL) {
        if (strstr(line, sensitive_word) != NULL) {
            if (is_count <= 0) {
                fclose(fp);
                return 1;
            }
            count++;
        }
    }

    fclose(fp);

    return (is_count > 0) ? count : 0;
}


int has_anon_exec_memory(const char* map_name) {
    FILE *fp;
    char line[MAX_LINE];

    fp = fopen(map_name, "r");
    if (fp == NULL) {
        return -1;
    }

    int is_smaps = (strcmp(map_name, self_smaps) == 0);
    int in_block = 0;
    int is_executable = 0;
    int is_anonymous = 0;

    while (fgets(line, sizeof(line), fp) != NULL) {
        if (is_smaps) {
            if (strchr(line, '-') != NULL) {
                if (in_block && is_executable && is_anonymous) {
                    fclose(fp);
                    return 1;
                }
                in_block = 1;
                is_executable = 0;
                is_anonymous = 0;

                if (strstr(line, "r-xp") != NULL || strstr(line, "--xp") != NULL)
                    is_executable = 1;

                char *filename_start = strchr(line, '/');
                if (filename_start == NULL) {
                    char *end = strchr(line, '\n');
                    if (end != NULL) {
                        *end = '\0';
                        char *last_space = strrchr(line, ' ');
                        if (last_space != NULL && *(last_space + 1) == '\0') {
                            is_anonymous = 1;
                        } else if (last_space == NULL) {
                             // Assuming checking for end of line emptiness if fields differ
                        }
                    }
                }
            }

        } else {

            if (strstr(line, "r-xp") != NULL || strstr(line, "--xp") != NULL) {
                char *filename_start = strchr(line, '/');
                if (filename_start == NULL) {
                    char *end = strchr(line, '\n');
                    if (end != NULL) {
                        *end = '\0';
                        char *last_space = strrchr(line, ' ');
                        if (last_space != NULL && *(last_space + 1) == '\0') {
                            fclose(fp);
                            return 1;
                        }
                    }
                }
            }
        }
    }

    if (is_smaps && in_block && is_executable && is_anonymous) {
        fclose(fp);
        return 1;
    }

    fclose(fp);
    return 0;
}


int scan_mem_keywords(const char** keywords) {
    FILE* maps = fopen(self_maps, "r");
    if (!maps)
        return -1;

    int mem_fd = open(self_mem, O_RDONLY);
    if (mem_fd < 0) {
        fclose(maps);
        return -1;
    }

    char line[256];
    while (fgets(line, sizeof(line), maps)) {
        unsigned long start, end;
        char perms[5], pathname[256];
        pathname[0] = 0;
        if (sscanf(line, "%lx-%lx %4s %*s %*s %*s %255s", &start, &end, perms, pathname) < 3) {
            continue;
        }
        if (perms[0] != 'r' || perms[2] != 'x') // Check for r-x
            continue;

        if (strstr(pathname, APP_NAME) != NULL)
            continue;


        size_t size = end - start;
        // Limit scanning size to avoid OOM or huge delays, or just try aggressive
        if (size > 10 * 1024 * 1024) { 
            // example limit 10MB per segment? User didn't have limit, but malloc might fail on large.
            // keeping as is but checking result
        }

        char* buffer = (char*)malloc(size);
        if (!buffer)
            continue;

        if (lseek(mem_fd, start, SEEK_SET) == -1) {
            free(buffer);
            continue;
        }

        ssize_t bytes_read = read(mem_fd, buffer, size);
        if (bytes_read <= 0) {
            free(buffer);
            continue;
        }

        for (int i = 0; keywords[i] != NULL; i++) {
            size_t keyword_len = strlen(keywords[i]);
            void* result = memmem(buffer, bytes_read, keywords[i], keyword_len);
            if (result) {
                LOGD(LOG_TAG "找到关键字 '%s' 在地址 %lx-%lx 中的偏移 %ld\n",
                     keywords[i], start, end, (long)((char*)result - buffer));
                free(buffer);
                fclose(maps);
                close(mem_fd);
                return 1;
            }
        }

        free(buffer);
    }

    fclose(maps);
    close(mem_fd);
    return 0;
}


int scan_task_status(const char** task_name) {
    DIR *dir = opendir(self_task);
    if (dir == NULL)
        return 0;

    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
            continue;
        }

        char file_path[MAX_LINE];
        snprintf(file_path, sizeof(file_path), task_status, entry->d_name);

        FILE *fp = fopen(file_path, "r");
        if (fp == NULL)
            continue;


        char buffer[MAX_LINE];
        if (fgets(buffer, MAX_LINE, fp) == NULL) {
            fclose(fp);
            continue;
        }

        fclose(fp);

        buffer[strcspn(buffer, "\n")] = '\0';

        for (int j = 0; task_name[j] != NULL; j++) {
            if (strstr(buffer, task_name[j]) != NULL) {
                closedir(dir);
                return 1;
            }
        }
    }

    closedir(dir);
    return 0;
}


int check_lib_integrity(const char *soname) { 

    struct map_info exec_info = find_info_from_maps(soname, "x");
    if (exec_info.start == 0 && exec_info.size == 0)
        return -1;


    struct map_info data_info = find_info_from_maps(soname, "rw");
    if (data_info.start == 0 && data_info.size == 0) {
        // Just log, don't fail immediately if rw is missing, but usually it exists.
    }

    int fd = open(exec_info.pathname, O_RDONLY);
    if (fd < 0)
        return -1;

    off_t file_size = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);

    void *file_data = mmap(NULL, file_size, PROT_READ, MAP_PRIVATE, fd, 0);
    if (file_data == MAP_FAILED) {
        close(fd);
        return -1;
    }

    close(fd);

    Elf64_Ehdr *ehdr = (Elf64_Ehdr *)file_data;
    if (memcmp(ehdr->e_ident, ELFMAG, SELFMAG) != 0) {
        munmap(file_data, file_size);
        return -1;
    }

    Elf64_Phdr *phdr = (Elf64_Phdr *)((char*)file_data + ehdr->e_phoff);
    uint32_t exec_disk_crc = 0, data_disk_crc = 0;
    size_t exec_segment_size = 0, data_segment_size = 0;
    int found_exec = 0, found_data = 0;

    // 解析 ELF 文件中的段
    for (int i = 0; i < ehdr->e_phnum; i++) {
        if (phdr[i].p_type == PT_LOAD) {
            if ((phdr[i].p_flags & PF_X) && phdr[i].p_offset == exec_info.offset) { 
                exec_disk_crc = crc32((uint8_t *)file_data + phdr[i].p_offset, phdr[i].p_memsz);
                exec_segment_size = phdr[i].p_memsz;
                found_exec = 1;
            } else if ((phdr[i].p_flags & PF_W) && data_info.start != 0 && phdr[i].p_offset == data_info.offset) { 
                data_disk_crc = crc32((uint8_t *)file_data + phdr[i].p_offset, phdr[i].p_memsz);
                data_segment_size = phdr[i].p_memsz;
                found_data = 1;
            }
        }
    }

    if (!found_exec) {
        munmap(file_data, file_size);
        return -1;
    }

    void *exec_mem_data = (void *)exec_info.start;
    if (mprotect(exec_mem_data, exec_segment_size, PROT_READ | PROT_EXEC) < 0) {
        // Depending on SELinux or other restrictions, mprotect might fail if we ask for EXEC on something not mapped as such? 
        // But we are reading it. Usually just PROT_READ is enough to CRC it? 
        // Although if it's executable, we can just read it. 
        // If we want to be safe, maybe don't mprotect if we already have read access? 
        // The snippet did mprotect via PROT_READ | PROT_EXEC.
    }
    uint32_t exec_mem_crc = crc32((uint8_t *)exec_mem_data, exec_segment_size);

    int data_modified = 0;
    uint32_t data_mem_crc = 0;
    if (found_data && data_info.start != 0) {
        void *data_mem_data = (void *)data_info.start;
        // We probably shouldn't mprotect data segment to PROT_READ|PROT_WRITE blindly if it's already usable, or if it might mess up something?
        // But for reading, we just need PROT_READ.
        // The original code did PROT_READ | PROT_WRITE.
         if (mprotect(data_mem_data, data_segment_size, PROT_READ | PROT_WRITE) < 0) {
            // LOGD("Failed to mprotect data memory at %p:", data_mem_data);
        } else {
            data_mem_crc = crc32((uint8_t *)data_mem_data, data_segment_size);
            data_modified = (data_disk_crc != data_mem_crc);
        }
    }

    munmap(file_data, file_size);

    if (exec_mem_crc != exec_disk_crc) {
        LOGD("%s executable segment hooked: disk_crc=%08x, mem_crc=%08x",
             soname, exec_disk_crc, exec_mem_crc);
        return 1;
    }
    if (data_modified) {
        LOGD("%s data segment hooked: disk_crc=%08x, mem_crc=%08x",
             soname, data_disk_crc, data_mem_crc);
        return 1;
    }

    // LOGD("%s is not hooked: exec_disk=%08x, exec_mem=%08x, data_disk=%08x, data_mem=%08x",
    //      soname, exec_disk_crc, exec_mem_crc, data_disk_crc, data_mem_crc);
    return 0;
}


int check_all_libs_integrity(const char** so_name_list) {

    for (int i = 0; so_name_list[i] != NULL; i++) {
        const char* soname = so_name_list[i];
        int status = check_lib_integrity(soname);

        if (status == -1) {
            // LOGD("Failed to check %s: CRC calculation error", soname);
        }
        else
            if (status == 1)
                return 1;
    }

    return 0;
}


size_t get_symbol_offset(const char *file_path, const char *symbol_name) {
    int fd = open(file_path, O_RDONLY);
    if (fd == -1) {
        return 0;
    }

    off_t file_size = lseek(fd, 0, SEEK_END);
    lseek(fd, 0, SEEK_SET);

    void *elf_data = mmap(NULL, file_size, PROT_READ, MAP_PRIVATE, fd, 0);
    if (elf_data == MAP_FAILED) {
        close(fd);
        return 0;
    }

    Elf64_Ehdr *ehdr = (Elf64_Ehdr *)elf_data; // ELF 头
    Elf64_Shdr *shdr = (Elf64_Shdr *)((char*)elf_data + ehdr->e_shoff); // 节表
    Elf64_Shdr *sh_strtab = &shdr[ehdr->e_shstrndx]; // 节名字符串表
    const char *sh_strtab_data = (const char *)((char*)elf_data + sh_strtab->sh_offset);

    Elf64_Shdr *symtab = NULL, *strtab = NULL;
    for (int i = 0; i < ehdr->e_shnum; i++) {
        const char *section_name = sh_strtab_data + shdr[i].sh_name;
        if (strcmp(section_name, ".symtab") == 0) {
            symtab = &shdr[i];
        } else if (strcmp(section_name, ".strtab") == 0) {
            strtab = &shdr[i];
        }
    }

    if (!symtab || !strtab) {
        munmap(elf_data, file_size);
        close(fd);
        return 0;
    }

    // 解析符号表
    Elf64_Sym *sym = (Elf64_Sym *)((char*)elf_data + symtab->sh_offset);
    const char *str = (const char *)((char*)elf_data + strtab->sh_offset);
    int sym_count = symtab->sh_size / sizeof(Elf64_Sym);

    for (int i = 0; i < sym_count; i++) {
        const char *name = str + sym[i].st_name;
        if (strstr(name, symbol_name)) {
            size_t offset = sym[i].st_value;
            munmap(elf_data, file_size);
            close(fd);
            return offset;
        }
    }

    munmap(elf_data, file_size);
    close(fd);
    return 0;
}


size_t get_func_address(const char *soname, const char *symbol_name){
    size_t base = find_info_from_maps(soname, NULL).start;
    size_t offset = get_symbol_offset(find_path_from_maps(soname), symbol_name);

    return base + offset;
}
