package com.aicamera.servlets;

import com.aicamera.util.DBUtil;
import com.aicamera.util.PasswordUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String userId = req.getParameter("newId");
        String password = req.getParameter("newPw");
        String passwordConfirm = req.getParameter("newPwConfirm");
        String email = req.getParameter("email");

        // 1. 서버 측 유효성 검사
        if (userId == null || userId.trim().isEmpty() || password == null || password.isEmpty()) {
            resp.sendRedirect("register.jsp?error=missing");
            return;
        }
        if (!password.equals(passwordConfirm)) {
            resp.sendRedirect("register.jsp?error=pw_mismatch");
            return;
        }

        // 2. 비밀번호를 BCrypt로 해싱
        String hashedPassword = PasswordUtil.hashPassword(password);

        // 3. 데이터베이스에 사용자 정보 저장
        String sql = "INSERT INTO users (user_id, user_pw, email) VALUES (?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, userId);
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, email);
            pstmt.executeUpdate();

            resp.sendRedirect("index.jsp?success=true"); // 회원가입 성공
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // MySQL 'Duplicate entry' 에러 코드
                resp.sendRedirect("register.jsp?error=id_exists");
            } else {
                resp.sendRedirect("register.jsp?error=db_error");
            }
        } catch (Exception e) {
            resp.sendRedirect("register.jsp?error=server_error");
        }
    }
}