package com.aicamera.servlets;

import com.aicamera.util.DBUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/stopRecording")
public class StopRecordingServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String userId = (String) session.getAttribute("userId");

        if (userId == null) return;

        // 세션 정리
        session.removeAttribute("segmentCounter");

        String tempVideoPath = getServletContext().getRealPath("/uploads/temp_videos");
        String finalVideoPath = getServletContext().getRealPath("/uploads/final_videos");
        new File(finalVideoPath).mkdirs();

        List<String> segmentFiles = new ArrayList<>();
        Timestamp firstSegmentTime = null;

        try (Connection conn = DBUtil.getConnection(getServletContext())) {
            // 1. 병합할 파일 목록 임시 DB에서 가져오기
            String selectSql = "SELECT segment_filename, created_at FROM temp_videos WHERE user_id = ? ORDER BY segment_id ASC";
            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, userId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    segmentFiles.add(rs.getString("segment_filename"));
                    if (firstSegmentTime == null) {
                        firstSegmentTime = rs.getTimestamp("created_at");
                    }
                }
            }

            if (segmentFiles.isEmpty()) return;

            // 2. FFmpeg 실행 로직
            // FFmpeg를 위한 파일 리스트(mylist.txt) 생성
            File listFile = new File(tempVideoPath, userId + "_mylist.txt");
            try (PrintWriter writer = new PrintWriter(listFile)) {
                for (String fileName : segmentFiles) {
                    writer.println("file '" + fileName + "'");
                }
            }

            // 최종 파일 이름 랜덤 생성 (중복 방지 로직은 생략, UUID로 충분히 대체 가능)
            String finalVideoName = UUID.randomUUID().toString() + ".mp4";

            /*
             * [중요] 아래 FFmpeg 실행 코드는 서버에 FFmpeg가 설치되어 있고,
             * 실행 경로가 시스템 PATH에 잡혀있어야 동작합니다.
             * 실제 실행은 ProcessBuilder를 사용합니다.
             */
            System.out.println("FFmpeg 병합을 시작합니다: " + userId);
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-f", "concat",
                "-safe", "0",
                "-i", listFile.getAbsolutePath(),
                "-c", "copy",
                new File(finalVideoPath, finalVideoName).getAbsolutePath()
            );
            pb.directory(new File(tempVideoPath)); // 작업 디렉토리 설정
            Process process = pb.start();
            
            // FFmpeg 실행 로그 확인 (디버깅용)
            new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().forEach(System.out::println);

            int exitCode = process.waitFor(); // FFmpeg 작업이 끝날 때까지 대기

            if (exitCode == 0) { // FFmpeg 병합 성공
                System.out.println("병합 성공: " + finalVideoName);
                // 3. 메인 DB에 최종 영상 정보 저장
                String insertSql = "INSERT INTO main_videos (user_id, video_file_name, start_time, analysis_status) VALUES (?, ?, ?, '분석중')";
                try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                    insertPstmt.setString(1, userId);
                    insertPstmt.setString(2, finalVideoName);
                    insertPstmt.setTimestamp(3, firstSegmentTime);
                    insertPstmt.executeUpdate();
                }

                // 4. 임시 DB 및 파일 삭제
                String deleteSql = "DELETE FROM temp_videos WHERE user_id = ?";
                try (PreparedStatement deletePstmt = conn.prepareStatement(deleteSql)) {
                    deletePstmt.setString(1, userId);
                    deletePstmt.executeUpdate();
                }
                for (String fileName : segmentFiles) {
                    new File(tempVideoPath, fileName).delete();
                }
                listFile.delete();

            } else {
                System.err.println("FFmpeg 병합 실패. Exit code: " + exitCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}