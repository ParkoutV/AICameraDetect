package com.aicamera.servlets;

import com.aicamera.util.DBUtil;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

@WebServlet("/uploadSegment")
@MultipartConfig
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

        // 세션에서 세그먼트 카운터 가져오기 및 증가
        Integer counter = (Integer) session.getAttribute("segmentCounter");
        if (counter == null) {
            counter = 1;
        } else {
            counter++;
        }
        session.setAttribute("segmentCounter", counter);

        // 파일 저장
        Part filePart = req.getPart("video");
        String fileName = userId + "-" + counter + ".webm";
        String uploadPath = getServletContext().getRealPath("") + File.separator + "uploads" + File.separator + "temp_videos";
        
        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        filePart.write(uploadPath + File.separator + fileName);

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