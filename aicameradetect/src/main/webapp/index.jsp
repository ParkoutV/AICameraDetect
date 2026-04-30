<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    if ("logout".equals(request.getParameter("action"))) {
        session.invalidate();
        response.sendRedirect("index.jsp");
        return;
    }
    String loggedInUser = (String) session.getAttribute("userId");
%>
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
<% if (loggedInUser != null) { %>
    <div class="login-container">
        <h2 style="text-align: center;">AI 카메라 시스템</h2>
        <div class="form-group" style="text-align: center; margin-bottom: 20px;">
            <label style="margin-right: 15px;">
                <input type="radio" name="accessTypeLogged" value="blackbox" checked> 블랙박스
            </label>
            <label>
                <input type="radio" name="accessTypeLogged" value="video"> 영상확인
            </label>
        </div>
        <p style="text-align: center; font-size: 16px; margin-bottom: 20px;">
            현재 <strong style="color: #007BFF;"><%= loggedInUser %></strong> 계정으로 로그인 중입니다.
        </p>
        <div style="display:flex; gap:10px;">
            <button type="button" onclick="doLogout()" style="flex:1; padding:10px; background-color:#dc3545; color:white; border:none; border-radius:4px; cursor:pointer;">로그아웃</button>
            <button type="button" onclick="proceedToPage()" style="flex:1; padding:10px; background-color:#28a745; color:white; border:none; border-radius:4px; cursor:pointer;">접속하기</button>
        </div>
    </div>

    <!-- 자동 접속 알림(모달) UI (이미 로그인된 경우) -->
    <div id="autoProceedOverlay" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.7); align-items:center; justify-content:center; z-index:9999;">
        <div style="background:white; padding:25px; border-radius:8px; text-align:center; color:black; width: 80%; max-width: 320px;">
            <h3 style="margin-top:0; color:#333;">자동 접속</h3>
            <p style="font-size: 15px; color: #555; margin-bottom: 10px; line-height: 1.5;">
                <strong style="color:#007BFF; font-size:1.1em;"><%= loggedInUser %></strong> 계정으로<br>
                <strong id="proceedTypeDisplay"></strong>에 접속 하시겠습니까?
            </p>
            <p style="color:#dc3545; font-weight:bold; margin-bottom:20px;"><span id="autoProceedTimer">10</span>초 후 자동으로 접속됩니다.</p>
            <div style="display:flex; gap:10px;">
                <button type="button" id="cancelProceedBtn" style="flex:1; padding:10px; background-color:#6c757d; color:white; border:none; border-radius:5px; cursor:pointer;">취소</button>
                <button type="button" id="confirmProceedBtn" style="flex:1; padding:10px; background-color:#28a745; color:white; border:none; border-radius:5px; cursor:pointer;">접속</button>
            </div>
        </div>
    </div>

    <script>
        function doLogout() {
            localStorage.removeItem('savedUserId');
            localStorage.removeItem('savedUserPw');
            localStorage.removeItem('savedAccessType');
            window.location.href = 'index.jsp?action=logout';
        }
        function proceedToPage() {
            // 사용자가 수동으로 선택한 접속 타입도 다음 번을 위해 저장합니다.
            const type = document.querySelector('input[name="accessTypeLogged"]:checked').value;
            localStorage.setItem('savedAccessType', type);
            
            if (type === 'blackbox') window.location.href = 'blackbox.jsp';
            else window.location.href = 'video.jsp';
        }

        document.addEventListener('DOMContentLoaded', () => {
            // "메인 화면으로 돌아가기" 버튼이나 브라우저 뒤로가기를 통해 온 경우 자동 접속 비활성화
            const navEntry = performance.getEntriesByType("navigation")[0];
            const isBackForward = navEntry && navEntry.type === 'back_forward';
            const urlParams = new URLSearchParams(window.location.search);
            const preventAuto = urlParams.get('auto') === 'false';

            if (preventAuto) {
                window.history.replaceState({}, document.title, window.location.pathname); // URL에서 파라미터를 숨겨서 다음번 새로고침 시에는 정상 작동하도록 처리
            }
            if (preventAuto || isBackForward) {
                return; // 모달을 띄우지 않고 자동 접속 로직을 여기서 즉시 종료합니다.
            }

            // 이전에 저장된 접속 타입 불러오기 (기본값: 블랙박스)
            const savedAccessType = localStorage.getItem('savedAccessType') || 'blackbox';
            const targetRadio = document.querySelector(`input[name="accessTypeLogged"][value="${savedAccessType}"]`);
            if (targetRadio) targetRadio.checked = true;

            // 모달 초기화
            const overlay = document.getElementById('autoProceedOverlay');
            const timerSpan = document.getElementById('autoProceedTimer');
            document.getElementById('proceedTypeDisplay').textContent = savedAccessType === 'blackbox' ? '블랙박스' : '영상확인';
            
            overlay.style.display = 'flex';
            let timeLeft = 10;
            
            const cancelProceed = () => {
                clearInterval(timerInterval);
                overlay.style.display = 'none';
            };

            const timerInterval = setInterval(() => {
                timeLeft--;
                timerSpan.textContent = timeLeft;
                if (timeLeft <= 0) {
                    clearInterval(timerInterval);
                    proceedToPage();
                }
            }, 1000);

            document.getElementById('confirmProceedBtn').onclick = () => { clearInterval(timerInterval); proceedToPage(); };
            document.getElementById('cancelProceedBtn').onclick = cancelProceed;
            overlay.onclick = (e) => { if (e.target === overlay) cancelProceed(); };
        });

        // bfcache(뒤로가기 캐시)에서 페이지가 복원될 때를 감지하는 이벤트 리스너
        window.addEventListener('pageshow', function(event) {
            // event.persisted가 true이면 뒤로가기/앞으로가기로 페이지에 진입한 것입니다.
            if (event.persisted) {
                // 멈춰있는 모달이 화면에 남아있는 문제를 해결하기 위해 강제로 숨깁니다.
                const overlay = document.getElementById('autoProceedOverlay');
                if (overlay) {
                    overlay.style.display = 'none';
                }
            }
        });
    </script>
<% } else { %>
    <div class="login-container">
        <h2 style="text-align: center;">AI 카메라 시스템 로그인</h2>
        <form action="login" method="post" onsubmit="saveLoginInfo()">
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

    <!-- 자동 로그인 알림(모달) UI -->
    <div id="autoLoginOverlay" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.7); align-items:center; justify-content:center; z-index:9999;">
        <div style="background:white; padding:25px; border-radius:8px; text-align:center; color:black; width: 80%; max-width: 320px;">
            <h3 style="margin-top:0; color:#333;">자동 로그인</h3>
            <p style="font-size: 15px; color: #555; margin-bottom: 10px; line-height: 1.5;">
                <strong id="savedIdDisplay" style="color:#007BFF; font-size:1.1em;"></strong> 계정으로<br>
                <strong id="savedTypeDisplay"></strong>에 로그인 하시겠습니까?
            </p>
            <p style="color:#dc3545; font-weight:bold; margin-bottom:20px;"><span id="autoLoginTimer">10</span>초 후 자동으로 로그인됩니다.</p>
            <div style="display:flex; gap:10px;">
                <button type="button" id="cancelAutoLoginBtn" style="flex:1; padding:10px; background-color:#6c757d; color:white; border:none; border-radius:5px; cursor:pointer;">취소</button>
                <button type="button" id="confirmAutoLoginBtn" style="flex:1; padding:10px; background-color:#28a745; color:white; border:none; border-radius:5px; cursor:pointer;">로그인</button>
            </div>
        </div>
    </div>

    <script>
        // 로그인 폼 제출 시 입력 정보 내부 저장소에 저장 (자동 로그인 용도)
        function saveLoginInfo() {
            localStorage.setItem('savedUserId', document.querySelector('input[name="userId"]').value);
            localStorage.setItem('savedUserPw', document.querySelector('input[name="userPw"]').value);
            localStorage.setItem('savedAccessType', document.querySelector('input[name="accessType"]:checked').value);
        }

        // 페이지 로드 시 URL 파라미터를 확인하여 에러 메시지를 표시합니다.
        document.addEventListener('DOMContentLoaded', () => {
            const urlParams = new URLSearchParams(window.location.search);
            if (urlParams.has('error')) {
                const errorType = urlParams.get('error');
                if (errorType === 'true') {
                    alert('아이디 또는 비밀번호가 일치하지 않습니다.');
                    localStorage.removeItem('savedUserPw'); // 틀린 비밀번호로 무한 자동 로그인을 시도하는 것을 방지
                } else if (errorType === 'db_error') {
                    alert('데이터베이스 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
                }

                // 취소되거나 실패한 경우, 편의를 위해 아이디와 접속 유형은 남겨둡니다.
                const savedUserId = localStorage.getItem('savedUserId');
                const savedAccessType = localStorage.getItem('savedAccessType');
                if (savedUserId) {
                    document.querySelector('input[name="userId"]').value = savedUserId;
                }
                if (savedAccessType) {
                    const targetRadio = document.querySelector(`input[name="accessType"][value="${savedAccessType}"]`);
                    if (targetRadio) targetRadio.checked = true;
                }

                // 알림 후 URL에서 에러 파라미터를 제거하여 새로고침 시 알림이 다시 뜨지 않도록 합니다.
                window.history.replaceState({}, document.title, window.location.pathname);
            } else {
                // 에러 없이 정상적으로 로그인 창에 들어온 경우 (자동 로그인 대상)
                const savedUserId = localStorage.getItem('savedUserId');
                const savedUserPw = localStorage.getItem('savedUserPw');
                const savedAccessType = localStorage.getItem('savedAccessType') || 'blackbox';

                if (savedUserId && savedUserPw) {
                    const overlay = document.getElementById('autoLoginOverlay');
                    const timerSpan = document.getElementById('autoLoginTimer');
                    
                    document.getElementById('savedIdDisplay').textContent = savedUserId;
                    document.getElementById('savedTypeDisplay').textContent = savedAccessType === 'blackbox' ? '블랙박스' : '영상확인';
                    
                    overlay.style.display = 'flex';
                    let timeLeft = 10;
                    
                    const doAutoLogin = () => {
                        document.querySelector('input[name="userId"]').value = savedUserId;
                        document.querySelector('input[name="userPw"]').value = savedUserPw;
                        document.querySelector(`input[name="accessType"][value="${savedAccessType}"]`).checked = true;
                        
                        document.querySelector('form').submit(); // 폼 강제 전송
                    };

                    const cancelAutoLogin = () => {
                        clearInterval(timerInterval);
                        overlay.style.display = 'none';
                        
                        // 모달 취소 시 화면 폼에 기존 아이디 세팅
                        document.querySelector('input[name="userId"]').value = savedUserId;
                        document.querySelector(`input[name="accessType"][value="${savedAccessType}"]`).checked = true;
                    };

                    // 1초마다 카운트다운 타이머
                    const timerInterval = setInterval(() => {
                        timeLeft--;
                        timerSpan.textContent = timeLeft;
                        if (timeLeft <= 0) {
                            clearInterval(timerInterval);
                            doAutoLogin();
                        }
                    }, 1000);

                    // 버튼 및 백그라운드 클릭 이벤트 등록
                    document.getElementById('confirmAutoLoginBtn').onclick = () => {
                        clearInterval(timerInterval);
                        doAutoLogin();
                    };
                    
                    document.getElementById('cancelAutoLoginBtn').onclick = cancelAutoLogin;
                    
                    overlay.onclick = (e) => {
                        if (e.target === overlay) cancelAutoLogin();
                    };
                }
            }
        });

        // bfcache(뒤로가기 캐시)에서 페이지가 복원될 때를 감지하는 이벤트 리스너
        window.addEventListener('pageshow', function(event) {
            if (event.persisted) {
                // 멈춰있는 모달이 화면에 남아있는 문제를 해결하기 위해 강제로 숨깁니다.
                const overlay = document.getElementById('autoLoginOverlay');
                if (overlay) {
                    overlay.style.display = 'none';
                }
            }
        });
    </script>
<% } %>
</body>
</html>
