# N Tune · Pleos 차량용 앱 시제품 5.3.0

주행 데이터와 운전자의 목표를 함께 분석해, 자신에게 맞는 주행 감각을 탐색하도록 돕는 현대자동차그룹 차량용 앱 개발 공모전 출품작입니다. 실제 앱 화면에는 Nostalife Drive Studio Pleos Edition 이름을 사용합니다.

**5.3.0 서명된 Release APK와 소스**를 함께 제공합니다. 빌드·서명·자동 테스트는 통과했지만, 이번 Release의 공식 Pleos 에뮬레이터 설치·실행과 실제 AI 응답은 아직 검증되지 않았습니다. 아래 검증 범위를 확인해 주세요.

## 심사용 APK

- **[5.3.0 서명된 Release APK 다운로드](https://github.com/nostalifeofficial-a11y/n-tune-pleos/raw/refs/heads/main/apk/Nostalife_NTune_release.apk)**
- [APK 정보·설치·검증 안내](apk/README.md)
- [3D 테스트 주행 홈페이지](https://nostalife-drive.rusticglint.chatgpt.site)
- [빌드 안내](docs/BUILD.md) · [검증 기록](docs/release-validation.json) · [APK SHA-256](docs/SHA256SUMS.txt)

요구 환경은 공식 Pleos Connect 2.1.0 에뮬레이터(Android Automotive API 34 / x86_64)입니다. APK의 최소 SDK는 29입니다. 부팅이 완료된 에뮬레이터에서 저장소 루트 기준으로 설치·실행합니다.

```bash
adb install -r apk/Nostalife_NTune_release.apk
adb shell am start -n kr.nostalife.pleos.contest/kr.nostalife.companion.PleosActivity
```

웹에서 주행 후 ‘분석하고 앱으로 보내기’를 누르고, 앱의 ‘홈페이지 결과 → 결과 연결’에 같은 연결 코드를 입력하는 흐름입니다. 서버에 유효한 AI API 설정이 필요합니다. 웹의 가상 주행은 시연용 데이터 공급 수단입니다. Release는 HTTPS 웹 연결을 사용하며, HTTP 로컬 연결과 기존 Windows 실행기는 [이전 Debug 개발 흐름](delivery/README.md)입니다.

- APK: `apk/Nostalife_NTune_release.apk` · **24,010,824 bytes** (약 22.90 MiB)
- 버전: `5.3.0` / code `10` · 패키지: `kr.nostalife.pleos.contest`
- SHA-256: `0c3617b08f45141b9982c4e58e6d9b9909f165114cb6db992a4cb23852da25bb`
- Release 빌드, APK Signature Scheme v2 검증, 디버그 플래그 해제, 16 KiB APK 정렬 검사 통과

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

- **Pleos 에뮬레이터 이미지:** 공식 `connect-avd/2.x/crp_car_emulator.zip` 사용. 이번 실행에서 ADB로 `Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys` 빌드 지문을 확인했습니다.
- **운영 환경:** Android Automotive API 34 / x86_64. 앱 빌드는 Android SDK Platform 37, Build Tools 36.0.0, Pleos Vehicle SDK 2.3.2를 사용합니다.
- **메인 화면 설정:** 2560 × 1440 px, 160 dpi. **보조 디스플레이 설정:** 720 × 1280 px, 213 dpi. 이는 `pleos-launcher/Launcher.cs`의 AVD 구성값이며, 실제 차량의 해상도 규격을 뜻하지 않습니다.
- **Release 산출물:** `apk/Nostalife_NTune_release.apk`는 `assembleRelease`로 빌드하고 별도의 Release 키로 서명했습니다. 기존 Debug 파일의 이름을 바꾼 것이 아닙니다.
- **이번 설치 검증 한계:** Linux 작업환경에 KVM/CPU 가상화가 없습니다. 소프트웨어 모드에서 공식 이미지의 ADB 연결과 APK 전송·해시 일치까지 확인했으나, Android 시스템의 메인 스레드가 60초 이상 멈춰 재시작되면서 패키지 서비스가 사라졌습니다. 설치·실행은 확인하지 못했습니다.
- **과거 Debug 이력:** `delivery/Nostalife-Pleos-5.3.0-debug.apk`의 이전 설치·화면·AI 검증 기록은 보존했습니다. 그 결과를 새 Release의 검증 결과로 옮기지 않습니다.

## 검증 및 소스

이번 실행에서 Android 단위 테스트 **36/36**, 웹 모의 AI 테스트 **14/14**, 로컬 연결 합성 데이터 테스트 **42/42**가 통과했습니다. Release lint는 **오류 0건 / 경고 22건**입니다. Windows 실행기 검사는 이번 Linux 환경에서 실행하지 않았습니다.

새 API 키 발급 요청이 OpenAI Platform에서 거부되어 이번 실제 AI 호출은 실행하지 않았습니다. 앱의 핵심 화면·연결·추천 표시의 Release 종단 간 검증과 Gleo 실제 음성 호출은 남아 있습니다. [검증 기록](docs/release-validation.json)의 `current_run`이 이번 결과이고, `previous_debug_validation`은 기존 이력입니다.

| 경로 | 내용 |
|---|---|
| android-app/pleos-app/ | 공모전용 Pleos UI 및 목표별 추천 |
| android-app/app/, android-app/vehicle-probe/ | 공통 계약·통신·저장 및 차량 API 읽기 |
| web/ | 테스트 주행, AI 서버, 결과 전달 API |
| pleos-launcher/ | 공식 에뮬레이터와 APK를 준비하는 Windows 실행기 |
| local-bridge/ | PC 테스트 주행 입력과 로컬 연결 도구 |
| apk/ | 서명된 Release APK와 설치·검증 안내 |
| delivery/ | 이전 Debug APK와 개발용 설치 안내 |

[현재 소스 파일 해시 목록](docs/source-manifest.json) · [이전 소스 해시 이력](docs/history/source-manifest-5.3.0-original.json) · [Pleos API 적용 근거](docs/API-적용근거.md) · [외부 구성요소](docs/THIRD_PARTY_NOTICES.md)

심레이싱 원본 Nostalife의 ACC/PC 도구와 이번 Pleos 차량용 시제품은 실행 환경과 검증 범위가 다릅니다. 공모전 시제품의 N Custom은 개인 선호를 기록하는 UI이며 실차 센서 수집·ECU 설정 변경을 검증한 제품이 아닙니다.

[공식 제출 안내](https://appdevchallenge.auto-hmg.io/guide/submission)는 `apk/팀명_앱이름_release.apk`, 소스 코드, 최종 커밋 SHA와 README의 실행 환경을 요구합니다. 접수양식·동의서 제출과 접수 이메일 전송은 별도 절차입니다.

API 키, 개인 연결 코드, 원시 주행 기록, 로컬 계정 설정은 포함하지 않습니다. 자체 소스에 새 재배포 라이선스를 부여하지 않으며 외부 구성요소는 각 라이선스를 따릅니다.
