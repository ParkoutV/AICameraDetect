package com.aicamera.tasks;

import com.aicamera.util.ConfigUtil;
import com.aicamera.util.DBUtil;
import com.aicamera.util.GoogleDriveUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalyzedReportTask implements Runnable {
    // 동시 다운로드 수를 제한하기 위한 스레드 풀 (Quote/Queue 시스템 역할 - 최대 3개 영상 동시 다운로드)
    private static final ExecutorService downloadExecutor = Executors.newFixedThreadPool(3);

    private static class EventInfo {
        String videoName;
        String timeStr;
        String typeStr;
        String details;
    }

    @Override
    public void run() {
        System.out.println("[AnalyzedReportTask] 구글 드라이브에서 분석 완료 보고서(XML)를 확인합니다...");
        try {
            String folderId = ConfigUtil.getProperty("gdrive.analyzed_folder.id", "");
            if (folderId == null || folderId.isEmpty()) {
                System.err.println("[AnalyzedReportTask] gdrive.analyzed_folder.id 설정이 없습니다.");
                return;
            }

            // 1. 해당 폴더에서 .xml 파일 목록 가져오기
            List<com.google.api.services.drive.model.File> xmlFiles = GoogleDriveUtil.findFilesByExtension(folderId, ".xml");
            if (xmlFiles == null || xmlFiles.isEmpty()) {
                return;
            }

            for (com.google.api.services.drive.model.File xmlFile : xmlFiles) {
                processXmlFile(xmlFile, folderId);
            }
        } catch (Exception e) {
            System.err.println("[AnalyzedReportTask] 실행 중 오류 발생:");
            e.printStackTrace();
        }
    }

    private void processXmlFile(com.google.api.services.drive.model.File xmlFile, String folderId) {
        try {
            System.out.println("[AnalyzedReportTask] XML 파일 처리 시작: " + xmlFile.getName());
            InputStream is = GoogleDriveUtil.downloadFileAsStream(xmlFile.getId());
            
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            doc.getDocumentElement().normalize();

            is.close(); // InputStream 안전하게 닫기

            // 중복 폴링(다운로드)을 방지하기 위해 메모리에 파싱된 직후 구글 드라이브에서 즉시 삭제합니다.
            GoogleDriveUtil.deleteFile(xmlFile.getId());

            String originalVideoName = getTagValue("VideoName", doc.getDocumentElement());
            if (originalVideoName == null || originalVideoName.isEmpty()) {
                System.err.println("[AnalyzedReportTask] VideoName 태그가 없습니다. 파일: " + xmlFile.getName());
                return;
            }

            NodeList eventVideoNodes = doc.getElementsByTagName("EventVideo");
            
            if (eventVideoNodes.getLength() == 0) {
                // 이벤트가 없을 경우: 즉시 DB 업데이트만 수행 (XML은 위에서 이미 삭제됨)
                updateMainVideoStatus(originalVideoName, "이벤트 없음");
                System.out.println("[AnalyzedReportTask] 이벤트 없음 처리 완료: " + originalVideoName);
                return;
            }

            List<EventInfo> events = new ArrayList<>();
            for (int i = 0; i < eventVideoNodes.getLength(); i++) {
                Element eventElement = (Element) eventVideoNodes.item(i);
                EventInfo event = new EventInfo();
                event.videoName = getTagValue("EventVideoName", eventElement);
                event.timeStr = getTagValue("EventTime", eventElement);
                event.typeStr = getTagValue("EventType", eventElement);
                event.details = getTagValue("EventDetails", eventElement);
                if (event.videoName != null) {
                    events.add(event);
                }
            }

            // 영상을 다운로드하고 DB에 기록하는 작업을 비동기(downloadExecutor)로 제출하여 병목 방지
            downloadExecutor.submit(() -> {
                boolean allSuccess = true;
                for (EventInfo event : events) {
                    try {
                        com.google.api.services.drive.model.File driveVideoFile = GoogleDriveUtil.findFileByName(folderId, event.videoName);
                        if (driveVideoFile != null) {
                            String finalPath = ConfigUtil.getFinalVideoPath();
                            File localVideo = new File(finalPath, event.videoName);
                            System.out.println("[AnalyzedReportTask] 이벤트 영상 다운로드 시작: " + event.videoName);
                            GoogleDriveUtil.downloadFile(driveVideoFile.getId(), localVideo);
                            
                            insertEventVideoDB(originalVideoName, event); // DB 등록
                            
                            // 구글 드라이브 용량 확보 및 중복 방지를 위해 다운받은 이벤트 영상 삭제
                            GoogleDriveUtil.deleteFile(driveVideoFile.getId());
                            System.out.println("[AnalyzedReportTask] 이벤트 영상 다운로드 완료: " + event.videoName);
                        } else {
                            System.err.println("[AnalyzedReportTask] 구글 드라이브에서 영상을 찾을 수 없습니다: " + event.videoName);
                            allSuccess = false;
                        }
                    } catch (Exception e) {
                        System.err.println("[AnalyzedReportTask] 이벤트 영상 처리 실패: " + event.videoName);
                        e.printStackTrace();
                        allSuccess = false;
                    }
                }

                // 모든 영상 다운로드 및 처리가 완료되었을 때만 '분석 완료'로 변경
                if (allSuccess) {
                    updateMainVideoStatus(originalVideoName, "분석 완료");
                    System.out.println("[AnalyzedReportTask] 분석 완료 처리 및 정리 완료: " + originalVideoName);
                }
            });

        } catch (Exception e) {
            System.err.println("[AnalyzedReportTask] XML 파싱 실패: " + xmlFile.getName());
            e.printStackTrace();
        }
    }

    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return null;
    }

    private void updateMainVideoStatus(String originalVideoName, String status) {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("UPDATE main_videos SET analysis_status = ? WHERE ? LIKE CONCAT('%', video_file_name)")) {
            pstmt.setString(1, status);
            pstmt.setString(2, originalVideoName);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void insertEventVideoDB(String originalVideoName, EventInfo event) {
        // 서브 쿼리를 통해 구글 드라이브 업로드용 파일명에서 순수 영상 파일명(video_file_name)을 추출하여 매핑합니다.
        String sql = "INSERT INTO event_videos (original_video_name, event_video_name, event_time, event_case, event_desc) VALUES ((SELECT video_file_name FROM main_videos WHERE ? LIKE CONCAT('%', video_file_name) LIMIT 1), ?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, originalVideoName);
            pstmt.setString(2, event.videoName);
            
            // 시간 파싱
            Timestamp eventTime = null;
            if (event.timeStr != null) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                    Date parsedDate = sdf.parse(event.timeStr);
                    eventTime = new Timestamp(parsedDate.getTime());
                } catch (Exception e) {
                    eventTime = new Timestamp(System.currentTimeMillis());
                }
            }
            pstmt.setTimestamp(3, eventTime);

            // 이벤트 케이스 매핑
            int eventCase = 0;
            if (event.typeStr != null) {
                if (event.typeStr.contains("신호")) eventCase = 1;
                else if (event.typeStr.contains("차선")) eventCase = 2;
                else if (event.typeStr.contains("속도")) eventCase = 3;
                else if (event.typeStr.matches("\\d+")) eventCase = Integer.parseInt(event.typeStr);
                else eventCase = 4; // 기타
            }
            pstmt.setInt(4, eventCase);
            pstmt.setString(5, event.details);

            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}