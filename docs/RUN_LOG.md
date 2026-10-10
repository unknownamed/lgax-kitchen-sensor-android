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

[v0.1.1 원본 실행 화면](https://github.com/unknownamed/lgax-kitchen-sensor-android/tree/c8658311ec562a878ed4f9056f6cadeb715f4f9c/docs/images/screenshots), [원문 결과와 내부 CSV](logs/2026-10-10-android-emulator.txt).

이 실행의 휴대폰 센서는 에뮬레이터의 가상 센서다. 외부 온습도·빛 센서는 연결하지 않아 CSV의 해당 열은 비어 있다. CSV의 `recorded_at_utc`는 UTC이고, 앱의 최근 기록 표시는 실행 기기의 시간대를 따른다.

## 2026-10-10 · v0.2.0 디자인과 코드 정리

- Apple 웹디자인 분석 DESIGN.md를 받고 MIT 라이선스와 출처를 보관했다.
- 흰색·연회색 표면, 큰 지수, 파란 캡슐 버튼과 한 화면의 세로 카드 구성으로 변경했다.
- 화면 표시, 센서 등록·폴링, HTTP 요청, 값 유효 기간, CSV 파일 작업을 분리했다.
- CSV 요약 읽기·쓰기·복사를 별도 스레드에서 처리한다. 파일 길이가 바뀌지 않았다면 요약을 다시 읽지 않는다.
- `sample_age_ms`와 앱 수신 뒤 경과 시간을 함께 적용해 오래된 외부 값이 기록되지 않도록 수정했다.
- 구성 그림, 시나리오와 와이어프레임도 v0.2.0 화면에 맞춰 교체했다.
- 시스템 파일 선택기의 로딩 화면이 캡처되던 타이밍을 수정했다. 실제 저장 버튼이 나타난 것을 확인한 뒤 원본 화면을 캡처한다.

대상 코드: `fafc08b`, v0.2.0. [빌드 결과](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/37993503219), [에뮬레이터 실행 결과](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/37993503217).

| 확인한 항목 | 결과 |
| --- | --- |
| Gradle 단위 테스트 | 15개 성공, 실패·오류·생략 0개 |
| Android APK 빌드와 lint | 성공 |
| ESP32 펌웨어 컴파일 | 성공, 943328 bytes / 전역 변수 46132 bytes |
| 측정 시작과 가상 센서 값 기록 | 성공, CSV 한 행 추가 |
| 시스템 파일 선택기에서 CSV 저장 | 성공, 내보낸 실제 파일과 내부 원본 내용 일치 |
| 시스템 파일 선택기 취소 | 내부 기록 유지 |
| 앱 재시작 | 기록 개수·최근 기록 시각 유지, 측정 중지 상태 |
| 320dp 너비와 130% 글자 크기 | 기록·내보내기 버튼 표시와 접근 확인 |
| Android Runtime 치명적 예외 | 없음 |

단위 테스트는 외부 값 만료, 측정 중지·재시작 시 값 초기화, 비정상 휴대폰 값 제외, 주소 검사, CSV 빈 필드·소수점·원본 유지·손상 행 처리·요약 갱신을 확인한다. 첫 테스트 실행에서 지수 반올림 예시의 기대값을 잘못 적은 항목 하나가 실패했으며, 계산 결과 57로 고친 뒤 모두 통과했다.

[원문 빌드·테스트 출력](logs/2026-10-10-v0.2.0-checks.txt), [원문 실행 결과와 내부 CSV](logs/2026-10-10-v0.2.0-android-emulator.txt), [당시 실행 캡처 8장](https://github.com/unknownamed/lgax-kitchen-sensor-android/tree/0141018/docs/images/screenshots). 화면은 APK 원본이며 구성 그림·시나리오와 구분한다. 센서 기록은 에뮬레이터의 가상 움직임 값이다. 실물 ESP32는 연결하지 않았다.

## 2026-10-10 · v0.2.1 연결 오류와 재시도 안내

시간 초과, 연결 불가, HTTP 404·503·기타 응답 오류, 잘못된 측정 응답과 크기 초과를 짧은 확인 안내로 구분했다. 연결 실패 뒤 다음 측정 주기를 기다리는 상태와 다시 연결 중인 상태를 나눠 표시한다. 폴링 간격과 화면 구성은 유지했다.

첫 [CI 실행](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38009327881)에서 테스트에 사용한 JDK HTTP 서버 클래스가 Android 테스트 컴파일 환경에 없어 실패했다. 표준 `ServerSocket`으로 테스트 응답을 보내도록 고친 뒤 로컬 JUnit 실행과 Gradle 검증을 통과했다. 앱의 추가 실행 의존성은 없다.

검증 대상: `0f5eb28`, v0.2.1. [수정 후 CI 결과](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38024056685).

| 확인한 항목 | 결과 |
| --- | --- |
| 로컬 JUnit 실행 | 20개 성공 |
| Gradle 단위 테스트 | 20개 성공, 실패·오류·생략 0개 |
| Android APK 빌드 | 성공 |
| Android lint | 오류 0개, 경고 13개 |
| 변경하지 않은 ESP32 펌웨어의 CI 컴파일 | 성공 |

새 HTTP 테스트는 로컬 소켓으로 404·503·500 응답, 리다이렉트 차단, 16 KiB 초과 응답, 닫힌 포트와 응답 시간 초과를 확인한다. 실제 센서 측정값을 사용하지 않았다. lint 경고에는 미사용 리소스 8개, Android 대상 버전·백업 설정·배경 겹침·앱 아이콘·주소 입력 접근성 안내가 포함된다.

[원문 테스트·빌드 결과](logs/2026-10-10-v0.2.1-connection-checks.txt). CI는 `android-check-reports` 아티팩트에 단위 테스트 XML과 lint XML도 저장한다. 이번 수정에서는 에뮬레이터 화면을 새로 캡처하지 않았으며 README의 실행 화면은 v0.2.0이다. 재시도 상태 전환과 실물 ESP32 연결 동작은 기기에서 추가 확인이 필요하다.

## 2026-10-10 · 화면 설명용 예시 데이터

구성 목업·4컷 시나리오·SVG 와이어프레임의 자리 표시를 이해하기 쉬운 예시 수치로 바꿨다. 온도 25°C, 습도 50%, 빛 ADC 1000을 기존 계산식에 넣으면 약 57.44이므로 정수 지수는 57로 맞췄다. 휴대폰 조도 320 lx, 움직임 0.12 m/s², 기록 3개와 표시 시각도 가상 예시다. 각 그림과 README에 예시 데이터임을 표시했다.

SVG 문법과 렌더링을 확인하고, 두 PNG의 화면 수치와 예시 표시를 확인했다. 기존 실제 실행 캡처·센서 기록·원문 검증 로그는 변경하지 않았다. 이번 변경은 그림과 설명 문서에 한정되며 앱 코드·펌웨어의 새 빌드나 실물 측정을 수행한 기록이 아니다.

## 2026-10-10 · v0.3.0 앱 아이콘과 붉은색 화면

접시와 물방울을 조합한 자체 적응형 앱 아이콘을 추가했다. 둥근 아이콘 마스크와 Android 13 단색 아이콘 리소스도 포함한다. LG 공식 색상 가이드의 LG Red `#A50034`와 Active Red `#FD312E`를 참고해 버튼·지수·진행 표시를 바꾸고, 따뜻한 회색 바탕과 흰색 센서 카드로 정리했다.

연결 상태 배지, 측정 전·외부 센서 대기·휴대폰 센서만 사용하는 상태의 지수 설명, 입력칸 레이블과 예시 주소를 추가했다. 큰 글자에서도 헤더의 설명이 길어지지 않도록 줄였다. 미사용 문자열을 제거하고, 입력칸의 힌트와 접근성 레이블이 중복되지 않도록 수정했다.

검증 대상: `ffabceb`, v0.3.0. [빌드 결과](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38035962114). 단위 테스트 20개가 실패·오류·생략 없이 통과했고, APK 빌드와 lint를 완료했다. lint는 오류 0개, 경고 3개다. 남은 경고는 Android 대상 버전, 백업 규칙 설정, 루트 배경 겹침이다. [결과 원문](logs/2026-10-10-v0.3.0-design-checks.txt).

첫 [에뮬레이터 실행](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38034770246)은 측정·기록·CSV 저장과 취소·재시작까지 진행했으나, 320dp·130% 글자 크기 확인에서 자동 스크롤 횟수가 부족해 기록 버튼을 찾지 못했다. 검증 도구의 스크롤 한도를 늘리고 목표가 보이지 않으면 명시적으로 실패하도록 수정했다.

수정 뒤 [에뮬레이터 실행](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38035962265)은 통과했다. Android API 30, Pixel 2 화면 프로필을 사용했다. 원본 캡처 확인에서 확대 화면의 내보내기 버튼 아래쪽이 잘린 것을 발견해, 화면 영역 안쪽 여백까지 확인하는 캡처 조건을 추가했다.

최종 [재실행과 캡처](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38036373406), 대상 `fb59019`도 통과했다. 320dp·130% 글자 크기의 기록 개수·기록 버튼·내보내기 버튼이 실제 앱 화면 영역 안쪽에 모두 들어오는 것을 XML 위치와 원본 캡처로 확인했다. 아래 표와 현재 캡처 9장은 이 최종 실행 결과다.

| 확인한 항목 | 결과 |
| --- | --- |
| APK 설치·앱 시작, 아이콘을 포함한 헤더 | 성공 |
| 대기·휴대폰 측정 상태 배지와 지수 안내 | 성공 |
| 측정 시작·중지와 가상 휴대폰 센서 기록 | 성공, CSV 1행 |
| CSV 저장과 내보낸 파일 내용 일치 | 성공 |
| 저장 취소·앱 재시작 후 내부 기록 유지 | 성공 |
| 320dp 화면·130% 글자 크기의 기록·내보내기 버튼 | 성공 |
| Android Runtime 치명적 예외 | 없음 |

[원문 결과와 CSV](logs/2026-10-10-v0.3.0-android-emulator.txt), [당시 실행 캡처 9장](https://github.com/unknownamed/lgax-kitchen-sensor-android/tree/14d49f7/docs/images/screenshots). 이전 v0.2.0 화면은 [해당 커밋의 원본](https://github.com/unknownamed/lgax-kitchen-sensor-android/tree/0141018/app)과 [캡처](https://github.com/unknownamed/lgax-kitchen-sensor-android/tree/0141018/docs/images/screenshots)에 보관되어 있다.

구성 목업·시나리오·와이어프레임도 새 디자인으로 맞췄다. 그림의 수치는 예시 데이터로 유지하며 실제 센서 측정이나 앱 실행 기록을 뜻하지 않는다. 실물 ESP32 연결과 Android 13 이상 기기의 테마 아이콘 모양은 기기에서 확인할 작업으로 남겼다.

## 2026-10-10 · v0.3.1 버튼 크기와 시각적 안내

측정 시작·중지 버튼을 필요한 폭으로 줄이고, 기록·내보내기 버튼을 가로로 배치했다. 버튼의 최소 높이는 48dp로 유지한다. 지수 글자는 52sp, 기록 개수는 28sp로 조정하고, 카드의 여백과 간격을 줄였다. 접시·물방울, 휴대폰, 문서 아이콘으로 지수와 센서·기록 영역의 역할을 구분한다.

와이어프레임 네 단계에 같은 로고를 표시하고 저장 버튼을 붉은색으로 통일했다. 다운로드 목업에 `dish-dry-01.csv`·`dish-dry-02.csv`·`dish-dry-03.csv`를 예시 파일로 넣었다. 구성 그림과 사용 시나리오도 작은 버튼, 공통 로고와 예시 파일 목록으로 맞췄다. 이는 설명용 목업이며 실제 시스템 파일 선택기나 실제 실험 파일을 변경한 기록이 아니다.

초기 [v0.3.1 에뮬레이터 실행](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38037251351), 코드 `d7a2e88`는 기능 검증을 통과했다. 원본 캡처 확인에서 320dp·130% 글자 크기의 가로 버튼 이름이 마지막 한 글자만 다음 줄에 표시되는 것을 발견했다. 글자를 12~15sp 범위에서 자동 조절하고 한 줄로 유지하도록 보완했다.

최종 코드 `41264bf`의 [빌드 결과](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38037627848)는 성공이다. 단위 테스트 20개가 실패·오류·생략 없이 통과했고 APK 빌드와 lint를 완료했다. lint는 오류 0개, 경고 7개다. 기존 대상 버전·백업 규칙·배경 겹침 경고 3개와 새 아이콘/글자 행을 복합 drawable로 줄일 수 있다는 제안 4개다. [검증 결과 원문](logs/2026-10-10-v0.3.1-compact-checks.txt).

최종 [에뮬레이터 실행](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38037627986)도 통과했다. 시작·중지·상태 배지, 가상 센서 값 기록, CSV 저장 내용 일치, 취소와 재시작 후 기록 유지, 320dp·130% 글자 크기의 버튼을 확인했다. 두 버튼의 이름이 한 줄로 보이고 전체 버튼이 화면 안에 들어오는 것을 원본 캡처와 위치 정보로 확인했다. 치명적 Runtime 예외는 없었다. [원문 결과와 CSV](logs/2026-10-10-v0.3.1-android-emulator.txt), [당시 실행 캡처 9장](https://github.com/unknownamed/lgax-kitchen-sensor-android/tree/16fa8c9/docs/images/screenshots). 실제 ESP32는 연결하지 않았다.

## 2026-10-10 · 배선 안내 정리

현재 펌웨어의 DHT GPIO4·LDR GPIO34·3초 측정 간격을 대조해 [배선 안내](WIRING.md)를 추가했다. 온습도 DATA 풀업과 빛 센서 분압 연결을 분리한 그림, 부품 목록, 업로드·Serial·Wi-Fi·HTTP·앱 확인 순서와 증상별 연결 확인을 정리했다. Espressif와 Adafruit의 공식 핀·배선 문서를 참고했다. README 구조도의 색상도 현재 붉은색·따뜻한 회색 기준으로 맞췄다.

문서의 핀 번호·주소·Serial 속도·측정 간격을 소스와 대조하고 로컬 문서 링크와 diff를 확인했다. Android 코드와 펌웨어는 변경하지 않았으며 이번 작업에서 새 빌드나 실물 배선·측정을 수행하지 않았다.

## 2026-10-10 · v0.4.0 장치 그림과 샘플 실행

장치 구성 그림을 지수·기록 카드 안에 추가하고, 개발용 APK에서 출처가 표시된 샘플 화면을 실행하도록 했다. 일반 실행은 실제 센서 모드이며, 샘플 화면의 `example-readings.csv`와 내보내는 `dish-dry-example-readings.csv`를 일반 측정 기록과 분리한다. 샘플 파일 3개는 개발용 화면이 Android MediaStore를 통해 다운로드 폴더에 만든다.

온도 25°C·습도 50%·빛 ADC 1000·휴대폰 조도 320 lx·움직임 0.12·지수 57의 화면 표시와 앱 버튼을 통한 기록 3개를 확인했다. [v0.4.0 실행](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38040135551), 코드 `aa74fa5`는 CSV 파일 3개 표시, 내보내기 내용 일치, 취소·재시작 후 기록 유지, 일반 기록과의 분리, 320dp·130% 글자 크기 확인을 통과했다.

첫 캡처 시도는 Android 셸의 insert 명령이 URI를 반환할 것이라고 가정해 중단됐고, 다음 시도는 파일 제공자의 셸 쓰기 권한 검사에서 중단됐다. 앱의 ContentResolver와 보류 파일 공개 절차로 변경한 뒤 성공했다. 해당 시행 과정은 실물 센서 테스트를 뜻하지 않는다.

## 2026-10-10 · v0.4.1 ESP32-C6와 건전지 구성

제공된 ESP32-C6-WROOM-1 보드 사진을 기준으로 DevKitC-1 헤더 배치와 전원 구성을 정리했다. 펌웨어는 DHT22 GPIO18·LDR GPIO0과 `esp32:esp32:esp32c6` 빌드 대상으로 변경했다. 다른 ESP32 대상에서는 이 배선으로 빌드되지 않도록 대상 검사를 추가했고, 기존 응답 필드를 유지하면서 board 식별 필드를 추가했다.

설치 전원은 AA 알카라인 3개·스위치 홀더·정전압 5V 승압 모듈·보드 5V/GND로 정했다. 업로드 USB와 설치 전원을 분리하는 절차를 [배선 안내](WIRING.md)에 적었다. 건전지 팩과 C6 보드를 앱의 장치 그림·와이어프레임·구성도·시나리오에 반영한다. 사용 시간·부하 시 전압·실물 센서값은 아직 측정하지 않았다.

첫 C6 빌드 대상 `293f211`의 [GitHub Actions](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38041136321)는 Android와 ESP32-C6 펌웨어 빌드를 완료했다. 실제 보드 업로드·건전지 운용 검증과 구분한다.

최종 코드 `5f03ad8`의 [빌드 결과](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38041347077)는 성공이다. 단위 테스트 20개가 실패·오류·생략 없이 통과했고 APK 빌드와 lint를 완료했다. lint는 오류 0개·경고 7개다. C6 펌웨어는 프로그램 996569 bytes, 전역 변수 43160 bytes로 컴파일됐다. [검증 결과](logs/2026-10-10-v0.4.1-c6-checks.txt).

같은 코드의 [최종 에뮬레이터 실행](https://github.com/unknownamed/lgax-kitchen-sensor-android/actions/runs/38041346890)도 통과했다. C6·건전지 장치 그림, 샘플 수치와 기록 3개, 다운로드의 CSV 3개 목록, 내보낸 내용 일치, 취소·재시작, 일반·샘플 기록 분리와 320dp·130% 글자 크기를 확인했다. 시스템 파일 선택기는 목록 보기로 캡처해 파일 이름 세 개가 모두 보인다. [결과와 샘플 CSV](logs/2026-10-10-v0.4.1-android-emulator.txt), [현재 실행 캡처](images/screenshots). Android Runtime 치명적 예외는 없었다.

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
