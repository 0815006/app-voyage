package com.voyage.service;

import com.voyage.entity.ResourceCheckResponse;

import java.util.List;

public interface ResourceService {

    ResourceCheckResponse getResourceCheckByProduct(String productId, String batchNo, String fileSource);

    int deleteByOriginalFileName(String productId, String batchNo, String originalFileName);

    List<String> getAllProductIds();
}
