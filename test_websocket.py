#!/usr/bin/env python3
"""
웹소켓 연결 테스트 스크립트
"""
import socket
import base64
import hashlib
import time
import json

def test_websocket(host, port, path):
    """웹소켓 연결 테스트"""
    print(f"🔌 웹소켓 연결 테스트: ws://{host}:{port}{path}")
    
    try:
        # TCP 소켓 생성
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(10)
        
        # 서버 연결
        print(f"📡 {host}:{port}에 연결 시도 중...")
        sock.connect((host, port))
        print("✅ TCP 연결 성공!")
        
        # 웹소켓 핸드셰이크 키 생성
        key = base64.b64encode(hashlib.sha1(f"{int(time.time())}".encode()).digest()).decode()
        
        # 웹소켓 업그레이드 요청
        request = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {host}:{port}\r\n"
            f"Upgrade: websocket\r\n"
            f"Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            f"Sec-WebSocket-Version: 13\r\n"
            f"Origin: http://{host}:{port}\r\n"
            f"\r\n"
        )
        
        print("\n📤 웹소켓 핸드셰이크 요청 전송...")
        sock.send(request.encode())
        
        # 응답 받기
        response = sock.recv(4096).decode()
        print("\n📥 서버 응답:")
        print(response)
        
        # HTTP 101 Switching Protocols 확인
        if "101 Switching Protocols" in response:
            print("\n✅ 웹소켓 연결 성공! (HTTP 101)")
            print("🎉 웹소켓이 정상적으로 작동합니다!")
            
            # 잠시 대기 후 메시지 수신 시도
            print("\n⏳ 3초간 메시지 대기 중...")
            sock.settimeout(3)
            try:
                data = sock.recv(4096)
                if data:
                    print(f"📨 메시지 수신: {len(data)} bytes")
            except socket.timeout:
                print("ℹ️  메시지 없음 (정상 - 서버가 데이터를 보낼 때까지 대기)")
            
            sock.close()
            return True
        else:
            print("\n❌ 웹소켓 업그레이드 실패")
            print("응답이 HTTP 101이 아닙니다.")
            sock.close()
            return False
            
    except socket.timeout:
        print("❌ 연결 시간 초과")
        return False
    except ConnectionRefusedError:
        print("❌ 연결 거부됨 - 서버가 실행 중이지 않거나 포트가 닫혀있습니다")
        return False
    except Exception as e:
        print(f"❌ 오류 발생: {e}")
        return False

if __name__ == "__main__":
    host = "54.86.161.187"
    port = 8080
    path = "/ws/fsr-data"
    
    print("=" * 60)
    print("웹소켓 연결 테스트")
    print("=" * 60)
    
    success = test_websocket(host, port, path)
    
    print("\n" + "=" * 60)
    if success:
        print("✅ 테스트 성공!")
    else:
        print("❌ 테스트 실패")
    print("=" * 60)

