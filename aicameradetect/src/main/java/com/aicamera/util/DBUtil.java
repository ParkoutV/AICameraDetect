package com.aicamera.util;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBUtil {
    public static Connection getConnection() throws Exception {
        // ConfigUtil을 통해 중앙에서 관리되는 설정 정보를 사용합니다.
        String dbUrl = ConfigUtil.getProperty("db.url", null);
        String dbUser = ConfigUtil.getProperty("db.user", null);
        String dbPw = ConfigUtil.getProperty("db.password", null);

        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(dbUrl, dbUser, dbPw);
    }
}