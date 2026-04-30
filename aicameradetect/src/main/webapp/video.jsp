<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.sql.*" %>
<%@ page import="com.aicamera.util.DBUtil" %>
<%
    String userId = (String) session.getAttribute("userId");
    if (userId == null) {
        response.sendRedirect("index.jsp"); // 로그인이 안 되어 있으면 메인으로 튕겨냄
        return;
    }
    
    // 파라미터로 특정 파일명이 넘어오면 '재생 모드'로 간주합니다.
    String playFile = request.getParameter("play");
    // 파라미터로 원본 파일명이 넘어오면 '위반 내역 목록 모드'로 간주합니다.
    String eventsFor = request.getParameter("eventsFor");
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>실시간 영상 확인 - AI Camera Detect</title>
    <style>
        body { 
            font-family: sans-serif; 
            background-color: #f4f4f4; 
            margin: 0;
            padding: 10px; 
            box-sizing: border-box;
        }
        .container { 
            width: 100%;
            max-width: 900px; 
            margin: 0 auto; 
            background: #fff; 
            padding: 20px; 
            border-radius: 8px; 
            box-shadow: 0 0 10px rgba(0,0,0,0.1); 
            box-sizing: border-box;
        }
        h1, h2 { text-align: center; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { border: 1px solid #ddd; padding: 12px; text-align: center; }
        th { background-color: #e3f2fd; }
        .btn { display: inline-block; padding: 6px 12px; background-color: #007bff; color: white; text-decoration: none; border-radius: 4px; font-weight: bold; }
        .btn:hover { background-color: #0056b3; }
        .video-container { text-align: center; margin-top: 20px; }
        video { width: 100%; max-width: 800px; border: 2px solid #333; background: #000; border-radius: 8px; }
        .back-link { display: block; margin-top: 20px; text-align: center; font-weight: bold; color: #333; text-decoration: none; }
        .back-link:hover { text-decoration: underline; }
        .table-wrapper { overflow-x: auto; -webkit-overflow-scrolling: touch; }

        @media (max-width: 768px) {
            body { padding: 0; }
            .container { padding: 15px; border-radius: 0; box-shadow: none; }
            h1 { font-size: 1.5em; }
            h2 { font-size: 1.3em; }
            th, td { padding: 8px; font-size: 0.9em; white-space: nowrap; }
            .btn { padding: 5px 10px; font-size: 0.9em; }
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>내 블랙박스 보관함</h1>
        <p style="text-align: center;">로그인 사용자: <strong style="color:#0d47a1; font-size:1.1em;"><%= userId %></strong></p>

        <% if (playFile != null && !playFile.trim().isEmpty()) { %>
            <!-- ============================== -->
            <!-- 1. 비디오 재생 화면 (play 파라미터가 있을 때) -->
            <!-- ============================== -->
            <h2>영상 재생</h2>
            <div class="video-container">
                <!-- 브라우저 내장 비디오 플레이어 (controls 속성으로 탐색 기능 제공) -->
                <video controls autoplay>
                    <!-- 앞에서 만든 VideoServeServlet을 통해 로컬의 영상을 스트리밍으로 받아옵니다. -->
                    <source src="serveVideo?file=<%= playFile %>" type="video/mp4">
                    브라우저가 비디오 태그를 지원하지 않습니다.
                </video>
            </div>
            
            <% 
                String backTo = request.getParameter("backTo");
                if (backTo != null && !backTo.trim().isEmpty()) { 
            %>
                <a href="video.jsp?eventsFor=<%= backTo %>" class="back-link">← 위반 내역 목록으로 돌아가기</a>
            <% } else { %>
                <a href="video.jsp" class="back-link">← 전체 목록으로 돌아가기</a>
            <% } %>

        <% } else if (eventsFor != null && !eventsFor.trim().isEmpty()) { %>
            <!-- ============================== -->
            <!-- 2. 위반 내역(이벤트) 목록 화면 -->
            <!-- ============================== -->
            <h2>🚨 교통위반 분석 결과</h2>
            <p style="text-align: center;">원본 영상: <strong><%= eventsFor %></strong></p>
            <div class="table-wrapper">
                <table>
                    <thead>
                        <tr>
                            <th width="15%">순번</th>
                            <th width="50%">발생(촬영) 시간</th>
                            <th width="35%">이벤트 영상 확인</th>
                        </tr>
                    </thead>
                    <tbody>
                        <%
                            int evtCount = 1;
                            try (Connection conn = DBUtil.getConnection()) {
                                // 최대 128개의 이벤트 영상을 시간순으로 가져옵니다.
                                String evtSql = "SELECT event_video_name, event_time FROM event_videos WHERE original_video_name = ? ORDER BY event_time ASC LIMIT 128";
                                try (PreparedStatement evtPstmt = conn.prepareStatement(evtSql)) {
                                    evtPstmt.setString(1, eventsFor);
                                    try (ResultSet evtRs = evtPstmt.executeQuery()) {
                                        boolean hasEvtData = false;
                                        while (evtRs.next()) {
                                            hasEvtData = true;
                                            String evtFileName = evtRs.getString("event_video_name");
                                            Timestamp evtTime = evtRs.getTimestamp("event_time");
                        %>
                                        <tr>
                                            <td><%= evtCount++ %></td>
                                            <td><%= evtTime != null ? evtTime.toString() : "기록 없음" %></td>
                                            <td>
                                                <a href="video.jsp?play=<%= evtFileName %>&backTo=<%= eventsFor %>" class="btn" style="background-color: #ff9800;">▶ 이벤트 영상 보기</a>
                                            </td>
                                        </tr>
                        <%
                                        }
                                        if (!hasEvtData) {
                        %>
                                        <tr><td colspan="3">감지된 교통위반 사항이 없습니다.</td></tr>
                        <%
                                        }
                                    }
                                }
                            } catch (Exception e) { e.printStackTrace(); }
                        %>
                    </tbody>
                </table>
            </div>
            <hr style="margin-top: 30px;">
            <a href="video.jsp" class="back-link">← 메인 보관함으로 돌아가기</a>

        <% } else { %>
            <!-- ============================== -->
            <!-- 3. 메인 영상 목록 화면 (기본) -->
            <!-- ============================== -->
            <div class="table-wrapper">
                <table>
                    <thead>
                        <tr>
                            <th width="10%">순번</th>
                            <th width="35%">촬영 시간</th>
                            <th width="15%">분석 상태</th>
                            <th width="20%">원본 영상</th>
                            <th width="20%">이벤트 리스트</th>
                        </tr>
                    </thead>
                    <tbody>
                        <%
                            int count = 1;
                            try (Connection conn = DBUtil.getConnection()) {
                                // 현재 로그인한 사용자의 영상만 시간 역순(최신순)으로 가져옵니다.
                                String sql = "SELECT video_file_name, start_time, analysis_status FROM main_videos WHERE user_id = ? ORDER BY start_time DESC";
                                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                                    pstmt.setString(1, userId);
                                    try (ResultSet rs = pstmt.executeQuery()) {
                                        boolean hasData = false;
                                        while (rs.next()) {
                                            hasData = true;
                                            String fileName = rs.getString("video_file_name");
                                            Timestamp startTime = rs.getTimestamp("start_time");
                                            String status = rs.getString("analysis_status");
                        %>
                                        <tr>
                                            <td><%= count++ %></td>
                                            <td><%= startTime != null ? startTime.toString() : "기록 없음" %></td>
                                            <td style='font-weight: bold; color: <%= "완료".equals(status) ? "green" : "orange" %>;'>
                                                <%= status != null ? status : "알 수 없음" %>
                                            </td>
                                            <td>
                                                <a href="video.jsp?play=<%= fileName %>" class="btn">▶ 원본 재생</a>
                                            </td>
                                            <td>
                                                <% if ("완료".equals(status)) { %>
                                                    <a href="video.jsp?eventsFor=<%= fileName %>" class="btn" style="background-color: #dc3545; font-size: 0.9em;">🚨 이벤트 보기</a>
                                                <% } else { %>
                                                    <span style="color: #999; font-size: 0.9em;">-</span>
                                                <% } %>
                                            </td>
                                        </tr>
                        <%
                                        }
                                        if (!hasData) {
                        %>
                                        <tr><td colspan="5">저장된 블랙박스 영상이 없습니다.</td></tr>
                        <%
                                        }
                                    }
                                }
                            } catch (Exception e) { e.printStackTrace(); }
                        %>
                    </tbody>
                </table>
            </div>
            <hr style="margin-top: 30px;">
            <a href="blackbox.jsp" class="back-link" style="color: #28a745; margin-bottom: 10px;">블랙박스 촬영 화면으로 이동</a>
            <a href="index.jsp?auto=false" class="back-link">메인 화면으로 돌아가기</a>
        <% } %>
    </div>
</body>
</html>