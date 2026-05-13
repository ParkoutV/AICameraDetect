<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<% String userId = (String) session.getAttribute("userId"); %>
<!DOCTYPE html> 
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>블랙박스 제어 - AI Camera Detect</title>
    <style>
        body { 
            font-family: 'Noto Sans KR', sans-serif; 
            background-color: #F0F8FF;
            margin: 0;
            color: #1A2B4C;
        }
        .container { 
            width: 100%;
            max-width: 800px; 
            margin: 0 auto; 
            padding: 20px; 
            background-color: #FFFFFF;
            box-sizing: border-box;
            border-radius: 12px;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
        }
        h1, h2 { text-align: center; color: #00A2E8; }
        video {
            width: 100%;
            background-color: #000;
            border: 2px solid #87CEFA;
            border-radius: 8px;
            margin-top: 15px;
        }
        .controls {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin: 20px 0;
        }
        .controls label { font-weight: bold; color: #2C3E50; }
        .controls select { padding: 8px; border-radius: 6px; border: 1px solid #B3D4FF; }
        .buttons button {
            padding: 10px 20px;
            font-size: 16px;
            font-weight: bold;
            cursor: pointer;
            border: none;
            border-radius: 6px;
            color: white;
            transition: background-color 0.3s;
        }
        #toggleBtn { background-color: #00A2E8; } 
        #toggleBtn.recording { background-color: #FFB6C1; color: #1A2B4C; }
        #toggleBtn:disabled { background-color: #A0B2C6; }
        #recordedList a {
            display: block;
            padding: 10px;
            margin-top: 5px;
            background-color: #E1F5FE;
            text-decoration: none;
            color: #1A2B4C;
            border-radius: 6px;
            border-left: 4px solid #00A2E8;
        }
        #recordedList a:hover { background-color: #B3E5FC; }

        @media (min-width: 769px) {
            .container { margin-top: 20px; margin-bottom: 20px; border: 1px solid #87CEFA; }
        }
        @media (max-width: 768px) {
            .controls { flex-direction: column; align-items: stretch; gap: 10px; }
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>차량 블랙박스 시스템 (<%= userId != null ? userId : "비로그인" %>)</h1>
        <p style="text-align: center;">블랙박스 기록 조회 및 설정을 관리하는 페이지입니다.</p>

        <div class="controls">
            <label for="videoSource">카메라 선택:</label>
            <select id="videoSource"></select>
        </div>

        <video id="video" playsinline autoplay muted></video>

        <div class="controls buttons">
            <button id="toggleBtn">녹화 시작</button>
        </div>

        <details>
            <summary>영상 조각 로그</summary>
            <div id="recordedList"></div>
        </details>
        

        <hr style="margin-top: 30px;">
        <a href="video.jsp" style="display: block; text-align: center; margin-bottom: 15px; color: #28a745; font-weight: bold; text-decoration: none;">영상 보관함으로 이동</a>
        <a href="index.jsp?auto=false" style="display: block; text-align: center; color: #333; font-weight: bold; text-decoration: none;">메인 화면으로 돌아가기</a>
    </div>

    <!-- 커스텀 알림(모달) UI: 첫 방문 시 카메라 선택창 -->
    <div id="cameraAlertOverlay" style="display:none; position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.8); align-items:center; justify-content:center; z-index:9999;">
        <div style="background:white; padding:20px; border-radius:8px; text-align:center; color:black; width: 80%; max-width: 300px;">
            <h3 style="margin-top:0;">카메라 선택</h3>
            <p style="font-size: 14px; color: #555; word-break: keep-all;">녹화를 시작할 기본 카메라를 선택해주세요.</p>
            <video id="alertPreviewVideo" playsinline autoplay muted style="width: 100%; height: 150px; background-color: #000; border-radius: 5px; margin-bottom: 15px; object-fit: cover;"></video>
            <select id="alertCameraSelect" style="padding:10px; width:100%; margin-bottom:20px; box-sizing: border-box;"></select>
            <button id="alertCameraBtn" style="padding:10px 20px; background-color:#007BFF; color:white; border:none; border-radius:5px; cursor:pointer; width: 100%;">확인 및 녹화 시작</button>
        </div>
    </div>

    <script>
        const videoSelect = document.getElementById('videoSource');
        const videoElement = document.getElementById('video');
        const toggleBtn = document.getElementById('toggleBtn');
        const recordedList = document.getElementById('recordedList');

        let mediaRecorder;
        let stream;
        let recordingInterval;
        let currentRecordingId;
        let segmentCounter = 1;
        let isRecording = false;
        let isStopping = false; // 녹화 중지 상태를 추적하는 변수
        let activeUploads = 0;  // 현재 진행 중인 업로드 수
        let stopTime = null;    // 녹화 중지 시간 기록
        let mergeResolve = null; // 병합 완료 대기용 Promise
        let currentBitrate = 5000000; // 가변 비트레이트 초기값: 5Mbps (최대화질)
        let consecutiveOptimalUploads = 0; // 연속으로 빠르고 재시도 없이 성공한 횟수
        let isIntentionalNavigation = false; // 안전한 페이지 이동 상태 플래그
        let wakeLock = null;     // 화면 꺼짐 방지 객체

        // 화면 꺼짐 방지(Wake Lock) 요청
        async function requestWakeLock() {
            if ('wakeLock' in navigator) {
                try {
                    wakeLock = await navigator.wakeLock.request('screen');
                    console.log('화면 꺼짐 방지(Wake Lock) 활성화');
                } catch (err) {
                    console.error(`Wake Lock 오류: ${err.name}, ${err.message}`);
                }
            } else {
                console.warn('이 브라우저는 Wake Lock API를 지원하지 않습니다.');
            }
        }

        // 화면 꺼짐 방지(Wake Lock) 해제
        function releaseWakeLock() {
            if (wakeLock !== null) {
                wakeLock.release().then(() => {
                    wakeLock = null;
                    console.log('화면 꺼짐 방지(Wake Lock) 해제');
                });
            }
        }

        // 탭 전환 등으로 화면이 숨겨졌다가 다시 나타날 때 Wake Lock 재적용
        document.addEventListener('visibilitychange', async () => {
            if (document.visibilityState === 'visible' && isRecording) {
                await requestWakeLock();
            }
        });

        // 1. 사용 가능한 카메라 장치 목록 가져오기
        async function getCameras() {
            try {
                const devices = await navigator.mediaDevices.enumerateDevices();
                const videoDevices = devices.filter(device => device.kind === 'videoinput');
                
                videoSelect.innerHTML = ''; // 장치 목록 초기화

                videoDevices.forEach(device => {
                    const option = document.createElement('option');
                    option.value = device.deviceId;
                    option.text = device.label || `Camera ${videoSelect.length + 1}`;
                    videoSelect.appendChild(option);
                });
            } catch (e) {
                console.error('카메라 장치를 가져오는 데 실패했습니다:', e);
            }
        }

        // 2. 선택된 카메라의 영상 스트림 시작
        async function startVideo() {
            if (stream) {
                stream.getTracks().forEach(track => track.stop());
            }
            const deviceId = videoSelect.value;
            const constraints = {
            video: { 
                deviceId: deviceId ? { exact: deviceId } : undefined,
                width: { ideal: 1920 }, // Full HD 해상도 가로 픽셀 요청
                height: { ideal: 1080 } // Full HD 해상도 세로 픽셀 요청
            }
            };

            try {
                stream = await navigator.mediaDevices.getUserMedia(constraints);
                videoElement.srcObject = stream;
            } catch (e) {
                console.error('카메라 스트림에 접근할 수 없습니다:', e);
                alert('카메라에 접근할 수 없습니다. 권한을 확인해주세요.');
            }
        }

        // 3. 녹화 시작/중지 및 파일 생성 로직
        function startRecordingSegment() {
            if (!stream) {
                alert('카메라 스트림이 시작되지 않았습니다.');
                return;
            }
            
            // 이전 녹화가 있다면 중지하고 파일 생성
            if (mediaRecorder && mediaRecorder.state === 'recording') {
                mediaRecorder.stop();
            }

            // 녹화가 중지되었다면(isRecording=false) 새 세그먼트를 시작하지 않습니다.
            if (!isRecording) return;

            const segmentChunks = []; // 각 세그먼트마다 독립적인 배열을 사용합니다.
            const recorder = new MediaRecorder(stream, {
                videoBitsPerSecond: currentBitrate // 네트워크 상태에 따른 유동적인 비트레이트 적용
            });
            mediaRecorder = recorder; // 전역 레코더 참조 업데이트

            recorder.ondataavailable = event => {
                if (event.data.size > 0) {
                    segmentChunks.push(event.data);
                }
            };

            recorder.onstop = () => {
                if (segmentChunks.length === 0) {
                    checkAndMerge();
                    return;
                }

                const blob = new Blob(segmentChunks, { type: 'video/webm' });
                segmentChunks.length = 0; // 배열 명시적 초기화 (RAM 메모리 즉시 회수 힌트)
                uploadSegmentWithRetry(blob, currentRecordingId, segmentCounter++);
            };

            recorder.start();
            console.log('30초 녹화 세그먼트 시작:', new Date());
        }

        function uploadSegmentWithRetry(blob, recordingId, counter) {
            activeUploads++;

            // 업로드 상태를 표시할 p 태그를 미리 생성합니다.
            const p = document.createElement('p');
            p.textContent = `[${new Date().toLocaleTimeString()}] 세그먼트 ${counter} 업로드 시작...`;
            p.style.fontStyle = 'italic';
            recordedList.prepend(p);

            const attempt = (retryCount) => {
                // 재시도할 때마다 FormData를 새로 생성하여 메모리 누수 방지
                const formData = new FormData();
                formData.append('video', blob, 'segment.webm');
                formData.append('recordingId', recordingId);
                formData.append('segmentCounter', counter);

                const fetchStartTime = Date.now(); // 각 시도별 실제 소요 시간 측정용
                fetch('uploadSegment', {
                    method: 'POST',
                    body: formData
                })
                .then(response => {
                    if (!response.ok) throw new Error(`HTTP 오류: ${response.status}`);
                    return response.json();
                })
                .then(data => {
                    if(data.error) throw new Error(data.error);
                    
                    // 네트워크 상태 기반 화질(비트레이트) 자동 조절 로직
                    const uploadDuration = (Date.now() - fetchStartTime) / 1000; // 마지막 시도의 실제 업로드 소요 시간(초)

                    if (retryCount >= 2) {
                        // 2회 이상 재시도하여 성공한 경우 (catch 블록에서 이미 화질을 내렸으므로 유지)
                        consecutiveOptimalUploads = 0;
                    } else if (uploadDuration > 25) { 
                        // 단일 업로드 시도에 25초 이상 걸림 (네트워크 느림 -> 화질 하향)
                        // 신호등 색, 차선 식별을 위해 최소 2Mbps(2000000 bps)는 방어선으로 유지
                        currentBitrate = Math.max(2000000, currentBitrate - 1000000);
                        consecutiveOptimalUploads = 0;
                    } else if (retryCount === 0 && uploadDuration < 15) {
                        // 재시도 없이 한 번에 성공하고, 시간도 15초 미만 (네트워크 매우 원활 -> 연속 성공 카운트 증가)
                        consecutiveOptimalUploads++;
                        if (consecutiveOptimalUploads >= 2) {
                            currentBitrate = Math.min(5000000, currentBitrate + 1000000);
                            consecutiveOptimalUploads = 0; // 화질 상향 후 카운트 초기화
                        }
                    } else {
                        // 재시도를 1번 했거나, 시간이 15~25초 걸린 경우 (보통 상태)
                        consecutiveOptimalUploads = 0;
                    }

                    // 완료 상태 업데이트 및 현재 비트레이트 상태 표기
                    p.textContent = `[${new Date().toLocaleTimeString()}] 세그먼트 ${counter} 업로드 완료 (소요시간: ${uploadDuration.toFixed(1)}초, 재시도: ${retryCount}회, 적용 화질: ${(currentBitrate/1000000).toFixed(1)}Mbps)`;
                    p.style.fontStyle = 'normal';
                    
                    activeUploads--;
                    blob = null; // 업로드 성공 시 Blob 참조 강제 해제 (디스크 임시 파일 즉시 삭제 유도)
                    checkAndMerge();
                })
                .catch(error => {
                    let timeSinceStop = (isStopping && stopTime) ? (Date.now() - stopTime) : 0;
                    
                    // 정지 후 1분 초과 시 무시. (업로드 대기 중(진행 중)이거나 setTimeout 중일 때는 여기까지 안 오므로 조건 충족)
                    if (isStopping && timeSinceStop > 60000) {
                        p.textContent = `[${new Date().toLocaleTimeString()}] 세그먼트 ${counter} 업로드 실패 후 무시됨 (정지 후 1분 초과): ${error.message}`;
                        p.style.color = 'red';
                        p.style.fontStyle = 'normal';
                        
                        activeUploads--;
                        blob = null; // 업로드 영구 포기 시에도 참조 강제 해제
                        checkAndMerge();
                        return;
                    }

                    // 2회 연속 실패 시(최초 실패 후 1번 재시도마저 실패) 즉시 화질 하향
                    if (retryCount === 1) {
                        currentBitrate = Math.max(2000000, currentBitrate - 1000000);
                        consecutiveOptimalUploads = 0;
                    }

                    // 지수 백오프: 2^retryCount * 1000ms, 최대 120초
                    const delay = Math.min(1000 * Math.pow(2, retryCount), 120000);
                    // p 태그 내용을 '재시도' 상태로 업데이트합니다.
                    p.textContent = `[${new Date().toLocaleTimeString()}] 세그먼트 ${counter} 업로드 실패(${retryCount + 1}회), ${delay/1000}초 후 재시도...`;
                    p.style.color = 'orange';
                    
                    setTimeout(() => attempt(retryCount + 1), delay);
                });
            };
            attempt(0);
        }

        function checkAndMerge() {
            if (isStopping && activeUploads === 0) {
                sendMergeRequest();
                isStopping = false;
                if (mergeResolve) {
                    mergeResolve();
                    mergeResolve = null;
                }
            }
        }

        function stopRecordingAndNotifyServer() {
            return new Promise((resolve) => {
                if (recordingInterval) {
                    clearInterval(recordingInterval);
                    recordingInterval = null;
                }
                isStopping = true; // 병합 플래그 켜기
                stopTime = Date.now(); // 정지 시간 기록
                mergeResolve = resolve; // 완료 시 호출될 콜백 저장
                if (mediaRecorder && mediaRecorder.state === 'recording') {
                    mediaRecorder.stop(); // onstop 트리거 -> uploadSegmentWithRetry 시작 -> checkAndMerge
                } else {
                    checkAndMerge(); // 녹화 중이 아니면 즉시 확인
                }
            });
        }

        // 실제 서버에 병합을 요청하는 함수 분리
        function sendMergeRequest() {
            const formData = new FormData();
            formData.append('recordingId', currentRecordingId);
            navigator.sendBeacon('stopRecording', formData);
            console.log('마지막 세그먼트 업로드가 끝나고 서버에 병합을 요청했습니다.');
        }

        toggleBtn.onclick = async () => {
            if (isRecording) {
                // 녹화 중지 로직
                isRecording = false; // 플래그를 먼저 설정하여 경합 상태를 방지합니다.
                toggleBtn.disabled = true;
                toggleBtn.textContent = '저장 중...';
                
                await stopRecordingAndNotifyServer();
                
                releaseWakeLock(); // 녹화 중지 시 화면 꺼짐 방지 해제
                toggleBtn.classList.remove('recording');
                toggleBtn.textContent = '녹화 시작';
                toggleBtn.disabled = false;
            } else {
                // 녹화 시작 로직
                if (!stream) {
                    alert('카메라 스트림이 준비되지 않았습니다. 잠시 후 다시 시도해주세요.');
                    return;
                }
                isRecording = true;
                toggleBtn.classList.add('recording');
                toggleBtn.textContent = '녹화 중지';

                currentRecordingId = crypto.randomUUID(); // 새 녹화 세션마다 고유 ID 생성
                segmentCounter = 1;

                requestWakeLock(); // 녹화 시작 시 화면 꺼짐 방지 활성화
                startRecordingSegment(); // 즉시 첫 녹화 시작
                recordingInterval = setInterval(startRecordingSegment, 30000);
                console.log("녹화를 시작합니다.");
            }
        }

        // 페이지 로드 시 초기화
        async function init() {
            // 1. HTTPS 접속 확인 (모바일 브라우저는 HTTP 환경에서 카메라 접근을 완전히 차단합니다)
            if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                alert('카메라 접근을 지원하지 않는 브라우저이거나, HTTP 환경입니다.\n모바일에서는 반드시 HTTPS(또는 localhost)로 접속해야 합니다.');
                return;
            }

            try {
                // 2. 모바일 권한 팝업 유도: 장치 목록(enumerateDevices)을 부르기 전에 먼저 영상부터 요청해야 팝업이 뜹니다.
                stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
                
                await getCameras(); // 권한을 획득했으므로 카메라 이름(Label)도 정상적으로 가져옵니다.
                
                // 3. 로컬 스토리지에서 마지막으로 사용한 카메라 ID 불러오기
                const savedCameraId = localStorage.getItem('lastCameraId');
                
                // 저장된 카메라가 현재 연결된 장치 목록에 실제로 존재하는지 검증
                const isCameraAvailable = Array.from(videoSelect.options).some(opt => opt.value === savedCameraId);

                if (savedCameraId && isCameraAvailable) {
                    videoSelect.value = savedCameraId;
                    await startVideo(); // 선택된 장치 ID를 이용해 스트림 재시작
                    
                    if (stream && !isRecording) {
                        toggleBtn.click();
                    }
                } else {
                    // 처음 방문이거나, 이전에 썼던 카메라가 뽑혀있는 경우 알림(모달) 띄우기
                    // 기존 권한 획득용 임시 스트림을 종료하여 카메라 장치 사용 충돌 방지
                    if (stream) {
                        stream.getTracks().forEach(track => track.stop());
                        stream = null;
                    }

                    const overlay = document.getElementById('cameraAlertOverlay');
                    const alertSelect = document.getElementById('alertCameraSelect');
                    const alertBtn = document.getElementById('alertCameraBtn');
                    const alertPreview = document.getElementById('alertPreviewVideo');
                    
                    // 메인 선택 박스의 옵션들을 알림창으로 복사
                    alertSelect.innerHTML = videoSelect.innerHTML;
                    overlay.style.display = 'flex';
                    
                    let previewStream = null;
                    // 선택한 카메라의 미리보기 스트림을 띄우는 함수
                    const updatePreview = async () => {
                        if (previewStream) previewStream.getTracks().forEach(t => t.stop());
                        try {
                            previewStream = await navigator.mediaDevices.getUserMedia({
                                video: { deviceId: alertSelect.value ? { exact: alertSelect.value } : undefined }
                            });
                            alertPreview.srcObject = previewStream;
                        } catch (e) {
                            console.error('미리보기 스트림 오류:', e);
                        }
                    };
                    updatePreview(); // 모달이 켜질 때 최초 미리보기 실행
                    alertSelect.onchange = updatePreview; // 드롭다운 변경 시 미리보기 업데이트

                    alertBtn.onclick = async () => {
                        if (previewStream) previewStream.getTracks().forEach(t => t.stop()); // 미리보기 스트림 완전 종료
                        
                        const selectedId = alertSelect.value;
                        localStorage.setItem('lastCameraId', selectedId); // 선택한 카메라 내부 저장소에 기억
                        videoSelect.value = selectedId;
                        overlay.style.display = 'none';
                        
                        await startVideo();
                        if (stream && !isRecording) {
                            toggleBtn.click();
                        }
                    };
                }
            } catch (e) {
                console.error('카메라 권한 오류:', e);
                alert('카메라 권한이 거부되었거나 장치를 찾을 수 없습니다.\n브라우저 설정(사이트 설정)에서 카메라 권한을 "허용"으로 변경한 뒤 새로고침해주세요.');
            }
        }

        // '로그아웃' 등 페이지 내의 링크(a 태그)를 클릭했을 때의 안전한 종료 처리
        document.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', async (e) => {
                if (isRecording || activeUploads > 0) {
                    e.preventDefault(); // 즉시 페이지 이동 차단
                    const targetHref = link.href;
                    
                    // 화면 전체를 가리는 로딩 오버레이 생성
                    const overlay = document.createElement('div');
                    overlay.style.cssText = 'position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.8); color:white; display:flex; align-items:center; justify-content:center; z-index:9999; font-size:20px; font-weight:bold; text-align:center; line-height:1.6; flex-direction:column;';
                    overlay.innerHTML = '<span>마지막 영상을 서버에 안전하게 저장 중입니다...</span><br><span style="font-size:16px; color:#ccc;">잠시만 기다려주세요. 창을 닫지 마세요.</span>';
                    document.body.appendChild(overlay);

                    if (isRecording) {
                        isRecording = false; // 플래그를 먼저 설정합니다.
                        toggleBtn.disabled = true;
                        await stopRecordingAndNotifyServer();
                    } else if (activeUploads > 0) {
                        // 이미 중지 버튼을 눌렀으나 업로드가 남은 경우 대기
                        await new Promise(resolve => {
                            const checkInterval = setInterval(() => {
                                if (activeUploads === 0) {
                                    clearInterval(checkInterval);
                                    resolve();
                                }
                            }, 500);
                        });
                    }
                    isIntentionalNavigation = true; // 안전한 이동이므로 경고창 우회 플래그 켜기
                    window.location.href = targetHref; // 업로드 완료 후 원래 누르려던 링크로 이동
                }
            });
        });

        // 브라우저 탭 닫기, 뒤로가기 등 시스템적인 페이지 이탈 시 경고
        window.addEventListener('beforeunload', (event) => {
            if (!isIntentionalNavigation && (isRecording || activeUploads > 0)) { 
                // 대용량 파일 업로드는 unload 시 강제 취소되므로 시스템 경고 창을 띄움
                event.preventDefault();
                event.returnValue = '마지막 영상이 아직 서버에 업로드되지 않았습니다. 창을 닫으면 유실될 수 있습니다.';
            }
        });

        // 카메라(Select Box)를 직접 변경할 때도 마지막 카메라를 내부 저장소에 업데이트
        videoSelect.addEventListener('change', async () => {
            localStorage.setItem('lastCameraId', videoSelect.value);
            await startVideo();
        });

        init();
    </script>
</body>
</html>