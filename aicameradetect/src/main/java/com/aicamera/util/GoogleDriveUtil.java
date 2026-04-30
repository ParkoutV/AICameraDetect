package com.aicamera.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import com.google.api.client.http.FileContent;

import java.io.File;
import java.util.Collections;
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
                FileContent mediaContent = new FileContent("video/mp4", uploadFile);

                com.google.api.services.drive.model.File file = drive.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute();
                System.out.println("[GoogleDriveUtil] Google Drive 업로드 완료. File ID: " + file.getId());
            } catch (Exception e) {
                System.err.println("[GoogleDriveUtil] Google Drive 업로드 중 오류 발생: " + fileName);
                e.printStackTrace();
            }
        }, uploadExecutor);
    }
}