# 5.3.0 개발 및 Release 빌드

## 서명된 Release APK

JDK 21, Gradle Wrapper 9.4.1, Android SDK API 37 (`platforms;android-37.0`), Build Tools 36.0.0을 사용합니다. `JAVA_HOME`과 `ANDROID_HOME`을 설치 경로로 지정합니다. Pleos Vehicle SDK는 2.3.2입니다.

서명 키는 저장소 밖에 보관하고 다음 환경변수를 비밀관리 도구나 로컬 비공개 파일에서 제공합니다. 비밀번호를 명령 기록이나 소스 코드에 직접 쓰지 않습니다.

| 환경변수 | 값 |
|---|---|
| `NTUNE_RELEASE_STORE_FILE` | 키스토어의 절대 경로 |
| `NTUNE_RELEASE_STORE_PASSWORD` | 키스토어 비밀번호 |
| `NTUNE_RELEASE_KEY_ALIAS` | 서명 키 별칭 |
| `NTUNE_RELEASE_KEY_PASSWORD` | 서명 키 비밀번호 |

저장소 루트에서 실행합니다.

```bash
bash android-app/scripts/build-release.sh
```

이 스크립트는 `:pleos-app:assembleRelease`와 세 Android 모듈의 `testDebugUnitTest`를 실행합니다. 이 프로젝트에서 단위 테스트가 생성되는 변형은 Debug입니다. 단위 테스트 통과를 Release 설치·실행 통과로 간주하지 않습니다.

빌드 후 서명, 패키지 ID, 디버그 플래그, APK 정렬을 검사하고 통과한 파일을 `apk/Nostalife_NTune_release.apk`로 복사합니다. 원본은 `android-app/pleos-app/build/outputs/apk/release/pleos-app-release.apk`입니다. 서명 정보 없이 제출 파일을 만드는 방식이나 Debug APK의 이름을 바꾸는 방식은 사용하지 않습니다.

Windows에서는 동일한 환경변수를 안전하게 설정한 뒤 `android-app/`에서 다음 Gradle 작업을 실행할 수 있습니다.

```powershell
.\gradlew.bat :pleos-app:assembleRelease :pleos-app:testDebugUnitTest :app:testDebugUnitTest :vehicle-probe:testDebugUnitTest
& "$env:ANDROID_HOME/build-tools/36.0.0/apksigner.bat" verify --verbose --print-certs pleos-app/build/outputs/apk/release/pleos-app-release.apk
& "$env:ANDROID_HOME/build-tools/36.0.0/aapt.exe" dump badging pleos-app/build/outputs/apk/release/pleos-app-release.apk
```

패키지 ID는 `kr.nostalife.pleos.contest`이고 `application-debuggable`이 없어야 합니다. 설치·실행 절차와 현재 검증 한계는 [Release 안내](../apk/README.md)를 확인합니다.

새 Release 키는 이후 같은 앱의 업데이트에도 필요합니다. 키스토어와 비밀번호를 함께 안전하게 백업해야 하며 다른 키로 서명된 기존 Release에는 일반 업데이트 설치가 불가능합니다. 기존 Debug 앱은 `.debug` 패키지를 사용하므로 Release와 별개로 설치됩니다.

## 기존 Android Debug / Pleos 개발 빌드

Android Studio JDK, Android SDK platform 37 / build-tools 36.0.0을 사용합니다. ANDROID_HOME을 본인의 SDK 경로로 지정합니다. 검증 환경은 공식 Pleos 개발 에뮬레이터이며 Vehicle SDK는 2.3.2입니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android-app/scripts/build-pleos.ps1
```

앱·진단 모듈의 Debug 빌드, Android 단위 테스트와 Release 매니페스트 검사를 수행합니다. 결과 APK는 android-app/dist/Nostalife-Pleos-5.3.0-debug.apk입니다. `delivery/`의 APK는 이전 Debug 검증 이력으로 보존하며, 제출용 Release와 구분합니다. 로컬 서명과 빌드 환경에 따라 재빌드 APK 해시는 달라질 수 있습니다.

## 웹과 AI 서버

web/에서 Node.js 22.13 이상, pnpm을 사용합니다. 단위 테스트는 모의 AI와 node:sqlite를 이용하며 유료 AI 호출을 하지 않습니다.

```powershell
cd web
pnpm install --frozen-lockfile
node --test tests/ai.test.mjs tests/api.test.mjs tests/window.test.mjs
Copy-Item .env.example .env.local
node --env-file=.env.local scripts/preview.mjs
```

.env.local에 서버용 OPENAI_API_KEY를 설정합니다. 로컬 주소는 http://127.0.0.1:4182이며 미리보기 DB는 메모리에 존재합니다. 실제 AI 요청에는 이용료가 발생합니다. 서버 API 상태와 웹 package.json의 기존 4.0.0 표기는 Android 앱 5.3.0 버전과 별개입니다.

호스팅 빌드는 web/.openai/hosting.example.json을 hosting.json으로 복사하고 본인 프로젝트를 설정한 뒤 node scripts/build.mjs로 수행합니다. 운영 환경에는 DB D1 바인딩, drizzle/ 마이그레이션과 서버용 API 키가 필요합니다. 운영 배포 권한·계정 설정은 포함하지 않습니다.

## Windows 실행기와 로컬 연결

Android APK를 먼저 빌드합니다. 로컬 연결 도구는 Python 3.12 및 PyInstaller, 실행기는 64비트 Windows의 .NET Framework C# 컴파일러를 사용합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File local-bridge/build.ps1 -Python <python.exe>
powershell -NoProfile -ExecutionPolicy Bypass -File pleos-launcher/build.ps1
```

delivery/에 생성된 두 EXE와 APK를 같은 폴더에 둡니다. 기존 실행기는 Debug 패키지와 `run-as` 진단에 의존합니다. Release 설치에는 Release 안내의 `adb` 명령을 사용합니다. 실행기는 공식 Pleos 에뮬레이터 준비·설치를 돕는 도구이며 Windows 차량 앱 자체가 아닙니다. 최초 에뮬레이터 준비에는 대용량 다운로드와 하드웨어 가상화가 필요합니다.

로컬 연결 도구의 합성 데이터 테스트는 저장소 루트에서 실행합니다. 실제 ACC 게임, 실차나 AI 서비스를 호출하지 않습니다.

```bash
python -m unittest discover -s local-bridge -p 'test*.py' -v
```

## 검증 범위

현재 실행의 결과와 이전 5.3 Debug 검증 이력은 [검증 기록](release-validation.json)에서 구분합니다. 빌드, 서명, 단위 테스트, 에뮬레이터 설치·실행, 실제 AI 응답은 서로 다른 검증 항목입니다. 실차 설정 효과, 새 PC의 전체 최초 설치, Gleo 실제 음성 호출 및 앱 마켓 심사는 별도 검증 대상입니다. Release는 HTTPS 웹 결과 연결을 사용하며, HTTP PC 로컬 연결은 기존 Debug 개발 흐름으로 한정됩니다.
