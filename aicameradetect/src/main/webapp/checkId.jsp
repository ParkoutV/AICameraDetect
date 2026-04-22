<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.sql.*, com.aicamera.util.DBUtil" %>
<%
    request.setCharacterEncoding("UTF-8");
    String id = request.getParameter("id");
    boolean exists = false;
    
    if (id != null && !id.trim().isEmpty()) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT 1 FROM users WHERE user_id = ?")) {
            pstmt.setString(1, id.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    exists = true; // 아이디가 이미 존재함
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    out.print("{\"exists\": " + exists + "}");
%>