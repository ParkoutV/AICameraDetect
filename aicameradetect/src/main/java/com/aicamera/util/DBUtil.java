package com.aicamera.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import javax.servlet.ServletContext;

public class DBUtil {
    public static Connection getConnection(ServletContext context) throws Exception {
        Properties props = new Properties();
        // 경로는 webapp 폴더 기준입니다.
        try (InputStream in = context.getResourceAsStream("/db.properties")) {
            if (in == null) {
                throw new RuntimeException("db.properties 파일을 찾을 수 없습니다. 경로를 확인하세요.");
            }
            props.load(in);
        }
        
        String dbUrl = props.getProperty("db.url");
        String dbUser = props.getProperty("db.user");
        String dbPw = props.getProperty("db.password");

        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(dbUrl, dbUser, dbPw);
    }
}