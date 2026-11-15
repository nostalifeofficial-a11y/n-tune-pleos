# N Tune 5.3.0 서명된 Release

[Nostalife_NTune_release.apk 다운로드](https://github.com/nostalifeofficial-a11y/n-tune-pleos/raw/refs/heads/main/apk/Nostalife_NTune_release.apk)

| 항목 | 값 |
|---|---|
| 빌드 | `:pleos-app:assembleRelease` |
| 패키지 | `kr.nostalife.pleos.contest` |
| 버전 | `5.3.0` / versionCode `10` |
| 최소 / 대상 / 컴파일 SDK | 29 / 34 / 37 |
| 크기 | 24,010,824 bytes (약 22.90 MiB) |
| SHA-256 | `0c3617b08f45141b9982c4e58e6d9b9909f165114cb6db992a4cb23852da25bb` |
| 서명 | RSA 4096, APK Signature Scheme v2 검증 통과 |
| 인증서 SHA-256 | `813c495f93e4f5a390faed73d7067b264289e451c01dbfcff1133e162e5fe741` |
| 디버그 / APK 정렬 | debuggable 아님 / `zipalign -c -P 16 4` 통과 |
| 설치·실행 | 이번 환경에서 미검증 — 아래 제한 참조 |

## 공식 Pleos 에뮬레이터에서 확인할 절차

[공식 설치 안내](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)에 따라 Pleos Connect 2.1.0(API 34, x86_64)을 실행합니다. AVD 설정은 메인 2560×1440 / 160 dpi, 보조 720×1280 / 213 dpi입니다. 아래 명령은 저장소 루트 기준입니다.

```bash
adb devices
adb shell getprop sys.boot_completed
adb install -r apk/Nostalife_NTune_release.apk
adb shell am start -n kr.nostalife.pleos.contest/kr.nostalife.companion.PleosActivity
adb shell am force-stop kr.nostalife.pleos.contest
adb shell am start -n kr.nostalife.pleos.contest/kr.nostalife.companion.PleosActivity
```

`sys.boot_completed`가 `1`이고 설치 출력이 `Success`인지 먼저 확인합니다. Automotive의 활성 사용자와 메인 디스플레이에서 앱이 보이는지도 확인해야 합니다. 앱은 주행 제한 상태에서 상세 화면을 숨깁니다.

웹에서 가상 주행 → 분석 → 연결 코드 생성 후, 앱에서 ‘홈페이지 결과 → 결과 연결’에 코드를 입력합니다. 목표 복수 선택·직접 입력, 승차감·조향응답성, 차종별 N Custom, AI 추천 이유·다음 행동 표시를 확인합니다. 서버에는 유효한 AI API 키와 사용 권한이 필요합니다. 이 절차는 남은 검증 안내이며 완료 기록이 아닙니다.

## 이번 검증 결과와 제한

- 빌드·서명·패키지 검사·APK 정렬 통과. Android 36/36, 웹 모의 AI 14/14, 로컬 연결 42/42 통과. Release lint 오류 0건 / 경고 22건.
- 공식 Pleos 이미지의 SHA-1은 `b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa`입니다. 이미지 빌드 지문과 전송 APK의 SHA-256을 ADB로 확인했습니다.
- Linux 작업환경에 KVM/가상화 CPU 기능이 없어 기본 실행이 거부됐습니다. 강제 소프트웨어 모드에서는 ADB에 연결됐지만 `sys.boot_completed`가 비어 있었고 설치 시 `cmd: Can't find service: package`가 반환됐습니다. 잠시 준비됐던 패키지 서비스도 Android 시스템의 Watchdog 메인 스레드 60초 정지와 `system_server` 재시작 후 사라졌습니다. APK 결함 여부를 판단할 설치·실행 결과는 확보하지 못했습니다.
- OpenAI Platform의 새 키 발급 요청 거부로 실제 AI 응답은 미실행입니다. 모의 응답 테스트를 실제 AI 검증으로 간주하지 않습니다.
- Windows 실행기, Gleo 실제 음성, 실차 센서 수집은 이번 실행에서 미검증이며 ECU 제어는 미구현입니다.

서명 키와 비밀번호는 저장소에 포함하지 않습니다. 동일 패키지의 업데이트에는 같은 키가 필요합니다. Debug는 `.debug` 패키지이며 Release와 별개입니다. Release의 HTTP 로컬 연결은 허용하지 않으므로 기존 PC 실행기로 이 APK를 검증했다고 해석하지 않습니다.

[빌드·키 설정](../docs/BUILD.md) · [기계 판독 검증 기록](../docs/release-validation.json) · [전체 APK 해시](../docs/SHA256SUMS.txt)
