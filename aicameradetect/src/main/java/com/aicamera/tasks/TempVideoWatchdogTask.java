package com.aicamera.tasks;

import com.aicamera.servlets.StopRecordingServlet;
import com.aicamera.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 주기적으로 실행되어 5분 이상 새로운 조각이 업로드되지 않은
 * '고아(orphan)' 녹화 세션을 찾아 강제로 병합 및 처리하는 Watchdog 클래스입니다.
 */
public class TempVideoWatchdogTask implements Runnable {

    private static final Set<String> processingIds = new HashSet<>();

    // 세션 정보를 담기 위한 내부 클래스
    private static class SessionInfo {
        String userId;
        String recordingId;
        long lastUpdatedAt;
    }

    @Override
    public void run() {
        System.out.println("[Watchdog] 비정상 종료된 녹화 세션을 확인합니다...");

        // recording_id DB 컬럼 없이 파일명에서 추출하기 위해 전체를 조회합니다.
        String sql = "SELECT user_id, segment_filename, created_at FROM temp_videos";
        Map<String, SessionInfo> sessions = new HashMap<>();

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String userId = rs.getString("user_id");
                String filename = rs.getString("segment_filename");
                Timestamp createdAt = rs.getTimestamp("created_at");

                // 파일명 형식: userId_recordingId_counter.webm
                String[] parts = filename.split("_");
                if (parts.length >= 3) {
                    // 유저 ID에 '_'가 포함될 수 있으므로, 뒤에서 두 번째 요소를 recordingId로 추출합니다.
                    String recordingId = parts[parts.length - 2];
                    String uniqueId = userId + ":" + recordingId;

                    SessionInfo info = sessions.computeIfAbsent(uniqueId, k -> {
                        SessionInfo s = new SessionInfo();
                        s.userId = userId;
                        s.recordingId = recordingId;
                        s.lastUpdatedAt = 0;
                        return s;
                    });

                    if (createdAt != null && createdAt.getTime() > info.lastUpdatedAt) {
                        info.lastUpdatedAt = createdAt.getTime();
                    }
                }
            }

            long now = System.currentTimeMillis();
            long fiveMinutesInMillis = 5 * 60 * 1000;

            for (SessionInfo info : sessions.values()) {
                // 마지막 업데이트 시간으로부터 5분이 지났는지 확인
                if ((now - info.lastUpdatedAt) > fiveMinutesInMillis) {
                    String uniqueProcessingId = info.userId + ":" + info.recordingId;

                    synchronized (processingIds) {
                        if (processingIds.contains(uniqueProcessingId)) continue;
                        processingIds.add(uniqueProcessingId);
                    }

                    System.out.println("[Watchdog] 5분 이상 업데이트 없는 녹화 ID(" + info.recordingId + ")를 발견했습니다. 강제 병합을 시작합니다.");

                    CompletableFuture.runAsync(() -> {
                        try {
                            StopRecordingServlet.processAndMergeSegments(info.userId, info.recordingId);
                        } finally {
                            synchronized (processingIds) { processingIds.remove(uniqueProcessingId); }
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("[Watchdog] 고아 녹화 세션 확인 중 오류 발생:");
            e.printStackTrace();
        }
    }
}