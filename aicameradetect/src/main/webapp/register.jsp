<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>AI Camera Detect - 회원가입</title>
    <style>
        .register-container {
            width: 350px;
            margin: 50px auto;
            padding: 30px;
            border: 1px solid #ddd;
            border-radius: 8px;
            box-shadow: 0 4px 8px rgba(0,0,0,0.1);
        }
        .form-group {
            margin-bottom: 15px;
        }
        .form-group label {
            display: block;
            margin-bottom: 5px;
            font-size: 14px;
            font-weight: bold;
        }
        .form-group input[type="text"],
        .form-group input[type="password"],
        .form-group input[type="email"] {
            width: 100%;
            padding: 10px;
            box-sizing: border-box;
        }
        .submit-btn {
            width: 100%;
            padding: 10px;
            background-color: #28a745;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 16px;
        }
        .submit-btn:hover {
            background-color: #218838;
        }
        .back-link {
            display: block;
            text-align: center;
            margin-top: 15px;
            text-decoration: none;
            color: #007BFF;
            font-size: 14px;
        }
    </style>
</head>
<body>
    <div class="register-container">
        <h2 style="text-align: center;">시스템 회원가입</h2>
        <form action="registerProcess.jsp" method="post">
            <div class="form-group">
                <label>아이디</label>
                <input type="text" name="newId" placeholder="사용할 아이디 입력" required>
            </div>
            <div class="form-group">
                <label>비밀번호</label>
                <input type="password" name="newPw" placeholder="비밀번호 입력" required>
            </div>
            <div class="form-group">
                <label>비밀번호 확인</label>
                <input type="password" name="newPwConfirm" placeholder="비밀번호 다시 입력" required>
            </div>
            <div class="form-group">
                <label>이메일</label>
                <input type="email" name="email" placeholder="example@email.com">
            </div>
            <button type="submit" class="submit-btn">가입하기</button>
            <a href="index.jsp" class="back-link">로그인 화면으로 돌아가기</a>
        </form>
    </div>
</body>
</html>