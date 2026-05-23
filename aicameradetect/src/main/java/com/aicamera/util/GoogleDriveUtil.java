package com.aicamera.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleDriveUtil {
    private static Drive driveService;

    // 구글 드라이브 업로드 전용 스레드 풀 생성 (최대 3개 동시 업로드, 나머지는 대기열 처리)
    private static final ExecutorService uploadExecutor = Executors.newFixedThreadPool(3);

    // 여러 스레드가 동시에 접근하여 driveService를 중복 생성하는 것을 방지하기 위해 synchronized 키워드 추가
    public static synchronized Drive getDriveService() throws Exception {
        if (driveService == null) {
            // db.properties에서 OAuth 2.0 인증 정보 로드
            String clientId = ConfigUtil.getProperty("gdrive.client.id", "");
            String clientSecret = ConfigUtil.getProperty("gdrive.client.secret", "");
            String refreshToken = ConfigUtil.getProperty("gdrive.refresh.token", "");

            if (clientId.isEmpty() || clientSecret.isEmpty() || refreshToken.isEmpty()) {
                throw new IllegalArgumentException("OAuth 2.0 인증 정보(gdrive.client.id, secret, refresh.token)가 db.properties에 없습니다.");
            }

            UserCredentials credentials = UserCredentials.newBuilder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setRefreshToken(refreshToken)
                    .build();

            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("AI Camera Detect")
                    .build();
        }
        return driveService;
    }

    public static void uploadVideoAsync(String filePath, String fileName) {
        // 웹 응답 지연(Blocking) 방지를 위한 비동기 처리
        // 기본 스레드 풀 고갈 방지를 위해 생성해둔 uploadExecutor(전용 풀)를 사용하도록 지정
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("[GoogleDriveUtil] Google Drive 비동기 업로드 시작: " + fileName);
                Drive drive = getDriveService();
                String folderId = ConfigUtil.getProperty("gdrive.final_folder.id", "");

                if (folderId.isEmpty()) {
                    System.err.println("[GoogleDriveUtil] 오류: gdrive.final_folder.id 설정이 없습니다. 업로드를 취소합니다.");
                    return;
                }

                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                fileMetadata.setName(fileName);
                fileMetadata.setParents(Collections.singletonList(folderId)); // 대상 폴더 지정

                File uploadFile = new File(filePath);

                // 80 Mbps (초당 약 10MB) 속도 제한 설정
                long maxBytesPerSec = 10_000_000L;

                try (InputStream fileIn = new FileInputStream(uploadFile);
                     InputStream throttledIn = new ThrottledInputStream(fileIn, maxBytesPerSec)) {
                    
                    InputStreamContent mediaContent = new InputStreamContent("video/mp4", throttledIn);
                    mediaContent.setLength(uploadFile.length()); // 진행률 관리 및 API 최적화를 위해 파일 크기 명시

                    com.google.api.services.drive.model.File file = drive.files().create(fileMetadata, mediaContent)
                            .setFields("id")
                            .execute();
                    System.out.println("[GoogleDriveUtil] Google Drive 업로드 완료. File ID: " + file.getId());
                }
            } catch (Exception e) {
                System.err.println("[GoogleDriveUtil] Google Drive 업로드 중 오류 발생: " + fileName);
                e.printStackTrace();
            }
        }, uploadExecutor);
    }

    public static List<com.google.api.services.drive.model.File> findFilesByExtension(String folderId, String extension) throws Exception {
        Drive drive = getDriveService();
        String query = "'" + folderId + "' in parents and name contains '" + extension + "' and trashed = false";
        return drive.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .execute()
                .getFiles();
    }

    public static com.google.api.services.drive.model.File findFileByName(String folderId, String fileName) throws Exception {
        Drive drive = getDriveService();
        String query = "'" + folderId + "' in parents and name = '" + fileName + "' and trashed = false";
        List<com.google.api.services.drive.model.File> files = drive.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .execute()
                .getFiles();
        if (files != null && !files.isEmpty()) {
            return files.get(0); // 가장 첫 번째 일치하는 파일 반환
        }
        return null;
    }

    public static InputStream downloadFileAsStream(String fileId) throws Exception {
        Drive drive = getDriveService();
        return drive.files().get(fileId).executeMediaAsInputStream();
    }

    public static void downloadFile(String fileId, File destination) throws Exception {
        Drive drive = getDriveService();
        try (OutputStream outputStream = new FileOutputStream(destination)) {
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream);
        }
    }

    public static void deleteFile(String fileId) throws Exception {
        Drive drive = getDriveService();
        drive.files().delete(fileId).execute();
    }

    /**
     * 업로드 속도 제한(Throttling)을 위한 커스텀 InputStream
     */
    private static class ThrottledInputStream extends InputStream {
        private final InputStream in;
        private final long maxBytesPerSec;
        private long bytesRead = 0;
        private final long startTime = System.currentTimeMillis();

        public ThrottledInputStream(InputStream in, long maxBytesPerSec) {
            this.in = in;
            this.maxBytesPerSec = maxBytesPerSec;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b != -1) throttle(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = in.read(b, off, len);
            if (read > 0) throttle(read);
            return read;
        }

        private void throttle(long bytes) throws IOException {
            bytesRead += bytes;
            long elapsed = System.currentTimeMillis() - startTime;
            long expectedTime = (bytesRead * 1000L) / maxBytesPerSec;
            if (elapsed < expectedTime) {
                try {
                    Thread.sleep(expectedTime - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("업로드 속도 조절 중 스레드 인터럽트 발생", e);
                }
            }
        }

        @Override
        public void close() throws IOException {
            in.close();
        }

        @Override
        public int available() throws IOException {
            return in.available();
        }
    }
}