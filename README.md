# 🐿️ SquirFit - 스마트인솔과 Mediapipe를 활용한 실시간 자세 분석 서비스 🏃‍♂️‍➡️

영상 분석과 족저압 분석을 통합하여 실시간 자세 분석 피드백을 제공하는 피트니스 앱

[![App Store](https://img.shields.io/badge/App_Store-0D96F6?style=for-the-badge&logo=app-store&logoColor=white)](https://apps.apple.com/kr/app/squirfit-%EB%8B%A4%EB%9E%8C%EC%9D%B4%EC%99%80-%ED%95%A8%EA%BB%98-%EC%9A%B4%EB%8F%99%EC%9D%84/id6746436823)
[![YouTube](https://img.shields.io/badge/YouTube-FF0000?style=for-the-badge&logo=youtube&logoColor=white)](https://youtu.be/GBPyvMfTekw?si=384V0t_OB3BXtuuV)
<br/><br/>

<!-- 공통 컨테이너 -->
<div style="max-width: 1200px; margin: 0 auto;">
  <div style="display: flex; justify-content: space-between;">
    <img src="https://github.com/user-attachments/assets/4bda310b-9042-45f3-b81f-2842bbe40173" width="24%" />
    <img src="https://github.com/user-attachments/assets/0eb135f4-a898-41a2-848a-0d8c9fb36c33" width="24%" />
    <img src="https://github.com/user-attachments/assets/4e27fc03-e5b9-4f1b-806f-ffcd5bfa5cc3" width="24%" />
    <img src="https://github.com/user-attachments/assets/8b287bb0-5fd0-44d5-902e-958ccc32af70" width="24%" />
  </div>
  <br/>
  <img 
    src="https://github.com/user-attachments/assets/c27b2927-1b98-4e5c-95e8-6ae576fb9be6"
    style="width: 100%;"
  />
</div>

<br/>

## 👟 서비스 개요

- 최근 개인 건강 관리에 대한 관심이 빠르게 증가하고 있고, 홈 트레이닝의 대중화로 디지털 피트니스 시장 역시 지속적인 성장을 보이고 있음
- 그러나 기존 피트니스 앱들은 순간적인 자세 분석 정보만 제공하거나, 운동 자세에 대한 텍스트/영상 설명만 제공하고 있어 실시간으로 사용자가 동작을 교정하며 운동할 수 있는 기능은 부족함
- 이러한 문제를 해결하기 위해, Mediapipe를 사용한 비전 AI로 전신 관절 분석과 스마트 인솔을 통한 족저압 분석을 통합하여, 기존보다 정확하고 개인화된 실시간 자세 피드백을 제공하는 서비스를 개발함
<br/>

## ✨ 주요 기능 설명

- 📷 카메라 기반 실시간 자세 분석 및 스켈레톤 출력
- 🦶 스마트 인솔을 활용한 부위별 압력 분포 출력
- 💬 10초마다 자세에 대한 음성 및 시각적 피드백 제공
- 🔢 운동 시 자동 횟수/세트 카운팅
- 📝 운동 완료 후 결과 데이터 확인
<br/>

<table>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/0c0c9c34-fe40-46d3-9f60-8c980f314c8d" width="100%" />
      <br/>
      <strong>앱 시작화면</strong>
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/c3ef2c24-2355-4144-8f5f-67a7dda79e13" width="100%" />
      <br/>
      <strong>운동 진입 화면</strong>
    </td>
    <td align="center" style="display: flex; flex-direction: column; height: 100%;">
  <img
    src="https://github.com/user-attachments/assets/7dc262d4-01ba-4527-8c58-957b450ef4c6"
    width="100%"
  />
  <div style="margin-top: auto;">
    <strong>운동 중 화면</strong>
  </div>
</td>

</td>
  </tr>

  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/7497d328-e52d-4637-8c2a-264fb5de81c1" width="100%" />
      <br/>
      <strong>운동 완료 화면</strong>
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/1e637549-9b0e-400b-bb31-ace22ea19f7e" width="100%" />
      <br/>
      <strong>운동 시작 화면</strong>
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/477963b0-64c8-48ab-a202-69ba0d6a767f" width="100%" />
      <br/>
      <strong>설정 화면</strong>
    </td>
  </tr>
</table>

<br/>

## 🛠️ 기술 스택

- iOS: Swift, SwiftUI, AVFoundation, Combine, Websocket
- Server: SpringBoot, Java17, MySQL 8.0, Spring Data JPA, Websocket, Docker, Docker Compose
- AI: Google Mediapipe, FastAPI, Websocket, Azure App Service, Github Actions
- Embedded: ESP32 DevKit WROOM-32U * 2, FSR400 * 6, Arduino
<br/>

<img width="2048" height="1147" alt="image" src="https://github.com/user-attachments/assets/facb6e04-66bb-4220-97af-741efa9454e6" />

<br/><br/>

## 👩‍💻 팀원 구성

<table>
  <tr align="center">
        <td><strong>임베디드</strong></td>
        <td><strong>백엔드</strong></td>
        <td><strong>iOS</strong></td>
        <td><strong>AI</strong></td>
    </tr>
    <tr align="center">
        <td><B>박수정</B></td>
        <td><B>전주은</B></td>
        <td><B>이가은</B></td>
        <td><B>곽윤지</B></td>
    </tr>
    <tr align="center">
        <td>
            <img src="https://avatars.githubusercontent.com/u/80205505?v=4?size=100" width="100">
            <br>
            <a href="https://github.com/qkrtnwjd4212/Squirret_Embedded">qkrtnwjd4212</a>
        </td>
        <td>
            <img src="https://avatars.githubusercontent.com/u/150149986?v=4?size=100" width="100">
            <br>
            <a href="https://github.com/qkrtnwjd4212/Squirret_Backend">jeon-jueun</a>
        </td>
        <td>
            <img src="https://avatars.githubusercontent.com/u/82457928?v=4?size=100" width="100">
            <br>
            <a href="https://github.com/rriver2/SquirFit">rriver2</a>
        </td>
        <td>
            <img src="https://avatars.githubusercontent.com/u/83887485?v=4?size=100" width="100">
            <br>
            <a href="https://github.com/yunjikwak/capston-design-mpp">yunjikwak</a>
        </td>
    </tr>
</table>
