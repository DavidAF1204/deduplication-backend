package com.example.dedup;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DedupService {
    private final FileRepository fileRepository;
    private final ChunkRepository chunkRepository;
    private final ContainerRepository containerRepository;

    @Value("${dedup.container-size:1048576}")
    private int CONTAINER_SIZE;
    private static final int D = 257; // Rabin fingerprint multiplier
    
    private ByteArrayOutputStream containerBuffer = new ByteArrayOutputStream();
    private List<ChunkEntity> containerChunks = new ArrayList<>();

    @Transactional
    public void uploadFile(MultipartFile file, int minChunk, int avgChunk, int maxChunk) throws IOException {
        String filePath = file.getOriginalFilename();

        // Validate file doesn't already exist
        if (fileRepository.existsById(filePath)) {
            throw new IllegalArgumentException("File already exists: " + filePath);
        }

        // Validate chunk sizes are powers of 2
        if (!isPowerOfTwo(minChunk) || !isPowerOfTwo(avgChunk) || !isPowerOfTwo(maxChunk)) {
            throw new IllegalArgumentException("Chunk sizes must be powers of 2");
        }

        FileEntity fileEntity = new FileEntity();
        fileEntity.setFilePath(filePath);
        
        byte[] buffer = file.getBytes();
        fileEntity.setSize(buffer.length);
        
        // Perform Rabin fingerprinting
        List<byte[]> chunks = rabinChunking(buffer, minChunk, avgChunk, maxChunk);
        
        for (byte[] chunk : chunks) {
            String checksum = calculateMD5(chunk);
            fileEntity.getChunkFingerprints().add(checksum);
            
            ChunkEntity chunkEntity = chunkRepository.findById(checksum).orElse(null);
            if (chunkEntity == null) {
                // New unique chunk
                chunkEntity = new ChunkEntity(checksum, chunk.length);
                chunkEntity.getFiles().add(filePath);
                
                // Add to container buffer
                if (containerBuffer.size() + chunk.length > CONTAINER_SIZE) {
                    flushContainer();
                }
                containerBuffer.write(chunk);
                containerChunks.add(chunkEntity);
            } else {
                // Existing chunk
                chunkEntity.getFiles().add(filePath);
                chunkRepository.save(chunkEntity);
            }
        }

        // Flush remaining chunks
        if (containerBuffer.size() > 0) {
            flushContainer();
        }

        fileRepository.save(fileEntity);
    }

    @Transactional
    public byte[] downloadFile(String filePath) throws IOException {
        FileEntity fileEntity = fileRepository.findById(filePath)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + filePath));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        for (String fingerprint : fileEntity.getChunkFingerprints()) {
            ChunkEntity chunkEntity = chunkRepository.findById(fingerprint)
                    .orElseThrow(() -> new IOException("Chunk not found: " + fingerprint));
            
            ContainerEntity container = containerRepository.findById(chunkEntity.getContainerId())
                    .orElseThrow(() -> new IOException("Container not found: " + chunkEntity.getContainerId()));
            
            byte[] chunk = new byte[chunkEntity.getSize()];
            System.arraycopy(container.getData(), chunkEntity.getOffset(), chunk, 0, chunkEntity.getSize());
            outputStream.write(chunk);
        }
        
        return outputStream.toByteArray();
    }

    @Transactional
    public void deleteFile(String filePath) {
        FileEntity fileEntity = fileRepository.findById(filePath)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + filePath));

        // Remove file reference from chunks and delete unreferenced chunks
        for (String fingerprint : fileEntity.getChunkFingerprints()) {
            ChunkEntity chunkEntity = chunkRepository.findById(fingerprint).orElse(null);
            if (chunkEntity != null) {
                chunkEntity.getFiles().remove(filePath);
                
                if (chunkEntity.getFiles().isEmpty()) {
                    chunkRepository.delete(chunkEntity);
                } else {
                    chunkRepository.save(chunkEntity);
                }
            }
        }

        // Delete the file entity
        fileRepository.deleteById(filePath);

        // Clean up empty containers
        List<Integer> containerIds = containerRepository.findAll().stream()
                .map(ContainerEntity::getId)
                .collect(Collectors.toList());
        
        for (Integer containerId : containerIds) {
            List<ChunkEntity> chunks = chunkRepository.findByContainerId(containerId);
            if (chunks.isEmpty()) {
                containerRepository.deleteById(containerId);
            }
        }
    }

    @Transactional(readOnly = true)
    public DedupStatistics getStatistics() {
        long totalFiles = fileRepository.countFiles();
        long uniqueChunks = chunkRepository.countChunks();
        long totalBytes = fileRepository.sumFileSize();
        long uniqueBytes = chunkRepository.sumChunkSize();
        long totalContainers = containerRepository.count();
        
        // Calculate total chunks (pre-deduplicated)
        long totalChunks = fileRepository.findAll().stream()
                .mapToLong(file -> file.getChunkFingerprints().size())
                .sum();
        
        double deduplicationRatio = totalBytes > 0 ? (double) totalBytes / uniqueBytes : 0;
        
        return new DedupStatistics(
                totalFiles,
                totalChunks,
                uniqueChunks,
                totalBytes,
                uniqueBytes,
                totalContainers,
                deduplicationRatio
        );
    }

    private void flushContainer() throws IOException {
        if (containerBuffer.size() > 0) {
            // Get next container ID
            Integer maxId = containerRepository.findMaxContainerId();
            int nextId = maxId != null ? maxId + 1 : 0;
            
            // Create and save container
            byte[] data = containerBuffer.toByteArray();
            ContainerEntity container = new ContainerEntity(nextId, data);
            containerRepository.save(container);
            
            // Update chunk metadata
            int offset = 0;
            for (ChunkEntity chunk : containerChunks) {
                chunk.setContainerId(nextId);
                chunk.setOffset(offset);
                offset += chunk.getSize();
                chunkRepository.save(chunk);
            }
            
            containerBuffer.reset();
            containerChunks.clear();
        }
    }

    private List<byte[]> rabinChunking(byte[] data, int minChunk, int avgChunk, int maxChunk) {
        List<byte[]> chunks = new ArrayList<>();
        int windowSize = minChunk;
        long[] power = new long[windowSize];
        long q = avgChunk;

        // Precompute powers
        power[0] = 1;
        for (int i = 1; i < windowSize; i++) {
            power[i] = (power[i-1] * D) % q;
        }

        int start = 0;
        int pos = 0;
        long fingerprint = 0;

        while (pos < data.length) {
            if (pos - start < windowSize) {
                // Initial window filling (s = 0 case in formula)
                long term = ((data[pos] & 0xFF) * power[windowSize - 1 - (pos - start)]) % q;
                fingerprint = (fingerprint + term) % q;
            } else {
                // Rolling hash (s > 0 case in formula)
                long term1 = ((data[pos - windowSize] & 0xFF) * power[windowSize - 1]) % q;
                long term2 = (D * (fingerprint - term1)) % q;
                fingerprint = (term2 + (data[pos] & 0xFF)) % q;
                // Ensure proper modulo for negative numbers
                if (fingerprint < 0) {
                    fingerprint = ((fingerprint % q) + q) % q;
                }
            }

            // Check if we found a chunk boundary
            if ((pos - start + 1) >= minChunk && 
                ((pos - start + 1) >= maxChunk || (fingerprint & (avgChunk - 1)) == 0)) {
                // This gives the chunk range of [start...pos]
                byte[] chunk = Arrays.copyOfRange(data, start, pos + 1);
                chunks.add(chunk);
                start = pos + 1;
                fingerprint = 0;
            }
            pos++;
        }

        // Add remaining bytes as final chunk
        if (start < data.length) {
            chunks.add(Arrays.copyOfRange(data, start, data.length));
        }

        return chunks;
    }

    private static String calculateMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}