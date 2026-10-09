# 개발·실행 기록

LG AX · 키친에서 치킨먹기 / 황대겸

## 2026-10-10 · 초기 앱과 펌웨어 빌드

환경: GitHub Actions Linux 실행 환경, JDK 17, ESP32 Arduino 코어 3.1.3, DHT sensor library 1.4.6.

대상 코드: `7371e18` 시점. [실행 결과](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/37987982977).

| 실행한 것 | 결과 |
| --- | --- |
| `./gradlew assembleDebug lintDebug --no-daemon` | 성공, APK 생성과 lint 완료 |
| `arduino-cli compile --fqbn esp32:esp32:esp32 firmware/dish_dry_sensor` | 성공 |
| ESP32 프로그램 크기 | 943328 bytes, 71% |
| ESP32 전역 변수 메모리 | 46132 bytes, 14% |

[원문 출력 발췌](logs/2026-10-10-build.txt). 로그의 시간은 UTC이며, 한국 시각으로 2026-10-10 오전 실행이다.

## 2026-10-10 · 프론트 v0.1.1 작업

- 연결, 건조 지수, 외부 센서, 휴대폰 센서, 기록을 카드로 배치했다.
- CSV 기록 개수와 최근 기록 시각을 앱을 다시 열었을 때도 읽어 표시한다.
- Android 8 테마 검사에서 나온 내비게이션 바 설정 문제를 버전별 리소스로 수정했다.
- 앱을 에뮬레이터에 설치하고 연결 대기 화면의 실제 실행 캡처를 확보했다.
- 센서값이 같을 때도 반복되던 텍스트 갱신을 줄이고, 화면 확인 도구가 오래된 덤프를 읽지 않도록 수정했다.

## 2026-10-10 · Android 앱 실제 실행

대상 코드: `6f46a75`, v0.1.1. 환경: Android API 30 에뮬레이터, Pixel 2 화면 프로필. [실행 결과](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/37989622824).

| 확인한 동작 | 결과 |
| --- | --- |
| APK 설치와 앱 시작 | 성공 |
| 측정 시작과 버튼 상태 전환 | 성공 |
| 가상 휴대폰 센서 값으로 현재 값 기록 | 성공, 내부 CSV 1행 추가 |
| CSV 파일 선택기 열기·저장 버튼·앱 복귀 | 성공 |
| 앱 강제 종료 후 다시 실행 | 기록 1개와 최근 기록 시각 유지 |
| 측정 중지 상태에서 기록 버튼 | 비활성화 확인 |
| Android Runtime 치명적 예외 | 없음 |

[원본 실행 화면](images/screenshots), [원문 결과와 내부 CSV](logs/2026-10-10-android-emulator.txt).

이 실행의 휴대폰 센서는 에뮬레이터의 가상 센서다. 외부 온습도·빛 센서는 연결하지 않아 CSV의 해당 열은 비어 있다. CSV의 `recorded_at_utc`는 UTC이고, 앱의 최근 기록 표시는 실행 기기의 시간대를 따른다.

## 실물 ESP32 실행 기록 · 확인 대기

현재 확보한 기록은 펌웨어 컴파일이다. 보드 업로드, Serial 출력과 DHT22·LDR 실물 측정은 아직 진행하지 않았다.

실물 확인을 할 때 아래 항목을 원문과 함께 추가한다.

| 기록 항목 | 현재 상태 |
| --- | --- |
| 확인 날짜·사용 보드 | 확인 대기 |
| 업로드한 펌웨어 커밋 | 확인 대기 |
| USB 포트·업로드 결과 | 확인 대기 |
| Serial Monitor, 115200 baud 출력 | 아직 없음 |
| 개발용 Wi-Fi 접속 결과 | 확인 대기 |
| `GET /readings` 응답 원문 | 아직 없음 |
| DHT22 온습도 측정 | 확인 대기 |
| LDR 밝기 변화에 따른 ADC 변화 | 확인 대기 |
| 센서 선을 뺐을 때 HTTP 503 응답 | 확인 대기 |

실물 확인 순서는 배선 → 펌웨어 업로드 → Serial Monitor → Wi-Fi 연결 → HTTP 응답 → 앱 측정 → CSV 저장으로 잡는다. 화면의 건조 지수와 접시의 실제 물기는 별도로 비교해야 한다.
