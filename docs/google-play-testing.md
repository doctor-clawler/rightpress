# Google Play 테스트 배포

현재 준비 버전: `0.6.1-beta.1` / versionCode `9`. Google Play 설치 링크는 아직 발급되지 않았다. GitHub APK 다운로드와 Play 내부 테스트 설치는 서로 다른 배포 경로다.

## 빌드

- application ID: `io.github.doctor_clawler.rightpress`
- minSdk 34 / targetSdk 36 / compileSdk 36
- JDK 17 / Android Gradle Plugin 8.10.1 / Gradle 8.11.1
- 공식 APK와 같은 배포 서명 사용. 비밀 정보는 로컬 환경 변수로만 제공.

```sh
python3 tools/localization.py check
./gradlew :app:bundleRelease :app:assembleRelease :app:lintRelease
```

AAB: `app/build/outputs/bundle/release/app-release.aab`.
모든 언어를 포함하며 별도 폰트·네이티브 라이브러리를 추가하지 않는다.

## 기존 설치와 서명

Play App Signing에 등록할 때 **기존 앱 서명키 사용**을 선택해야 GitHub APK와 업데이트 호환성을 유지할 수 있다. AAB를 기존 키로 서명하는 것만으로 Play 설치 APK의 서명이 일치하는 것은 아니다. Google이 새 앱 서명키를 생성하면 기존 키는 업로드키로만 쓰일 수 있다.

기존 공식 APK의 앱 서명 인증서 SHA-256:

```text
28:BB:0C:A0:7D:34:46:6F:92:AD:DA:DE:BB:06:14:F9:EE:EB:52:7F:CD:1D:B8:76:4B:DA:74:C9:8F:00:EA:E0
```

Play Console에서 제공하는 PEPK 절차로 키를 암호화해 전송하고, 등록 후 **앱 서명 인증서** 값을 비교한다. 키 원본·암호·암호화한 키 파일은 저장소나 GitHub 릴리스에 올리지 않는다. 서명이 다르면 삭제 후 재설치를 업데이트 검증의 대체로 삼지 않는다.

## 내부 테스트 완료 기준

1. 앱 등록과 필요한 정책·수출 규정·앱 서명 설정 완료.
2. 서명된 AAB 업로드 후 Console에 표시된 패키지·versionCode·targetSdk 확인.
3. 테스터가 실제 사용할 Google 계정을 내부 테스트 대상으로 등록.
4. 내부 테스트 출시 상태와 Console에서 발급된 참여 URL 확인.
5. 등록 계정으로 참여 페이지가 열리는지 확인. Play 설치 후 접근성 연결·입력 동작은 기기에서 별도 검증.

내부 테스트 배포를 일반 공개 출시나 Google 정책 심사 통과로 표현하지 않는다. Play 설치도 접근성 권한을 자동으로 허용하지 않는다. 기기 정책이나 고급 보호 제한은 별도로 적용될 수 있다.

## 접근성 설정

RightPress 실행 → 접근성 설정 → 안내에 동의 → Android 접근성에서 RightPress 사용.
동의 없이 시스템 설정에서 먼저 서비스를 켜면 앱이 서비스를 종료한다.

직접 받은 APK에서 '제한된 설정'이 표시되면 Android 앱 정보의 '제한된 설정 허용' 경로를 확인한다. Play Protect 해제나 `isAccessibilityTool=true` 허위 선언으로 제한을 우회하지 않는다. RightPress는 일반 마우스·키보드 유틸로 `isAccessibilityTool=false`를 유지한다.

## 참고

- [Google Play 대상 API 요구사항](https://support.google.com/googleplay/android-developer/answer/11926878)
- [내부·비공개·공개 테스트](https://support.google.com/googleplay/android-developer/answer/9845334)
- [앱 서명 및 기존 키 등록](https://developer.android.com/studio/publish/app-signing)
- [접근성 API 정책](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Android 제한된 설정](https://support.google.com/android/answer/12623953)
