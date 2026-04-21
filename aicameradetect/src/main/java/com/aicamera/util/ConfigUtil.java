package com.aicamera.util;

import java.io.InputStream;
import java.util.Properties;
import javax.servlet.ServletContext;

public class ConfigUtil {
    private static final Properties properties = new Properties();
    private static boolean isInitialized = false;

    /**
     * 웹 애플리케이션 시작 시 ServletContext를 이용해 설정을 초기화합니다.
     * 이 메서드는 한 번만 호출되어야 합니다. (예: ServletContextListener에서)
     * @param context 웹 애플리케이션의 ServletContext
     */
    public static synchronized void init(ServletContext context) {
        if (isInitialized) {
            return;
        }
        
        System.out.println("[ConfigUtil] Starting to search for db.properties...");
        // 기존 환경(resources, webapp 루트)과 새로운 환경(WEB-INF)을 모두 지원하도록 탐색 범위 확대
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("db.properties");
        if (in != null) System.out.println("[ConfigUtil] Found db.properties in classpath.");

        if (in == null) {
            in = context.getResourceAsStream("/db.properties");
            if (in != null) System.out.println("[ConfigUtil] Found db.properties in /db.properties.");
        }
        if (in == null) {
            in = context.getResourceAsStream("/WEB-INF/db.properties");
            if (in != null) System.out.println("[ConfigUtil] Found db.properties in /WEB-INF/db.properties.");
        }

        try {
            if (in != null) {
                properties.load(in);
                isInitialized = true;
                System.out.println("[ConfigUtil] Successfully loaded db.properties.");
                System.out.println("[ConfigUtil] Loaded path.temp_videos: " + properties.getProperty("path.temp_videos"));
                System.out.println("[ConfigUtil] Loaded path.final_videos: " + properties.getProperty("path.final_videos"));
            } else {
                System.err.println("[ConfigUtil] db.properties file not found. Using default paths.");
            }
        } catch (Exception e) {
            System.err.println("[ConfigUtil] Error loading db.properties:");
            e.printStackTrace();
        } finally {
            if (in != null) try { in.close(); } catch(Exception e) {}
        }
    }

    public static String getProperty(String key, String defaultValue) {
        if (!isInitialized) {
            System.err.println("[ConfigUtil] Warning: ConfigUtil is not initialized. Returning default value.");
        }
        String value = properties.getProperty(key, defaultValue);
        System.out.println("[ConfigUtil] getProperty called - key: " + key + ", returning value: " + value);
        return value;
    }

    public static String getTempVideoPath() {
        String path = getProperty("path.temp_videos", "C:\\Users\\kghbs\\aicamera_uploads\\temp_videos");
        System.out.println("[ConfigUtil] getTempVideoPath returning: " + path);
        return path;
    }

    public static String getFinalVideoPath() {
        String path = getProperty("path.final_videos", "C:\\Users\\kghbs\\aicamera_uploads\\final_videos");
        System.out.println("[ConfigUtil] getFinalVideoPath returning: " + path);
        return path;
    }
}