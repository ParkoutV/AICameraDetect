package com.aicamera.servlets;

import com.aicamera.tasks.AnalyzedReportTask;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/runAnalysisTask")
public class RunAnalysisTaskServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String userId = (String) session.getAttribute("userId");

        // root 계정만 접근 가능하도록 권한 검사
        if (userId == null || !"root".equals(userId)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다.");
            return;
        }

        // 백그라운드 스레드에서 AnalyzedReportTask 실행 (UI 멈춤 방지)
        new Thread(new AnalyzedReportTask()).start();

        // 처리 완료 후 blackbox.jsp로 성공 메시지와 함께 돌아감
        resp.sendRedirect("blackbox.jsp?taskSuccess=true");
    }
}