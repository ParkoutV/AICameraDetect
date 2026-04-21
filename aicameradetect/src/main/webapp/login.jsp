<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.sql.*, com.aicamera.util.DBUtil" %>
<%
    // 1. 인코딩 및 파라미터 수신
    request.setCharacterEncoding("UTF-8");
    
    String userId = request.getParameter("userId");
    String userPw = request.getParameter("userPw");
    String accessType = request.getParameter("accessType");
    
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
        pstmt.setString(2, userPw);
        
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