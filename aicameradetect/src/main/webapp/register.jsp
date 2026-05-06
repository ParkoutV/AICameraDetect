<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>AI Camera Detect - 회원가입</title>
    <style>
        body {
            background-color: #F0F8FF;
            font-family: 'Noto Sans KR', sans-serif;
            color: #1A2B4C;
        }
        .login-container, .register-container {
            width: 90%;
            max-width: 350px;
            margin: 10vh auto;
            padding: 30px;
            background-color: rgba(255, 255, 255, 0.95);
            border: 2px solid #87CEFA; 
            border-radius: 12px;
            box-shadow: 0 8px 20px rgba(26, 43, 76, 0.15);
            box-sizing: border-box;
        }
        .form-group {
            margin-bottom: 15px;
        }
        .form-group input[type="text"],
        .form-group input[type="password"],
        .form-group input[type="email"] {
            width: 100%;
            padding: 10px;
            border: 1px solid #B3D4FF;
            border-radius: 6px;
            box-sizing: border-box;
        }
        .submit-btn {
            width: 100%;
            padding: 10px;
            background-color: #00A2E8;
            color: white;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            font-size: 16px;
            font-weight: bold;
        }
        .submit-btn:hover {
            background-color: #007BB5;
        }
        .check-btn {
            padding: 10px;
            background-color: #2C3E50;
            color: white;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            font-size: 14px;
            white-space: nowrap;
            box-sizing: border-box;
        }
        .check-btn:hover { background-color: #1A252F; }
        .back-link {
            display: block;
            text-align: center;
            margin-top: 15px;
            text-decoration: none;
            color: #00A2E8;
            font-size: 14px;
            font-weight: bold;
        }
    </style>
</head>
<body>
    <div class="register-container">
        <h2 style="text-align: center;">시스템 회원가입</h2>
        <form action="register" method="post" onsubmit="return validateForm()">
            <div class="form-group">
                <label>아이디</label>
                <div style="display: flex; gap: 10px;">
                    <input type="text" id="newId" name="newId" placeholder="사용할 아이디 입력" required oninput="resetIdCheck()">
                    <button type="button" class="check-btn" onclick="checkDuplicateId()">중복확인</button>
                </div>
                <span id="idCheckMsg" style="font-size: 12px; display: block; margin-top: 5px;"></span>
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

    <script>
        let isIdChecked = false;

        // 아이디 입력값이 변경되면 중복 확인 상태 초기화
        function resetIdCheck() {
            isIdChecked = false;
            document.getElementById('idCheckMsg').textContent = '';
        }

        // AJAX를 이용해 아이디 중복 검사
        async function checkDuplicateId() {
            const idInput = document.getElementById('newId').value.trim();
            const msgSpan = document.getElementById('idCheckMsg');
            
            if (!idInput) {
                alert('아이디를 먼저 입력해주세요.');
                return;
            }

            try {
                const response = await fetch('checkId.jsp?id=' + encodeURIComponent(idInput));
                const result = await response.json();

                if (result.exists) {
                    msgSpan.textContent = '이미 사용 중인 아이디입니다.';
                    msgSpan.style.color = 'red';
                    isIdChecked = false;
                } else {
                    msgSpan.textContent = '사용 가능한 아이디입니다.';
                    msgSpan.style.color = 'green';
                    isIdChecked = true;
                }
            } catch (error) {
                alert('중복 확인 중 서버 오류가 발생했습니다.');
            }
        }

        // 폼 제출 전 검증
        function validateForm() {
            if (!isIdChecked) {
                alert('아이디 중복 확인을 완료해주세요.');
                return false;
            }
            return true;
        }
    </script>
</body>
</html>