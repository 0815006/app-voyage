package com.voyage.service;

import lombok.Data;

import java.util.Date;
import java.util.List;

public interface PerformanceDocService {
    String generateDoc(Long taskId);

    List<DocFileInfo> listDocs();

    boolean deleteDoc(String fileName);

    @Data
    class DocFileInfo {
        private String fileName;
        private long fileSize;
        private Date lastModified;
    }
}
