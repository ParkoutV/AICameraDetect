package com.aicamera.servlets;

import com.aicamera.util.DBUtil;
import com.aicamera.util.PasswordUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String userId = req.getParameter("userId");
        String userPw = req.getParameter("userPw");
        String accessType = req.getParameter("accessType");

        String dbPasswordHash = null;

        // 1. DB에서 사용자 ID로 해시된 비밀번호 조회
        String sql = "SELECT user_pw FROM users WHERE user_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    dbPasswordHash = rs.getString("user_pw");
                }
            }
        } catch (Exception e) {
            resp.sendRedirect("index.jsp?error=db_error");
            return;
        }

        // 2. BCrypt로 비밀번호 검증 및 세션 처리
        if (dbPasswordHash != null && PasswordUtil.checkPassword(userPw, dbPasswordHash)) {
            // 로그인 성공: 세션 고정 공격 방지를 위해 기존 세션 무효화 후 새 세션 생성
            HttpSession oldSession = req.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession newSession = req.getSession(true);
            newSession.setAttribute("userId", userId);

            if ("blackbox".equals(accessType)) {
                resp.sendRedirect("blackbox.jsp");
            } else {
                resp.sendRedirect("video.jsp"); // video.jsp 또는 다른 기본 페이지
            }
        } else {
            // 로그인 실패
            resp.sendRedirect("index.jsp?error=true");
        }
    }
}