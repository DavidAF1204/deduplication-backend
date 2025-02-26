package com.example.dedup;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequiredArgsConstructor
public class DedupController {
    private final DedupService dedupService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("minChunk") int minChunk,
            @RequestParam("avgChunk") int avgChunk,
            @RequestParam("maxChunk") int maxChunk) throws IOException {
        
        dedupService.uploadFile(file, minChunk, avgChunk, maxChunk);
        DedupStatistics stats = dedupService.getStatistics();
        
        return ResponseEntity.ok(
                "File uploaded successfully!\n" +
                "Deduplication Statistics:\n" +
                "- Total files: " + stats.getTotalFiles() + "\n" +
                "- Deduplication ratio: " + String.format("%.2f", stats.getDeduplicationRatio())
        );
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("filePath") String filePath) throws IOException {
        byte[] data = dedupService.downloadFile(filePath);
        ByteArrayResource resource = new ByteArrayResource(data);
        
        Path path = Paths.get(filePath);
        String filename = path.getFileName().toString();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentLength(data.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteFile(@RequestParam("filePath") String filePath) {
        dedupService.deleteFile(filePath);
        return ResponseEntity.ok("File deleted successfully: " + filePath);
    }

    @GetMapping("/stats")
    public ResponseEntity<DedupStatistics> getStatistics() {
        return ResponseEntity.ok(dedupService.getStatistics());
    }
}