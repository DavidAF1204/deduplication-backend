package com.example.dedup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for file operations
 */
@Repository
public interface FileRepository extends JpaRepository<FileEntity, String> {
    @Query("SELECT COUNT(f) FROM FileEntity f")
    long countFiles();
    
    @Query("SELECT SUM(f.size) FROM FileEntity f")
    long sumFileSize();
}

/**
 * Repository for chunk operations
 */
@Repository
public interface ChunkRepository extends JpaRepository<ChunkEntity, String> {
    @Query("SELECT COUNT(c) FROM ChunkEntity c")
    long countChunks();
    
    @Query("SELECT SUM(c.size) FROM ChunkEntity c")
    long sumChunkSize();
    
    List<ChunkEntity> findByContainerId(int containerId);
}

/**
 * Repository for container operations
 */
@Repository
public interface ContainerRepository extends JpaRepository<ContainerEntity, Integer> {
    @Query("SELECT MAX(c.id) FROM ContainerEntity c")
    Integer findMaxContainerId();
}