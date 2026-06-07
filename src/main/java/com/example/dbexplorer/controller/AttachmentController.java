package com.example.dbexplorer.controller;

import com.example.dbexplorer.service.AuditService;
import com.example.dbexplorer.service.AuthService;
import com.example.dbexplorer.service.FirebaseStorageService;
import com.example.dbexplorer.service.FirebaseStorageService.UploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for Firebase Cloud Storage file uploads.
 *
 * POST /api/attachments/upload
 *   — multipart/form-data: file, folder, documentName
 *   — returns the permanent public GCS URL plus upload metadata
 */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private static final Logger log = LoggerFactory.getLogger(AttachmentController.class);

    private static final long MAX_FILE_BYTES = 50 * 1024 * 1024; // 50 MB guard

    private final FirebaseStorageService firebaseStorage;
    private final AuthService auth;

    public AttachmentController(FirebaseStorageService firebaseStorage, AuthService auth) {
        this.firebaseStorage = firebaseStorage;
        this.auth = auth;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file")         MultipartFile file,
            @RequestParam("folder")       String folder,
            @RequestParam("documentName") String documentName,
            HttpServletRequest http) {

        auth.effectiveUser(http); // authentication enforced by AuthFilter; this surfaced for auditing

        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("No file was provided.");

        if (file.getSize() > MAX_FILE_BYTES)
            throw new IllegalArgumentException(
                "File exceeds the 50 MB upload limit (" + (file.getSize() / (1024 * 1024)) + " MB).");

        if (folder == null || folder.trim().isEmpty())
            throw new IllegalArgumentException("Folder name is required.");

        UploadResult result;
        try {
            result = firebaseStorage.upload(file, folder.trim(), documentName);
        } catch (IllegalStateException e) {
            // Configuration not ready — surface a clear 503 so the user knows what to fix.
            throw e;
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("Upload failed: " + e.getMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url",           result.publicUrl);
        body.put("folder",        result.folder);
        body.put("documentName",  result.savedName);
        body.put("folderCreated", result.folderCreated);
        body.put("originalName",  file.getOriginalFilename());
        body.put("sizeBytes",     file.getSize());

        http.setAttribute("auditDetail",
            "folder=" + result.folder + " name=" + result.savedName);

        return ResponseEntity.ok(body);
    }
}
