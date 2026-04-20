package com.aicamera.servlets;

import com.aicamera.util.ConfigUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/serveVideo")
public class VideoServeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String fileName = req.getParameter("file");
        
        if (fileName == null || fileName.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "파일명이 제공되지 않았습니다.");
            return;
        }

        // 디렉토리 트래버설(Directory Traversal) 공격 방지: 파일명에 경로 조작 문자가 있는지 확인
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 파일명입니다.");
            return;
        }

        File videoFile = new File(
        if (!videoFile.exists()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "영상을 찾을 수 없습니다.");
            return;
        }

        resp.setContentType("video/mp4");
        resp.setHeader("Content-Length", String.valueOf(videoFile.length()));
        resp.setHeader("Content-Disposition", "inline; filename=\"" + videoFile.getName() + "\"");
        resp.setHeader("Accept-Ranges", "bytes"); // 브라우저가 영상 탐색(Seeking)을 할 수 있도록 명시

        // 파일 데이터를 브라우저로 전송 (스트리밍)
        try (FileInputStream in = new FileInputStream(videoFile); OutputStream out = resp.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}