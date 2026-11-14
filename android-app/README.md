# N Tune Android / Pleos

현재 실행 화면 이름은 Driving Lab입니다. `pleos-app` 모듈이 v4.2.0의 실제 Pleos 앱이며, `app`과 `vehicle-probe`의 공통 코드를 함께 사용합니다. `app` 모듈만 따로 빌드한 결과는 최신 Pleos 화면과 다릅니다.

메인 화면, 12개 지표 격자, AI 상세 결과, 연결 코드 기반의 자동 조회와 수신 확인을 제공합니다. 주행 데이터는 웹 시뮬레이터가 생성한 값입니다. 실차 API 진단 코드는 신호의 실제 지원 여부를 확인하기 위한 것이며, 실차 데이터 확보 완료를 의미하지 않습니다.

Gleo action/callback 처리는 포함되어 있지만 실제 음성 인식부터 호출까지의 전체 검증과 심사 승인은 별도입니다.

[빌드 안내](../docs/BUILD.md) · [검증 범위](../docs/release-validation.json)
