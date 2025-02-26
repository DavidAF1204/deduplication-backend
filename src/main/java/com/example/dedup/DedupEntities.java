package com.example.dedup;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entity representing a file in the deduplication system
 */
@Entity
@Table(name = "files")
@Data
@NoArgsConstructor
public class FileEntity {
    @Id
    private String filePath;
    
    private long size;
    
    @ElementCollection
    @CollectionTable(name = "file_chunks", joinColumns = @JoinColumn(name = "file_path"))
    @Column(name = "chunk_fingerprint")
    private List<String> chunkFingerprints = new ArrayList<>();
}

/**
 * Entity representing a unique chunk in the deduplication system
 */
@Entity
@Table(name = "chunks")
@Data
@NoArgsConstructor
public class ChunkEntity {
    @Id
    private String fingerprint;
    
    private int containerId;
    private int offset;
    private int size;
    
    @ElementCollection
    @CollectionTable(name = "chunk_files", joinColumns = @JoinColumn(name = "chunk_fingerprint"))
    @Column(name = "file_path")
    private Set<String> files = new HashSet<>();
    
    public ChunkEntity(String fingerprint, int size) {
        this.fingerprint = fingerprint;
        this.size = size;
    }
}

/**
 * Entity representing a container that stores multiple chunks
 */
@Entity
@Table(name = "containers")
@Data
@NoArgsConstructor
public class ContainerEntity {
    @Id
    private Integer id;
    
    @Lob
    @Column(columnDefinition = "bytea")
    private byte[] data;
    
    public ContainerEntity(Integer id, byte[] data) {
        this.id = id;
        this.data = data;
    }
}