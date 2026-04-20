package com.aicamera.util;

import java.io.InputStream;
import java.util.Properties;

public class ConfigUtil {
    private static final Properties properties = new Properties();

    static {
        // 클래스패스에서 db.properties 파일을 찾아 로드합니다.
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                properties.load(in);
            } else {
                System.err.println("db.properties 파일을 찾을 수 없습니다. 기본 설정 경로를 사용합니다.");
            }
        } catch (Exception e) {
            System.err.println("db.properties 로드 중 오류 발생:");
            e.printStackTrace();
        }
    }

    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static String getTempVideoPath() {
        return getProperty("path.temp_videos", "C:\\Users\\kghbs\\aicamera_uploads\\temp_videos");
    }

    public static String getFinalVideoPath() {
        return getProperty("path.final_videos", "C:\\Users\\kghbs\\aicamera_uploads\\final_videos");
    }
}