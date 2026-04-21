package com.aicamera.servlets;

import com.aicamera.util.ConfigUtil;

import java.io.File;
import java.io.RandomAccessFile;
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
        
        System.out.println("\n[VideoServeServlet] --- Video serving request started ---");
        System.out.println("[VideoServeServlet] Requested file parameter: " + fileName);

        if (fileName == null || fileName.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "파일명이 제공되지 않았습니다.");
            return;
        }

        // 디렉토리 트래버설(Directory Traversal) 공격 방지: 파일명에 경로 조작 문자가 있는지 확인
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "잘못된 파일명입니다.");
            return;
        }

        System.out.println("[VideoServeServlet] Fetching final_videos path from ConfigUtil.");
        String basePath = ConfigUtil.getFinalVideoPath();
        System.out.println("[VideoServeServlet] Base folder path: " + basePath);

        File videoFile = new File(basePath, fileName);
        System.out.println("[VideoServeServlet] Absolute path to find: " + videoFile.getAbsolutePath());

        if (!videoFile.exists()) {
            System.err.println("[VideoServeServlet] [ERROR] File does not exist! Returning 404. Path: " + videoFile.getAbsolutePath());
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "영상을 찾을 수 없습니다.");
            return;
        }
        
        System.out.println("[VideoServeServlet] File found! Size: " + videoFile.length() + " bytes");

        long length = videoFile.length();
        long start = 0;
        long end = length - 1;

        // Range 헤더 처리 로직 (영상 건너뛰기 지원)
        String range = req.getHeader("Range");
        if (range != null && range.startsWith("bytes=")) {
            String[] ranges = range.substring(6).split("-");
            try {
                if (ranges.length > 0 && !ranges[0].isEmpty()) {
                    start = Long.parseLong(ranges[0]);
                }
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    end = Long.parseLong(ranges[1]);
                }
            } catch (NumberFormatException e) {
                start = 0;
                end = length - 1;
            }
        }

        long contentLength = end - start + 1;

        resp.setContentType("video/mp4");
        resp.setHeader("Accept-Ranges", "bytes");
        resp.setHeader("Content-Disposition", "inline; filename=\"" + videoFile.getName() + "\"");

        if (range != null) {
            resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT); // 206 상태 코드
            resp.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + length);
        } else {
            resp.setStatus(HttpServletResponse.SC_OK); // 200 상태 코드
        }
        resp.setHeader("Content-Length", String.valueOf(contentLength));

        // 파일 데이터를 브라우저로 전송 (스트리밍 및 탐색 지원)
        try (RandomAccessFile raf = new RandomAccessFile(videoFile, "r"); OutputStream out = resp.getOutputStream()) {
            raf.seek(start); // 요청한 시작 지점으로 이동
            byte[] buffer = new byte[8192];
            long bytesToRead = contentLength;
            int bytesRead;
            
            while (bytesToRead > 0 && (bytesRead = raf.read(buffer, 0, (int) Math.min(buffer.length, bytesToRead))) != -1) {
                out.write(buffer, 0, bytesRead);
                bytesToRead -= bytesRead;
            }
        }
    }
}