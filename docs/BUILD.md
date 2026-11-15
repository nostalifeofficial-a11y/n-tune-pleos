# 5.3.0 개발 및 빌드

## Android / Pleos

Android Studio JDK, Android SDK platform 37 / build-tools 36.0.0을 사용합니다. ANDROID_HOME을 본인의 SDK 경로로 지정합니다. 검증 환경은 공식 Pleos 개발 에뮬레이터이며 Vehicle SDK는 2.3.2입니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File android-app/scripts/build-pleos.ps1
```

앱·진단 모듈의 빌드, Android 단위 테스트와 릴리스 매니페스트 검사를 수행합니다. 결과 APK는 android-app/dist/Nostalife-Pleos-5.3.0-debug.apk입니다. 제출 APK 원본은 GitHub v5.3.0 릴리스에 별도 보존합니다. 로컬 서명과 빌드 환경에 따라 재빌드 APK 해시는 달라질 수 있습니다.

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

delivery/에 생성된 두 EXE와 APK를 같은 폴더에 둡니다. 실행기는 공식 Pleos 에뮬레이터 준비·설치를 돕는 도구이며 Windows 차량 앱 자체가 아닙니다. 최초 에뮬레이터 준비에는 대용량 다운로드와 하드웨어 가상화가 필요합니다.

## 검증 범위

Android 36개, 웹 14개, 실행기 8개 검사와 공식 에뮬레이터의 실제 AI 응답 수신·화면 표시를 기존 5.3 배포에서 확인했습니다. [검증 기록](release-validation.json)을 참고하세요. 실차 설정 효과, 새 PC의 전체 최초 설치, Gleo 실제 음성 호출, 출시용 서명 및 앱 마켓 심사는 별도 검증 대상입니다.
