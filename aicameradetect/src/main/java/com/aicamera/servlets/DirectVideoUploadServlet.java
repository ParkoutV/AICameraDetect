package com.aicamera.servlets;

import com.aicamera.util.ConfigUtil;
import com.aicamera.util.DBUtil;
import com.aicamera.util.GoogleDriveUtil;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

@WebServlet("/uploadDirectVideo")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 10,  // 메모리 임계치 10MB
    maxFileSize = 1024 * 1024 * 500,       // 단일 파일 최대 500MB 허용
    maxRequestSize = 1024 * 1024 * 1000    // 요청 전체 최대 1000MB 허용
)
public class DirectVideoUploadServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession();
        String userId = (String) session.getAttribute("userId");

        if (userId == null || !"root".equals(userId)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다.");
            return;
        }

        Part filePart = req.getPart("videoFile");
        if (filePart == null || filePart.getSize() == 0) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "업로드된 파일이 없습니다.");
            return;
        }

        // 파일 저장 경로 설정 및 폴더 생성
        String finalVideoPath = ConfigUtil.getFinalVideoPath();
        new File(finalVideoPath).mkdirs();

        // 원본 파일명에서 확장자 추출
        String originalFileName = filePart.getSubmittedFileName();
        String extension = ".mp4"; // 기본 확장자
        if (originalFileName != null && originalFileName.lastIndexOf('.') > 0) {
            extension = originalFileName.substring(originalFileName.lastIndexOf('.'));
        }

        // 파일 이름 충돌 방지를 위해 UUID 생성
        String finalVideoName = UUID.randomUUID().toString() + extension;
        String absoluteFinalPath = new File(finalVideoPath, finalVideoName).getAbsolutePath();

        // 1. 영상 파일 디스크에 직접 저장
        filePart.write(absoluteFinalPath);
        
        // 현재 시간을 영상 시작 및 업로드 시간으로 처리
        Timestamp startTime = new Timestamp(System.currentTimeMillis());

        try (Connection conn = DBUtil.getConnection()) {
            // 2. DB에 영상 메타데이터 등록 (분석중 상태)
            String insertSql = "INSERT INTO main_videos (user_id, video_file_name, start_time, analysis_status) VALUES (?, ?, ?, '분석중')";
            try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                insertPstmt.setString(1, userId);
                insertPstmt.setString(2, finalVideoName);
                insertPstmt.setTimestamp(3, startTime);
                insertPstmt.executeUpdate();
            }

            // 3. 구글 드라이브 비동기 업로드 (이름 규칙: yyyy-MM-dd_HH-mm-ss_UUID.mp4)
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String driveFileName = sdf.format(startTime) + "_" + finalVideoName;
            GoogleDriveUtil.uploadVideoAsync(absoluteFinalPath, driveFileName);

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 처리 완료 후 blackbox.jsp로 성공 메시지와 함께 돌아감
        resp.sendRedirect("blackbox.jsp?uploadSuccess=true");
    }
}