package com.aicamera.servlets;

import com.aicamera.util.DBUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

@WebServlet("/uploadSegment")
@MultipartConfig(maxFileSize = 1024 * 1024 * 50, maxRequestSize = 1024 * 1024 * 50) // 최대 50MB 파일 허용
public class UploadServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String userId = (String) session.getAttribute("userId");

        if (userId == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"로그인이 필요합니다.\"}");
            return;
        }

        // 클라이언트가 전송한 고유 녹화 ID와 순번을 사용 (세션 의존성 제거)
        String recordingId = req.getParameter("recordingId");
        String counterStr = req.getParameter("segmentCounter");
        
        if (recordingId == null || counterStr == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"recordingId와 segmentCounter 파라미터가 필요합니다.\"}");
            return;
        }
        
        int counter;
        try {
            counter = Integer.parseInt(counterStr);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"유효하지 않은 segmentCounter입니다.\"}");
            return;
        }

        // 파일 저장
        Part filePart = req.getPart("video");
        String fileName = userId + "_" + recordingId + "_" + counter + ".webm";
        String uploadPath = "C:\\Users\\kghbs\\aicamera_uploads\\temp_videos";
        
        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 파일 쓰기 오류를 방지하기 위해 Files.copy 사용 및 예외 처리 추가
        try (InputStream in = filePart.getInputStream()) {
            Files.copy(in, new File(uploadDir, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"파일 물리 저장 실패: " + e.getMessage() + "\"}");
            return;
        }

        // 임시 DB에 정보 저장
        try (Connection conn = DBUtil.getConnection(getServletContext())) {
            String sql = "INSERT INTO temp_videos (user_id, segment_filename) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, userId);
                pstmt.setString(2, fileName);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"DB 저장 실패: " + e.getMessage() + "\"}");
            return;
        }

        // 성공 응답
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write("{\"status\":\"success\", \"fileName\":\"" + fileName + "\"}");
    }
}