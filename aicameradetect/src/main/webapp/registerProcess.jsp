<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.sql.*, com.aicamera.util.DBUtil" %>
<%
    // 1. 인코딩 및 파라미터 수신
    request.setCharacterEncoding("UTF-8");
    
    String newId = request.getParameter("newId");
    String newPw = request.getParameter("newPw");
    String newPwConfirm = request.getParameter("newPwConfirm");
    String email = request.getParameter("email");
    
    // 2. 비밀번호 확인
    if (newPw == null || !newPw.equals(newPwConfirm)) {
        // 비밀번호가 서로 다르면 다시 가입 페이지로 돌려보냄
        response.sendRedirect("register.jsp?error=pwMismatch");
        return;
    }
    
    Connection conn = null;
    PreparedStatement pstmt = null;
    boolean isSuccess = false;
    
    try {
        conn = DBUtil.getConnection();
        
        String sql = "INSERT INTO users (user_id, user_pw, email) VALUES (?, ?, ?)";
        pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, newId);
        pstmt.setString(2, newPw);
        pstmt.setString(3, email);
        
        int result = pstmt.executeUpdate();
        if (result > 0) {
            isSuccess = true;
        }
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        if (pstmt != null) try { pstmt.close(); } catch(SQLException ex) {}
        if (conn != null) try { conn.close(); } catch(SQLException ex) {}
    }
    
    // 4. 결과에 따른 페이지 이동
    if (isSuccess) {
        response.sendRedirect("index.jsp?register=success"); // 가입 완료 시 로그인 화면으로
    } else {
        response.sendRedirect("register.jsp?error=fail"); // 가입 실패 시 회원가입 화면으로
    }
%>
