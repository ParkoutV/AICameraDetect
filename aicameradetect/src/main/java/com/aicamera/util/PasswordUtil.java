package com.aicamera.util;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {
    /**
     * BCrypt를 사용하여 비밀번호를 해시화합니다.
     */
    public static String hashPassword(String plainTextPassword) {
        return BCrypt.hashpw(plainTextPassword, BCrypt.gensalt());
    }

    /**
     * 입력된 비밀번호와 해시된 비밀번호가 일치하는지 확인합니다.
     */
    public static boolean checkPassword(String plainTextPassword, String hashedPassword) {
        return BCrypt.checkpw(plainTextPassword, hashedPassword);
    }
}