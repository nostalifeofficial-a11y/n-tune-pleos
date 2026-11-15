# 공모전용 Pleos 앱 5.3 적용 근거

Windows 실행기가 공식 Pleos 개발 에뮬레이터에 Android APK를 설치하고 차량 홈의 앱 패널에 표시합니다.
이것은 개발 에뮬레이터 실행 증거이며 실제 차량 배포 승인이나 차량 설정 제어를 의미하지 않습니다.

| API | 앱에서 하는 일 |
|---|---|
| Pleos Vehicle SDK 2.3.2: getBrake, getCurrentBrakePedalStatus, registerBrakePedalStatus / CAR_DRIVE | 브레이크 작동 Boolean 읽기 |
| Android CarPropertyManager / PERF_VEHICLE_SPEED / CAR_SPEED | 차량 속도 읽기 |
| READ_CAR_PEDALS, CAR_DYNAMICS_STATE | 제공되는 페달·서스펜션 속성 읽기, 미지원 구분 |
| Android 센서 API | 제공되는 가속도 확인 |
| CarUxRestrictionsManager | 주행 중 상세 화면 제한 |
| HTTPS latest/received | 홈페이지 AI 결과 수신과 수신 확인 |
| POST /api/channels/{code}/advice | 저장된 주행 요약과 선택한 목표로 실제 AI 추천 요청 |
| loopback 4182 + adb reverse | PC 테스트 주행 입력 연결 |

차량 쓰기 API는 없습니다. N Custom 선택은 앱 UI에만 저장됩니다.
5.1에서 실제 속도 표본과 브레이크 이벤트를 수신했으며 페달·서스펜션·가속도 일부는 미지원이었습니다.
이전 차량 API 측정은 5.1의 별도 기준 기록이며 이번 5.3에서 새 실차 측정을 수행한 것은 아닙니다.
이번 APK 설치 해시와 실제 AI 응답 수신의 검증 범위는 docs/release-validation.json에 정리했습니다.

숫자 추천은 AI가 목표·현재 선호·주행 요약을 읽고 생성합니다. 정해진 표로 환산하지 않습니다.
0~100 지수는 앱의 개인 선호 비교 척도이며 물리적 차량 조정값이 아닙니다.
N 추천은 차종·변속기·가능한 선택지를 검증합니다. 지원하지 않는 단계, 중복 항목, ESC 추천은 거부합니다.
근거가 없는 N 항목은 임의로 채우지 않고 선택에 맡깁니다.
목표와 원래 기록 ID가 일치하는 답변만 현재 추천으로 표시합니다. API 키는 APK에 없습니다.

공모전 홈페이지의 목표 기반 AI는 2026-11-15에 배포했습니다.
사용자 승인 범위에 따라 nostalife-drive만 업데이트했고 기존 심레이싱 서버는 유지했습니다.
