# N Tune · Pleos Pit Wall

주행 데이터를 분석하고, 운전자가 자신에게 맞는 승차감과 핸들링을 찾아가도록 돕는 **N Tune의 시제품**입니다. 현재 실행 화면에는 호환성을 위해 **Driving Lab** 이름이 남아 있습니다.

이 저장소는 **2026년 11월 11일 배포한 Pit Wall v4.2.0 EXE**에 대응하는 웹·AI 서버·Android 앱·Windows 실행기 소스를 정리한 공개 스냅샷입니다. 기존 Nostalife/ACC 프로젝트와 별도 프로젝트이며, 기존 Nostalife API를 호출하지 않습니다.

## 바로 실행

- **[Windows EXE 받기](https://github.com/nostalifeofficial-a11y/n-tune-pleos/releases/download/v4.2.0/Nostalife-Pleos.exe)**
- **[3D 주행 웹사이트 열기](https://nostalife-drive.rusticglint.chatgpt.site)**
- [v4.2.0 배포 자료](https://github.com/nostalifeofficial-a11y/n-tune-pleos/releases/tag/v4.2.0)
- [기존 웹사이트의 EXE 다운로드](https://nostalife-drive.rusticglint.chatgpt.site/downloads/Nostalife-Pleos.exe?v=4.2.0)

1. Windows PC에서 `Nostalife-Pleos.exe`를 실행합니다. 실행기는 공식 Pleos 에뮬레이터와 앱을 준비합니다. 처음 실행할 때는 약 4.6 GB의 다운로드와 가상화 지원이 필요합니다.
2. 웹 시뮬레이터와 Pleos 앱에 같은 연결 코드를 사용합니다.
3. 웹에서 `W/S`로 가속·제동하고 `A/D`로 조향합니다. 방향키도 지원합니다.
4. **분석하고 앱으로 보내기**를 누릅니다. 주행을 시작한 시점부터 버튼을 누른 시점까지를 분석합니다. **3랩 조건은 없습니다.**
5. Pleos 앱을 정차 상태에서 열어 두면 최신 결과가 갱신됩니다. 메인 화면, 12개 측정값 격자, 상세 분석을 확인할 수 있습니다.

EXE는 Windows용 차량 앱 자체가 아니라 **공식 Android Pleos 에뮬레이터와 APK를 실행하는 도구**입니다. Windows 코드 서명은 적용되어 있지 않습니다. 처음 설치하는 새 PC에서의 전체 설치 절차는 별도 검증이 필요합니다.

## 구현한 기능

- 직접 조작하는 3D 주행과 가상 차량 데이터 기록
- 가속·브레이크 입력, 횡·종가속도, 조향 변화, 서스펜션 변위 등의 지표 계산
- 전용 OpenAI API를 사용하는 서버 분석과 시뮬레이터용 기초 셋업 후보
- 연결 코드로 웹 결과 전달, Pleos 앱의 자동 조회와 실제 수신 확인
- Pit Wall 메인 화면과 3열 × 4행 세부 측정 화면
- 차량 API 진단 코드 및 Gleo action/callback 처리 코드
- 주행 제한 상태에 따른 화면 축소

## 구현 범위와 앞으로 할 일

현재 검증한 경로는 **가상 주행 → AI 분석 → 공식 Pleos 에뮬레이터 표시**입니다. 시뮬레이터 값은 실차에서 측정한 값이 아닙니다.

실차 센서별 접근 권한과 수집 가능 여부, 차량별 N 커스텀 설정과의 연결, 셋업 변경 효과, 사용자 만족도 기반의 반복 개인화, 스마트폰 전용 서비스, Gleo 실제 음성 호출은 후속 개발·검증 범위입니다. 차량 설정은 사용자가 직접 변경하는 흐름을 목표로 하며 이 앱은 차량 ECU를 자동 제어하지 않습니다.

## 소스 구성

| 경로 | 내용 |
| --- | --- |
| `web/` | 3D 시뮬레이터, 측정 지표, AI 분석, 결과·연결 API |
| `android-app/pleos-app/` | 실제 Pleos 앱의 화면과 Gleo 응답 |
| `android-app/app/` | 공통 데이터 계약·통신·저장 코드와 초기 Android 모듈 |
| `android-app/vehicle-probe/` | 허용된 차량 API로 지원 여부를 확인하는 진단 모듈 |
| `pleos-launcher/` | Windows 실행기 C# 소스와 빌드 스크립트 |
| `docs/` | 빌드 안내, 버전 일치 정보, 검증 범위, 파일 해시 |

[개발 및 빌드 안내](docs/BUILD.md) · [v4.2.0 검증 기록](docs/release-validation.json) · [원본과 동일한 소스 파일 목록](docs/source-manifest.json) · [실행 파일 SHA-256](docs/SHA256SUMS.txt)

API 키, 계정 설정, 개인 주행 기록, 로컬 경로 설정, 개발 캐시 및 기존 Nostalife의 미사용 모델은 포함하지 않습니다. 키는 서버 환경변수로 설정합니다. 공개 저장소라는 사실만으로 재배포 라이선스가 부여되지는 않으며, 포함된 외부 구성요소에는 [각 라이선스](docs/THIRD_PARTY_NOTICES.md)가 적용됩니다.
