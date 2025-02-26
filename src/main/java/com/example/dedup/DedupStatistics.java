package com.example.dedup;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DedupStatistics {
    private long totalFiles;
    private long totalChunks;
    private long uniqueChunks;
    private long totalBytes;
    private long uniqueBytes;
    private long totalContainers;
    private double deduplicationRatio;
}