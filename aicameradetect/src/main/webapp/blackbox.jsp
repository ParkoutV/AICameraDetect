<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<% String userId = (String) session.getAttribute("userId"); %>
<!DOCTYPE html> 
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>블랙박스 제어 - AI Camera Detect</title>
    <style>
        body { font-family: sans-serif; }
        .container { max-width: 800px; margin: 20px auto; padding: 20px; border: 1px solid #ccc; border-radius: 8px; }
        h1, h2 { text-align: center; }
        video {
            width: 100%;
            background-color: #000;
            border: 1px solid #ddd;
            margin-top: 15px;
        }
        .controls {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin: 20px 0;
        }
        .controls label { font-weight: bold; }
        .controls select { padding: 5px; }
        .buttons button {
            padding: 10px 20px;
            font-size: 16px;
            cursor: pointer;
            border: none;
            border-radius: 5px;
            color: white;
        }
        #startBtn { background-color: #28a745; }
        #startBtn:disabled { background-color: #999; }
        #stopBtn { background-color: #dc3545; }
        #stopBtn:disabled { background-color: #999; }
        #recordedList a {
            display: block;
            padding: 8px;
            margin-top: 5px;
            background-color: #f0f0f0;
            text-decoration: none;
            color: #333;
            border-radius: 4px;
        }
        #recordedList a:hover { background-color: #e0e0e0; }
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
            <button id="startBtn">녹화 시작</button>
            <button id="stopBtn" disabled>녹화 중지</button>
        </div>

        <h2>업로드된 영상 조각 로그</h2>
        <div id="recordedList"></div>

        <hr style="margin-top: 30px;">
        <a href="index.jsp" style="display: block; text-align: center;">로그아웃 (메인으로 돌아가기)</a>
    </div>

    <script>
        const videoSelect = document.getElementById('videoSource');
        const videoElement = document.getElementById('video');
        const startBtn = document.getElementById('startBtn');
        const stopBtn = document.getElementById('stopBtn');
        const recordedList = document.getElementById('recordedList');

        let mediaRecorder;
        let recordedChunks = [];
        let stream;
        let recordingInterval;

        // 1. 사용 가능한 카메라 장치 목록 가져오기
        async function getCameras() {
            try {
                const devices = await navigator.mediaDevices.enumerateDevices();
                const videoDevices = devices.filter(device => device.kind === 'videoinput');
                
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
                video: { deviceId: deviceId ? { exact: deviceId } : undefined }
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

            recordedChunks = [];
            mediaRecorder = new MediaRecorder(stream);

            mediaRecorder.ondataavailable = event => {
                if (event.data.size > 0) {
                    recordedChunks.push(event.data);
                }
            };

            mediaRecorder.onstop = () => {
                if (recordedChunks.length === 0) return;

                const blob = new Blob(recordedChunks, { type: 'video/webm' });
                const formData = new FormData();
                formData.append('video', blob, 'segment.webm');

                // 서버로 비동기 전송
                fetch('uploadSegment', {
                    method: 'POST',
                    body: formData
                })
                .then(response => response.json())
                .then(data => {
                    if(data.error) throw new Error(data.error);
                    console.log('Upload success:', data);
                    const p = document.createElement('p');
                    p.textContent = `[${new Date().toLocaleTimeString()}] 세그먼트 업로드 완료: ${data.fileName}`;
                    recordedList.prepend(p);
                })
                .catch(error => {
                    console.error('Upload error:', error);
                    const p = document.createElement('p');
                    p.textContent = `[${new Date().toLocaleTimeString()}] 업로드 실패: ${error.message}`;
                    p.style.color = 'red';
                    recordedList.prepend(p);
                });
            };

            mediaRecorder.start();
            console.log('30초 녹화 세그먼트 시작:', new Date());
        }

        startBtn.onclick = () => {
            // 수동 시작은 막고, 자동 시작만 허용
        };

        function stopRecordingAndNotifyServer() {
            if (recordingInterval) {
                clearInterval(recordingInterval);
                recordingInterval = null;
            }
            if (mediaRecorder && mediaRecorder.state === 'recording') {
                mediaRecorder.stop(); // 마지막 녹화분 저장 및 업로드
            }
            
            // navigator.sendBeacon은 페이지를 떠날 때 안정적으로 데이터를 보내는 API
            navigator.sendBeacon('stopRecording', new Blob());
            console.log('녹화가 중지되었으며 서버에 병합을 요청했습니다.');
        }

        stopBtn.onclick = () => {
            startBtn.disabled = false;
            stopBtn.disabled = true;
            stopRecordingAndNotifyServer();
        };

        function startAutoRecording() {
            if (!stream) {
                setTimeout(startAutoRecording, 1000); // 스트림 준비될 때까지 1초 대기
                return;
            }
            startBtn.disabled = true;
            stopBtn.disabled = false;
            
            startRecordingSegment(); // 즉시 첫 녹화 시작
            // 30초마다 새로운 녹화 세그먼트 시작
            recordingInterval = setInterval(startRecordingSegment, 30000);
            console.log("자동 녹화를 시작합니다.");
        }

        // 페이지 로드 시 초기화
        async function init() {
            await getCameras();
            await startVideo();
            startAutoRecording(); // 자동 녹화 시작
        }

        videoSelect.onchange = startVideo;
        window.addEventListener('beforeunload', () => {
            if (!stopBtn.disabled) { // 녹화가 진행중일 때만
                stopRecordingAndNotifyServer();
            }
        });

        init();
    </script>
</body>
</html>