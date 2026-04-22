<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.sql.*, com.aicamera.util.DBUtil, java.security.MessageDigest" %>
<%!
    // SHA-256 해시 함수 선언
    public String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("비밀번호 해시 중 오류 발생", e);
        }
    }
%>
<%
    // 1. 인코딩 및 파라미터 수신
    request.setCharacterEncoding("UTF-8");
    
    String userId = request.getParameter("userId");
    String userPw = request.getParameter("userPw");
    String accessType = request.getParameter("accessType");
    
    // 사용자가 입력한 평문 비밀번호를 DB와 동일한 방식으로 해시화
    String hashedPw = hashPassword(userPw);

    // 2. MySQL DB 연결 및 인증 로직
    boolean isLoginSuccess = false; 
    
    Connection conn = null;
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    
    try {
        conn = DBUtil.getConnection();
        
        String sql = "SELECT * FROM users WHERE user_id = ? AND user_pw = ?";
        pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, userId);
        pstmt.setString(2, hashedPw); // 해시화된 비밀번호로 비교
        
        rs = pstmt.executeQuery();
        if (rs.next()) {
            isLoginSuccess = true; // 일치하는 데이터가 있으면 로그인 성공
        }
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        // 자원 해제
        if (rs != null) try { rs.close(); } catch(SQLException ex) {}
        if (pstmt != null) try { pstmt.close(); } catch(SQLException ex) {}
        if (conn != null) try { conn.close(); } catch(SQLException ex) {}
    }
    
    if (isLoginSuccess) {
        // 3. 접속 유형(accessType)에 따른 페이지 리다이렉트 분기
        // 로그인 성공 시 세션에 사용자 ID 저장
        session.setAttribute("userId", userId);

        if ("blackbox".equals(accessType)) {
            response.sendRedirect("blackbox.jsp");
        } else if ("video".equals(accessType)) {
            response.sendRedirect("video.jsp");
        } else {
            response.sendRedirect("index.jsp");
        }
    } else {
        response.sendRedirect("index.jsp?error=true");
    }
%>