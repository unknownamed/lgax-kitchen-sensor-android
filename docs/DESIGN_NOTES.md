# 화면 디자인 기준 · v0.3.0

LG AX · 키친에서 치킨먹기 / 황대겸

## 받은 파일

[DESIGN.md](../DESIGN.md)는 [VoltAgent/awesome-design-md의 Apple 분석 문서](https://github.com/VoltAgent/awesome-design-md/blob/13be5c05c63be24b57581162364167028020f043/design-md/apple/DESIGN.md)를 2026-10-10에 내려받은 원본이다. Apple의 공식 문서나 제휴를 뜻하지 않는다. 원본 커밋은 `13be5c05c63be24b57581162364167028020f043`이며 [MIT 라이선스](references/apple-design-license.txt)를 함께 보관한다.

## 현재 Android 화면

v0.3.0은 [LG 공식 디자인 시스템](https://www.lg.com/global/our-identity/design-system/)의 둥근 제품 형태와 [LG 공식 색상 가이드, 37쪽](https://www.lg.com/us/wonderbox/Wonderbox-GuidelinesSpecs.pdf)의 LG Red·Active Red·Warm Gray를 참고한다. 접시와 물방울로 만든 자체 심볼을 사용한다. 위의 Apple 분석 파일은 이전 디자인의 참고 기록으로 보관한다.

| 기준 | 앱 적용 |
| --- | --- |
| 바탕과 표면 | 따뜻한 회색 `#F6F3EF`, 흰색 카드와 연한 장밋빛 지수 카드 |
| 글자 | `#262626` 본문, `#716F6A` 보조 설명 |
| 동작 색상 | LG Red `#A50034` 시작·기록 버튼, 지수와 내보내기 테두리 |
| 강조 | Active Red `#FD312E` 진행 표시, 연한 붉은 상태 배지 |
| 카드 | 22dp 둥근 모서리, 얇은 테두리와 센서별 안쪽 표면 |
| 버튼 | 50dp 이상 터치 영역, 둥근 캡슐, 누름·초점·비활성 상태 |
| 여백 | 화면 20dp, 카드 18dp, 센서 칸 12dp, 카드 간격 16dp |
| 글자 계층 | 제목 28sp, 지수 64sp, 센서값 24~32sp, 설명 13~14sp |
| 앱 아이콘 | 적응형 아이콘, 둥근 마스크와 Android 13 단색 아이콘 지원 |

큰 지수 아래에 연결·센서·기록을 세로로 배치한다. 상단에 자체 아이콘과 팀명을 표시하고, 연결 상태를 짧은 배지와 상세 안내로 보여 준다. 주소 입력칸에는 화면에 계속 보이는 레이블을 연결한다. 글자 확대 때 내용이 늘어나도록 버튼 높이와 카드 높이는 고정하지 않는다.

글꼴은 Android 기본 sans-serif를 사용한다. 지수나 센서값은 유효한 측정값으로만 표시하고, 실제 값이 없으면 대시 또는 미측정으로 표시한다.

README의 실행 화면은 APK 원본 캡처다. 구성 목업·시나리오·와이어프레임은 개념 설명용이며, ‘예시 데이터’라고 표시한 가상 수치를 사용한다. 온도 25°C, 습도 50%, 빛 ADC 1000과 임시 지수 57을 일관되게 맞춘다. 휴대폰 조도 320 lx, 움직임 0.12 m/s², 기록 3개와 시각도 설명용 예시다. 앱 화면과 실제 실행 로그에는 예시 수치를 넣지 않는다.

## 코드별 책임

| 파일 | 맡은 일 |
| --- | --- |
| `MainActivity` | 화면 생명주기, 버튼 동작, 시스템 파일 선택기, 백그라운드 파일 작업 |
| `DashboardView` | 화면을 한 번 연결하고 바뀐 표시만 갱신 |
| `MeasurementController` | 휴대폰 센서 등록·해제, 외부 센서 폴링, 지난 측정 회차 응답 제외 |
| `SensorHttpClient` | 주소 검사, HTTP 요청 시간·응답 크기 제한 |
| `MeasurementState` | 측정값 보관, 단조 증가 시간 기준 유효 기간 |
| `SensorReading` | 외부 센서 범위 검사와 임시 지수 계산 |
| `ReadingSnapshot` | 기록 버튼을 누른 시점의 유효한 값 |
| `RecordStore` | CSV 쓰기·읽기·복사와 기록 요약 |

외부 측정값은 응답에 들어 있는 `sample_age_ms`와 수신 뒤 경과 시간을 합쳐 10초를 넘으면 제외한다. 화면을 벗어나면 센서 등록과 폴링을 멈춘다. CSV 읽기·쓰기·내보내기는 별도 파일 스레드에서 실행한다.
