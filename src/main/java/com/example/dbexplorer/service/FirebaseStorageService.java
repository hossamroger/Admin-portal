package com.example.dbexplorer.service;

import com.example.dbexplorer.config.AppProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Uploads files to Google Firebase Cloud Storage.
 *
 * Credentials and bucket name are read exclusively from environment variables at runtime —
 * nothing is embedded in the codebase or configuration files:
 *
 *   GOOGLE_APPLICATION_CREDENTIALS  →  absolute path to the service-account JSON file
 *   FIREBASE_STORAGE_BUCKET         →  bucket name, e.g. my-project.appspot.com
 *
 * The Firebase SDK is initialized lazily on the first upload request and reused thereafter.
 */
@Service
public class FirebaseStorageService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseStorageService.class);

    private final AppProperties props;

    /** Guard for lazy one-time initialization. */
    private volatile Storage storage;
    private volatile String bucketName;

    public FirebaseStorageService(AppProperties props) {
        this.props = props;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public static final class UploadResult {
        public final String publicUrl;
        public final String folder;
        public final String savedName;
        public final boolean folderCreated;

        public UploadResult(String publicUrl, String folder, String savedName, boolean folderCreated) {
            this.publicUrl = publicUrl;
            this.folder = folder;
            this.savedName = savedName;
            this.folderCreated = folderCreated;
        }
    }

    /**
     * Upload {@code file} into {@code folder/finalName} inside the configured bucket.
     *
     * <ul>
     *   <li>If {@code folder} already has objects under its prefix, it is reused.</li>
     *   <li>If the folder is new, it is created implicitly (GCS has no real directories).</li>
     *   <li>The uploaded object is made publicly readable; the permanent GCS URL is returned.</li>
     * </ul>
     *
     * @param file         the multipart upload from the browser
     * @param folder       destination folder / path segment (e.g. "invoices")
     * @param documentName desired file name — if the caller omits the extension the original
     *                     file's extension is appended automatically
     */
    public UploadResult upload(MultipartFile file, String folder, String documentName) throws IOException {
        ensureInitialized();

        String resolvedFolder = sanitizePath(folder);
        String resolvedName   = resolveName(documentName, file.getOriginalFilename());
        String objectPath     = resolvedFolder.isEmpty()
                ? resolvedName
                : resolvedFolder + "/" + resolvedName;

        boolean folderCreated = !folderExists(resolvedFolder);

        String contentType = file.getContentType() != null
                ? file.getContentType()
                : "application/octet-stream";

        BlobId   blobId   = BlobId.of(bucketName, objectPath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build();

        try (InputStream in = file.getInputStream()) {
            Blob blob = storage.create(blobInfo, in);
            blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
        }

        String publicUrl = buildPublicUrl(bucketName, objectPath);
        log.info("Uploaded '{}' to gs://{}/{} — public URL generated", resolvedName, bucketName, objectPath);
        return new UploadResult(publicUrl, resolvedFolder, resolvedName, folderCreated);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Thread-safe lazy initialization of the Firebase SDK and GCS Storage client. */
    private synchronized void ensureInitialized() throws IOException {
        if (storage != null) return;

        String credPath = props.getFirebase().getCredentialsPath();
        String bucket   = props.getFirebase().getStorageBucket();

        if (credPath == null || credPath.trim().isEmpty()) {
            throw new IllegalStateException(
                "Firebase credentials path is not configured. " +
                "Set the GOOGLE_APPLICATION_CREDENTIALS environment variable to the absolute " +
                "path of your service-account JSON file before starting the application.");
        }
        if (bucket == null || bucket.trim().isEmpty()) {
            throw new IllegalStateException(
                "Firebase Storage bucket is not configured. " +
                "Set the FIREBASE_STORAGE_BUCKET environment variable (e.g. my-project.appspot.com) " +
                "before starting the application.");
        }

        GoogleCredentials credentials;
        try (FileInputStream stream = new FileInputStream(credPath.trim())) {
            credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
        }

        // Initialize the Firebase app only once (guard against re-init if the bean were re-created).
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setStorageBucket(bucket.trim())
                    .build();
            FirebaseApp.initializeApp(options);
        }

        this.storage    = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
        this.bucketName = bucket.trim();
        log.info("Firebase Storage initialized — bucket: {}", this.bucketName);
    }

    /** Returns true when at least one object exists under {@code folder/} prefix. */
    private boolean folderExists(String folder) {
        if (folder.isEmpty()) return true;
        String prefix = folder + "/";
        Storage.BlobListOption[] opts = {
            Storage.BlobListOption.prefix(prefix),
            Storage.BlobListOption.pageSize(1)
        };
        return storage.list(bucketName, opts).iterateAll().iterator().hasNext();
    }

    /**
     * Resolves the final file name:
     * — if documentName is blank → use the original filename
     * — if documentName has no extension → append the extension from the original filename
     * — otherwise use documentName as-is
     */
    static String resolveName(String documentName, String originalFilename) {
        String orig = (originalFilename != null && !originalFilename.isEmpty())
                ? originalFilename : "upload";
        if (documentName == null || documentName.trim().isEmpty()) return orig;
        String name = documentName.trim();
        if (!name.contains(".")) {
            String ext = extensionOf(orig);
            if (!ext.isEmpty()) name = name + "." + ext;
        }
        return name;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot + 1) : "";
    }

    /** Strip leading/trailing slashes and whitespace from a folder path. */
    static String sanitizePath(String folder) {
        if (folder == null) return "";
        return folder.trim().replaceAll("^/+|/+$", "").trim();
    }

    /** Permanent public GCS URL — works after makePublic() / Acl READER grant. */
    static String buildPublicUrl(String bucket, String objectPath) {
        try {
            // Encode each path segment individually so slashes in folder/name are preserved.
            // URLEncoder uses form-encoding (space -> '+'), but GCS object paths require
            // percent-encoding (space -> '%20'), so we fix '+' afterwards.
            String[] segments = objectPath.split("/");
            StringBuilder encoded = new StringBuilder();
            for (String seg : segments) {
                if (encoded.length() > 0) encoded.append('/');
                String s = URLEncoder.encode(seg, StandardCharsets.UTF_8.name())
                        .replace("+", "%20");
                encoded.append(s);
            }
            return "https://storage.googleapis.com/" + bucket + "/" + encoded;
        } catch (Exception e) {
            return "https://storage.googleapis.com/" + bucket + "/" + objectPath;
        }
    }
}
