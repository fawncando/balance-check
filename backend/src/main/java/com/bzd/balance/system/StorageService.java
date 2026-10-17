package com.bzd.balance.system;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StorageService {

    private final Path root;

    public StorageService(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("path", root.toString().replace('\\', '/'));
        try {
            Files.createDirectories(root);
            FileStore store = Files.getFileStore(root);
            long total = store.getTotalSpace();
            long free = store.getUsableSpace();
            stats.put("totalGb", roundGb(total));
            stats.put("freeGb", roundGb(free));
            stats.put("usedPercent", total > 0 ? Math.round((total - free) * 1000.0 / total) / 10.0 : null);
            stats.put("writable", Files.isWritable(root));
        } catch (IOException ex) {
            stats.put("writable", false);
            stats.put("error", ex.getMessage());
        }
        return stats;
    }

    private double roundGb(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0 / 1024.0 * 10.0) / 10.0;
    }
}
