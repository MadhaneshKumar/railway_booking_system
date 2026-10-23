package com.railway.service;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;

@Service
public class BlobStorageService {
    private final BlobContainerClient container;
    public BlobStorageService(BlobContainerClient container) { this.container = container; }

    public void upload(String blobName, byte[] data) {
        container.getBlobClient(blobName).upload(BinaryData.fromBytes(data), true);
    }

    /** Azure's equivalent of a presigned URL: read-only SAS valid for 15 minutes. */
    public String sasUrl(String blobName) {
        BlobClient blob = container.getBlobClient(blobName);
        BlobServiceSasSignatureValues v = new BlobServiceSasSignatureValues(
                OffsetDateTime.now().plusMinutes(15), new BlobSasPermission().setReadPermission(true));
        return blob.getBlobUrl() + "?" + blob.generateSas(v);
    }
}
