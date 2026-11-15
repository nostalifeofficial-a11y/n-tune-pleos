# N Tune · Pleos 차량용 앱 시제품 5.3.0

주행 데이터와 운전자의 목표를 함께 분석해, 자신에게 맞는 주행 감각을 탐색하도록 돕는 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다. 실제 앱 화면에는 Nostalife Drive Studio Pleos Edition 이름을 사용합니다.

**2026-11-15 최종 제출용 소스와 검증된 APK**를 함께 제공합니다. 이전 4.2.0 릴리스는 과거 버전이며 현재 제출본은 5.3.0입니다.

## 심사용 APK

- **[5.3.0 APK 다운로드](https://github.com/nostalifeofficial-a11y/n-tune-pleos/raw/refs/heads/main/delivery/Nostalife-Pleos-5.3.0-debug.apk)**
- [APK 파일과 설치 안내](delivery/README.md)
- [3D 테스트 주행 홈페이지](https://nostalife-drive.rusticglint.chatgpt.site)
- [빌드 안내](docs/BUILD.md) · [검증 기록](docs/release-validation.json) · [APK SHA-256](docs/SHA256SUMS.txt)

공식 Pleos 개발 에뮬레이터에 APK를 설치해 실행합니다. 웹에서 주행 후 ‘분석하고 앱으로 보내기’를 누르고, 앱의 ‘홈페이지 결과 → 결과 연결’에 같은 연결 코드를 입력합니다. 웹의 가상 주행은 시연용 데이터 공급 수단입니다.

## 구현한 기능

- 6가지 주행 목표의 복수 선택과 직접 입력
- 저장된 주행 요약·목표·현재 선호를 바탕으로 실제 AI 추천 요청
- 승차감·조향응답성의 추천값과 현재 선택을 구분해 표시
- 짧은 분석 설명, 다음 주행에서 해볼 활동, 한국시간 보관함
- N 모드와 차종별 CUSTOM 1/2 UI, 현재 선택과 AI 추천 외곽선 구분
- 공식 Pleos API·Android 차량/센서 API의 읽기 경로와 미지원 신호 구분
- 주행 중 상세 화면 제한

0~100 수치는 앱의 개인 선호 비교 지수입니다. 물리적인 차량 조정값이 아니며, N Custom은 UI 시제품입니다. 차량 쓰기 API나 ECU 자동 제어 기능은 없습니다. 실제 차량에서의 설정 효과와 안전성은 후속 검증 대상입니다.

## Pleos 개발 에뮬레이터 환경 및 APK 유형

- **Pleos 에뮬레이터 이미지:** 공식 `connect-avd/2.x/crp_car_emulator.zip` 사용. 저장소 실행기의 AVD 태그는 `Pleos Connect v2.1.0 APIs`이며, 이미지 자체의 세부 빌드 번호는 별도로 확인되지 않았습니다.
- **운영 환경:** Android Automotive API 34 / x86_64. 앱 빌드는 Android SDK Platform 37, Build Tools 36.0.0, Pleos Vehicle SDK 2.3.2를 사용합니다.
- **메인 화면 설정:** 2560 × 1440 px, 160 dpi. **보조 디스플레이 설정:** 720 × 1280 px, 213 dpi. 이는 `pleos-launcher/Launcher.cs`의 AVD 구성값이며, 실제 차량의 해상도 규격을 뜻하지 않습니다.
- **현재 검증 APK:** `delivery/Nostalife-Pleos-5.3.0-debug.apk` (debug 빌드, `kr.nostalife.pleos.contest.debug`). 공식 Pleos 개발 에뮬레이터에서 이 파일의 설치·표시와 AI 응답을 확인했습니다.
- **release 제출 구분:** 현재 저장소에는 배포 서명된 release APK가 없습니다. 기존 debug APK의 파일명만 release로 변경해서는 안 됩니다. 공모전 안내의 `apk/팀명_앱이름_release.apk` 형식은 실제 릴리스 빌드·서명·설치 검증 후에만 충족한 것으로 간주합니다.

## 검증 및 소스

5.3.0 배포 시 Android 단위 테스트 36개, 웹 테스트 14개, Windows 실행기 검사 8개를 통과했습니다. 공식 Pleos 에뮬레이터에서 설치 APK의 해시 일치, 실제 AI 응답 수신, 목표 선택과 추천 표시를 확인했습니다. 실차 검증 완료를 의미하지 않습니다.

| 경로 | 내용 |
|---|---|
| android-app/pleos-app/ | 공모전용 Pleos UI 및 목표별 추천 |
| android-app/app/, android-app/vehicle-probe/ | 공통 계약·통신·저장 및 차량 API 읽기 |
| web/ | 테스트 주행, AI 서버, 결과 전달 API |
| pleos-launcher/ | 공식 에뮬레이터와 APK를 준비하는 Windows 실행기 |
| local-bridge/ | PC 테스트 주행 입력과 로컬 연결 도구 |
| delivery/ | 최종 APK와 설치 안내 |

[소스 파일 해시 목록](docs/source-manifest.json) · [Pleos API 적용 근거](docs/API-적용근거.md) · [외부 구성요소](docs/THIRD_PARTY_NOTICES.md)

API 키, 개인 연결 코드, 원시 주행 기록, 로컬 계정 설정은 포함하지 않습니다. 자체 소스에 새 재배포 라이선스를 부여하지 않으며 외부 구성요소는 각 라이선스를 따릅니다.
