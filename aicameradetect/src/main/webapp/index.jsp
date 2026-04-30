<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>AI Camera Detect - 시스템 로그인</title>
    <style>
        .login-container {
            width: 90%;
            max-width: 350px;
            margin: 10vh auto;
            padding: 30px;
            border: 1px solid #ddd;
            border-radius: 8px;
            box-shadow: 0 4px 8px rgba(0,0,0,0.1);
            box-sizing: border-box;
        }
        .form-group {
            margin-bottom: 15px;
        }
        .form-group input[type="text"],
        .form-group input[type="password"] {
            width: 100%;
            padding: 10px;
            box-sizing: border-box;
        }
        .submit-btn {
            width: 100%;
            padding: 10px;
            background-color: #007BFF;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 16px;
        }
        .submit-btn:hover {
            background-color: #0056b3;
        }
    </style>
</head>
<body>
    <div class="login-container">
        <h2 style="text-align: center;">AI 카메라 시스템 로그인</h2>
        <form action="login" method="post">
            <div class="form-group" style="text-align: center; margin-bottom: 20px;">
                <label style="margin-right: 15px;">
                    <input type="radio" name="accessType" value="blackbox" checked> 블랙박스
                </label>
                <label>
                    <input type="radio" name="accessType" value="video"> 영상확인
                </label>
            </div>
            <div class="form-group">
                <input type="text" name="userId" placeholder="아이디" required>
            </div>
            <div class="form-group">
                <input type="password" name="userPw" placeholder="비밀번호" required>
            </div>
            <button type="submit" class="submit-btn">로그인</button>
            <div style="text-align: center; margin-top: 15px; font-size: 14px;">
                아직 계정이 없으신가요? <a href="register.jsp" style="text-decoration: none; color: #007BFF;">회원가입</a>
            </div>
        </form>
    </div>

    <script>
        // 페이지 로드 시 URL 파라미터를 확인하여 에러 메시지를 표시합니다.
        document.addEventListener('DOMContentLoaded', () => {
            const urlParams = new URLSearchParams(window.location.search);
            if (urlParams.has('error')) {
                const errorType = urlParams.get('error');
                if (errorType === 'true') {
                    alert('아이디 또는 비밀번호가 일치하지 않습니다.');
                } else if (errorType === 'db_error') {
                    alert('데이터베이스 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
                }
                // 알림 후 URL에서 에러 파라미터를 제거하여 새로고침 시 알림이 다시 뜨지 않도록 합니다.
                window.history.replaceState({}, document.title, window.location.pathname);
            }
        });
    </script>
</body>
</html>
