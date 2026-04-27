package com.aicamera.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.http.FileContent;

import java.io.FileInputStream;
import java.io.File;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class GoogleDriveUtil {
    private static Drive driveService;

    public static Drive getDriveService() throws Exception {
        if (driveService == null) {
            // db.properties에서 서비스 계정 JSON 키 파일 경로 로드
            String credentialsFilePath = ConfigUtil.getProperty("gdrive.credentials.path", "");
            if (credentialsFilePath.isEmpty()) {
                throw new IllegalArgumentException("gdrive.credentials.path 설정이 db.properties에 없습니다.");
            }

            GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsFilePath))
                    .createScoped(Collections.singleton(DriveScopes.DRIVE_FILE));

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
        });
    }
}