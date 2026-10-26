# Google Play 테스트 배포

현재 Play 내부 테스트 버전: **`0.6.1-beta.1` / versionCode `9`**. 2026-10-27 01:57 KST에 Console의 **내부 테스터에게 제공됨** 상태와 **검토되지 않음** 표시를 확인했다.

[Google Play 테스트 참여](https://play.google.com/apps/internaltest/4701300851549043962). 기존 `Stack Rush App Tester Beta` 목록의 등록 계정 5개만 참여할 수 있다. GitHub APK의 최신 공개 버전은 [0.6.0-beta.1](https://github.com/doctor-clawler/rightpress/releases/tag/v0.6.0-beta.1)이다.

## 설치

1. 등록된 테스터 Google 계정으로 로그인한 상태에서 위 참여 링크를 연다.
2. **Accept invite**로 초대를 수락한 뒤 Google Play 설치 링크를 따른다.
3. RightPress를 실행하고 접근성 사용 안내를 읽고 동의한다.
4. 앱에서 Android 접근성 설정을 열어 **RightPress 입력 도우미**를 켠다.
5. 앱으로 돌아와 마우스·키보드 기능을 설정한다.

초대 페이지는 현재 임시 이름 `io.github.doctor_clawler.rightpress (unreviewed)`를 표시한다. 등록 계정으로 초대 페이지와 `Accept invite` 버튼을 확인했으며, 초대 수락이나 실제 Play 설치는 실행하지 않았다. 실물 태블릿에서의 접근성·입력 동작도 추가 검증이 필요하다.

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

Play App Signing에 **기존 앱 서명키**를 등록했다. PEPK 암호화 파일 업로드·저장 후 Console의 **앱 서명 인증서**가 기존 공식 APK 인증서와 일치함을 확인했다. AAB 업로드 인증서만 비교한 결과가 아니다.

기존 공식 APK의 앱 서명 인증서 SHA-256:

```text
28:BB:0C:A0:7D:34:46:6F:92:AD:DA:DE:BB:06:14:F9:EE:EB:52:7F:CD:1D:B8:76:4B:DA:74:C9:8F:00:EA:E0
```

키 원본·암호·암호화한 키 파일은 저장소나 GitHub 릴리스에 올리지 않는다. 향후 서명 변경 시에도 앱 서명 인증서를 비교하며, 삭제 후 재설치를 업데이트 검증의 대체로 삼지 않는다.

## 배포 검증 기록

- 제출한 AAB SHA-256: `db33c990f7d69a8aad0ea819200426af9fbafd6dae96a911a0fc59efb0a439ac`.
- Console이 AAB를 수락한 뒤 package `io.github.doctor_clawler.rightpress`, versionCode `9`, minSdk `34`, targetSdk `36`을 확인했다.
- 내부 트랙의 `0.6.1-beta.1 (9)`가 활성 상태이며 내부 테스터에게 제공됨을 확인했다.
- `Stack Rush App Tester Beta` 5계정 목록만 선택되어 있고, 다른 목록은 미선택이며 저장 버튼이 비활성화된 저장 상태를 확인했다.
- Console에서 발급한 참여 링크를 등록 계정으로 열어 초대 페이지가 표시됨을 확인했다. 명단 등록·사용자 참여·실제 설치는 각각 별도 단계다.

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
