# Deduplication-Backend

A deduplication application that leverages PostgreSQL for persistence and utilizes Rabin fingerprinting to efficiently deduplicate files. Built with Spring Boot, JPA, and Lombok. It provides REST endpoints to upload, download, and delete files while minimizing storage redundancy.

## Features
**File Upload with Deduplication**  
- Splits files into chunks using Rabin chunking
- Computes MD5 checksums to identify unique chunks
- Avoids storing duplicate chunks

**File Download**  
- Reassembles files from deduplicated chunks stored in PostgreSQL

**File Deletion**  
- Removes file references and cleans up unreferenced chunks and containers

## Prerequisites
- Java 11 or later
- PostgreSQL database installed and running
- Maven

## Setup Guide

### Database Setup
Create a PostgreSQL database named `dedup`
```sql
-- Option 1: Using psql command line
psql -U postgres -c "CREATE DATABASE dedup;"

-- Option 2: Within psql console
CREATE DATABASE dedup;
```

Adjust database connection settings if necessary in `src/main/resources/application.properties`

### Build and Run
```bash
# Build
mvn clean package

# Option 1: Run with Maven
mvn spring-boot:run

# Option 2: Run the packaged JAR
java -jar target/dedup-0.0.1-SNAPSHOT.jar
```

## API Endpoints
**Upload File**  
- **URL:** `/upload`  
- **Method:** `POST`  
- **Parameters (Multipart Form):**
  - `file`: File to upload
  - `minChunk`: Minimum chunk size
  - `avgChunk`: Average chunk size
  - `maxChunk`: Maximum chunk size

**Download File**  
- **URL:** `/download`  
- **Method:** `GET`  
- **Parameter:** `filePath` — the name of file to download

**Delete File**  
- **URL:** `/delete`  
- **Method:** `DELETE`  
- **Parameter:** `filePath` — the name of file to delete

**Statistics**
- **URL:** `/stats`
- **Method:** `GET`