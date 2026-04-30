package com.aicamera.servlets;

import com.aicamera.util.DBUtil;
import com.aicamera.util.ConfigUtil;
import com.aicamera.util.GoogleDriveUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/stopRecording")
@MultipartConfig // 클라이언트에서 FormData를 beacon으로 보낼 때, 이를 파싱하기 위해 필요합니다.
public class StopRecordingServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String userId = (String) session.getAttribute("userId");

        if (userId == null) return;

        // 세션 대신 클라이언트에서 전달받은 recordingId를 사용합니다.
        String recordingId = req.getParameter("recordingId");
        if (recordingId == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        // 1번(조각 업로드 완료) 직후, 브라우저에게 성공 응답(200 OK)을 보내어 불필요한 대기와 통신을 즉시 종료시킵니다.
        resp.setStatus(HttpServletResponse.SC_OK);

        // 2번(병합)과 3번(드라이브 업로드) 과정을 백그라운드 스레드로 분리하여 브라우저 통신과 완전히 독립적으로 동작시킵니다.
        CompletableFuture.runAsync(() -> {
            processAndMergeSegments(userId, recordingId);
        });
    }

    public static void processAndMergeSegments(String userId, String recordingId) {
        System.out.println("[Watchdog/Merge] " + userId + "의 녹화 ID " + recordingId + "에 대한 병합 작업을 시작합니다.");
        String tempVideoPath = ConfigUtil.getTempVideoPath();
        String finalVideoPath = ConfigUtil.getFinalVideoPath();
        new File(finalVideoPath).mkdirs();

            List<String> segmentFiles = new ArrayList<>();
            Map<String, Timestamp> segmentTimes = new HashMap<>();

           try (Connection conn = DBUtil.getConnection()) {
                // 1. 병합할 파일 목록 임시 DB에서 가져오기
                String selectSql = "SELECT segment_filename, created_at FROM temp_videos WHERE user_id = ? AND segment_filename LIKE ? ORDER BY segment_id ASC";
            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, userId);
                pstmt.setString(2, "%" + recordingId + "%");
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String filename = rs.getString("segment_filename");
                    segmentFiles.add(filename);
                    // 각 파일 조각들의 생성 시간을 Map에 모두 저장해 둡니다.
                    segmentTimes.put(filename, rs.getTimestamp("created_at"));
                }
            }

        if (segmentFiles.isEmpty()) {
            System.out.println("[Watchdog/Merge] 처리할 세그먼트가 없습니다. 작업을 종료합니다. (ID: " + recordingId + ")");
            return;
        }

            // DB 저장 순서(segment_id)가 네트워크 지연 등으로 실제 촬영 순서와 다를 수 있으므로
            // 파일명 맨 끝에 있는 카운터(인덱스) 번호를 추출하여 오름차순으로 정확히 정렬합니다.
            segmentFiles.sort((f1, f2) -> {
                try {
                    int c1 = Integer.parseInt(f1.substring(f1.lastIndexOf('_') + 1, f1.lastIndexOf('.')));
                    int c2 = Integer.parseInt(f2.substring(f2.lastIndexOf('_') + 1, f2.lastIndexOf('.')));
                    return Integer.compare(c1, c2);
                } catch (Exception e) {
                    return f1.compareTo(f2); // 파싱 실패 시 기본 문자열 정렬로 대체
                }
            });

            // 2. 누락된 조각을 기준으로 그룹화 (예: 1~2, 4~6)
            List<List<String>> groups = new ArrayList<>();
            List<String> currentGroup = new ArrayList<>();
            int lastCounter = -1;

            for (String file : segmentFiles) {
                try {
                    int currentCounter = Integer.parseInt(file.substring(file.lastIndexOf('_') + 1, file.lastIndexOf('.')));
                    if (lastCounter != -1 && currentCounter - lastCounter > 1) {
                        groups.add(currentGroup);
                        currentGroup = new ArrayList<>();
                    }
                    currentGroup.add(file);
                    lastCounter = currentCounter;
                } catch (Exception e) {
                    currentGroup.add(file);
                }
            }
            if (!currentGroup.isEmpty()) {
                groups.add(currentGroup);
            }

            boolean allSuccess = true;

            // 3. 각 그룹별로 FFmpeg 병합 실행
            for (int i = 0; i < groups.size(); i++) {
                List<String> group = groups.get(i);
                
                // 현재 병합할 그룹의 제일 '첫 번째' 파일의 시간을 가져옵니다.
                Timestamp groupStartTime = segmentTimes.get(group.get(0));
                
                File listFile = new File(tempVideoPath, userId + "_" + recordingId + "_group" + i + "_mylist.txt");
                try (PrintWriter writer = new PrintWriter(listFile)) {
                    for (String fileName : group) {
                        writer.println("file '" + fileName + "'");
                    }
                }

                String finalVideoName = UUID.randomUUID().toString() + ".mp4";

                System.out.println("FFmpeg 병합을 시작합니다 (그룹 " + (i+1) + "/" + groups.size() + "): " + userId);
                ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-f", "concat",
                    "-safe", "0",
                    "-i", listFile.getAbsolutePath(),
                    "-c:v", "libx264",
                    "-c:a", "aac",
                    new File(finalVideoPath, finalVideoName).getAbsolutePath()
                );
                pb.directory(new File(tempVideoPath));
                Process process = pb.start();
                
                new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().forEach(System.out::println);

                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    System.out.println("병합 성공 (그룹 " + (i+1) + "): " + finalVideoName);
                    String insertSql = "INSERT INTO main_videos (user_id, video_file_name, start_time, analysis_status) VALUES (?, ?, ?, '분석중')";
                    try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                        insertPstmt.setString(1, userId);
                        insertPstmt.setString(2, finalVideoName);
                        insertPstmt.setTimestamp(3, groupStartTime);
                        insertPstmt.executeUpdate();
                    }

                    // 백그라운드 스레드를 통해 Google Drive로 영상 업로드 (기타 로직과 충돌 차단)
                    String absoluteFinalPath = new File(finalVideoPath, finalVideoName).getAbsolutePath();
                    String driveFileName = finalVideoName;
                    if (groupStartTime != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                        driveFileName = sdf.format(groupStartTime) + "_" + finalVideoName;
                    }
                    GoogleDriveUtil.uploadVideoAsync(absoluteFinalPath, driveFileName);
                } else {
                    System.err.println("FFmpeg 병합 실패 (그룹 " + (i+1) + "). Exit code: " + exitCode);
                    allSuccess = false;
                }
                listFile.delete();
            }

            // 4. 모든 병합이 성공했을 경우 임시 DB 및 파일 일괄 삭제
            if (allSuccess) {
                String deleteSql = "DELETE FROM temp_videos WHERE user_id = ? AND segment_filename LIKE ?";
                try (PreparedStatement deletePstmt = conn.prepareStatement(deleteSql)) {
                    deletePstmt.setString(1, userId);
                    deletePstmt.setString(2, "%" + recordingId + "%");
                    deletePstmt.executeUpdate();
                }
                for (String fileName : segmentFiles) {
                    new File(tempVideoPath, fileName).delete();
                }
            System.out.println("[Watchdog/Merge] 임시 파일 및 DB 데이터 정리 완료. (ID: " + recordingId + ")");
            }

        } catch (Exception e) {
        System.err.println("[Watchdog/Merge] 병합 작업 중 심각한 오류 발생 (ID: " + recordingId + ")");
            e.printStackTrace();
        }
    }
}