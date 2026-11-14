# 개발 및 빌드

이 문서는 배포된 v4.2.0을 기준으로 합니다. 차량 앱·Windows 실행기의 버전은 4.2.0이며, 웹 서버의 API 상태 버전은 4.0.0으로 유지되어 있습니다.

## 웹과 AI 서버

`web/`에서 Node.js 22.13 이상과 `pnpm`을 사용합니다. `node:sqlite`를 사용하는 로컬 테스트 환경이 필요합니다.

```powershell
cd web
pnpm install --frozen-lockfile
node --test tests/*.test.mjs
Copy-Item .env.example .env.local
node --env-file=.env.local scripts/preview.mjs
```

`.env.local`에는 본인이 발급한 프로젝트용 키를 직접 입력합니다. 브라우저·APK·EXE에 키를 넣지 마세요. 실제 AI 요청에는 API 이용료가 발생하며, 단위 테스트는 모의 응답을 사용합니다. 서버가 켜지면 `http://127.0.0.1:4182`를 엽니다. 미리보기 DB는 메모리에만 존재하므로 재시작할 때 새 연결 코드를 만드세요.

호스팅용 빌드는 다음과 같습니다.

```powershell
Copy-Item .openai/hosting.example.json .openai/hosting.json
# hosting.json에 본인의 Sites 프로젝트 ID를 설정합니다.
node scripts/build.mjs
```

운영 환경에는 `DB` D1 바인딩과 `drizzle/`의 SQL 마이그레이션, 서버용 `OPENAI_API_KEY` secret이 필요합니다. 이 저장소에는 운영 계정 연결 및 배포 권한이 포함되어 있지 않습니다. `scripts/check-live-analysis.mjs`는 실제 유료 요청을 실행하는 별도 확인 스크립트입니다.

웹의 EXE 다운로드를 자체 호스팅하려면 릴리스의 `Nostalife-Pleos.exe`를 `web/public/downloads/`에 배치하세요. 바이너리는 Git 소스 이력에 중복 저장하지 않습니다.

## Android / Pleos

Android Studio의 JDK, Android SDK API 37 및 build-tools 36.0.0이 필요합니다. 현재 에뮬레이터 검증 대상은 공식 Pleos Connect 2.1.0 / API 34이고 Vehicle SDK는 2.3.2입니다. 대상 실차에서 같은 SDK 접근이 된다는 의미는 아닙니다.

`android-app/`을 Android Studio로 열어 SDK 위치를 설정하거나, 로컬에서 `ANDROID_HOME`을 지정합니다. 개인 PC 경로가 들어가는 `local.properties`는 저장소에서 제외합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android-app/scripts/build-pleos.ps1
```

스크립트는 Gradle Wrapper로 빌드·단위 테스트·릴리스 매니페스트 병합 검사를 수행합니다. 한글 경로 대응을 위해 사용 가능한 임시 드라이브 문자를 연결한 뒤 해제합니다. 결과는 `android-app/dist/Nostalife-Pleos-4.2.0-debug.apk`입니다. 개발용 debug 서명이므로 다른 PC에서 다시 빌드하면 APK 해시가 달라질 수 있습니다.

공식 런타임 설치와 사용 조건은 [Pleos 안내](https://document.pleos.ai/docs/pleos-only/getting-started)를 따릅니다. 앱 마켓 등록·심사, 출시용 서명, 실차 권한 및 Gleo 전체 음성 흐름은 별도 작업입니다.

## Windows 실행기

64비트 Windows의 .NET Framework C# 컴파일러를 사용합니다. APK를 먼저 빌드한 후 실행합니다.

```powershell
New-Item -ItemType Directory -Path delivery -Force
powershell -NoProfile -ExecutionPolicy Bypass -File pleos-launcher/build.ps1
```

결과는 `delivery/Nostalife-Pleos.exe`입니다. 배포 릴리스에는 새로 빌드한 파일이 아닌, 2026-11-11에 확인한 기존 EXE 그대로를 첨부합니다. SHA-256은 `docs/SHA256SUMS.txt`를 참고하세요.

## 검증 범위

- 원래 v4.2.0 배포: Android 단위 테스트 25개, 실행기 자체 검사 8개, 실제 EXE 실행과 공식 에뮬레이터의 메인·격자·분석 화면 확인.
- 실제 웹 결과와 앱 캐시·수신 확인의 일치 검증. UI 배포 시점에는 기존 실제 분석 결과를 사용했고 새 유료 AI 요청은 하지 않았습니다.
- 실차 확인, 새 PC의 전체 최초 설치, 차량 셋업 개선 효과는 검증되지 않았습니다.
- 공개 소스에서는 운영 분석에 사용하지 않는 기존 Nostalife의 forest/model 파일과 관련 예전 테스트·내보내기 스크립트를 제외했습니다. 현재 웹 Worker·AI·주행 구간 분석 및 앱·실행기 코드는 원본과 동일하게 보존했습니다.
