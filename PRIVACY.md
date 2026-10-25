# RightPress privacy notice / 개인정보 안내

Effective: 2026-10-25. Applies to RightPress 0.5.0-beta.1.

## English

RightPress is a local Android mouse and external-keyboard utility. It has no account system, advertising, analytics SDK, remote server or `INTERNET` permission. RightPress does not transmit data to its developers.

### What the accessibility service handles

- **Screen accessibility hierarchy:** window and element bounds, visibility, ordering and available actions, to find the element under the pointer and request a long-click. The implementation does not read element text, capture screenshots or retain screen content.
- **Pointer and touch events:** coordinates, buttons and movement, used temporarily to turn clicks, drags and scrolling into touch gestures.
- **Keyboard events:** key codes and modifier states, used temporarily to match shortcuts and correctly consume their key-down/key-up events. Unassigned keys pass through. RightPress does not store typed sentences, passwords or a keyboard-event history.
- **Shortcut recording:** only after you start the recorder, the selected key combination is held for editing and saved if you choose Save. It is a shortcut setting, not a typing log.
- **Launchable apps:** the app picker queries visible launcher entries and displays their names. A selected target's package identifier is saved with its shortcut. RightPress does not request `QUERY_ALL_PACKAGES`.

Accessibility permission is broad. Android may expose information through this permission beyond what this implementation uses. The in-app disclosure explains the intended use before you choose whether to enable the service.

### What stays on the device

RightPress saves mouse/keyboard on/off settings, the selected mouse action, long-press options, configured shortcut combinations/actions/target packages, and accessibility-consent state in private app preferences. It does not upload these settings. The application disables Android backup through `allowBackup="false"`.

Technical failures may create messages in Android's local system log. The app does not deliberately log input text, screen contents or shortcut activity, and has no crash-report upload service. Android and the device manufacturer control their own system diagnostics.

### Your controls

- Disable mouse conversion or keyboard shortcuts independently inside the app.
- Disable **RightPress 입력 도우미** in Android accessibility settings to withdraw service access and stop both features.
- Delete individual shortcuts in the app.
- Clear RightPress app storage or uninstall it to delete its local settings and consent state.

### Downloads, reports and contact

GitHub hosts the source, APK downloads and issue tracker. Using GitHub is separate from the app's local processing and is governed by [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

For questions about this notice, open an issue in [this repository](https://github.com/doctor-clawler/rightpress/issues). Issues and attachments are public: do not include personal information, passwords or private screen contents. For security vulnerabilities, follow [SECURITY.md](SECURITY.md).

This notice will be updated when the app's data handling changes.

## 한국어

- 대상: RightPress 0.5.0-beta.1. 적용일: 2026-10-25.
- 계정·광고·분석 SDK·원격 서버·인터넷 권한 없음. 개발자에게 데이터 전송 없음.
- 화면 접근성 정보: 요소 위치·계층·표시 상태·동작 확인에 사용. 요소 텍스트 읽기, 화면 내용 보관, 스크린샷 촬영 없음.
- 포인터·터치 정보: 좌표·버튼·이동을 터치 제스처로 변환할 때 일시 사용.
- 키보드 이벤트: 키 코드·보조키 상태를 단축키와 비교하고 누름·뗌 처리에 일시 사용. 입력 문장·비밀번호·키 입력 이력 저장 없음. 미등록 키는 원래대로 전달.
- 단축키 입력 대기: 사용자가 시작했을 때 조합을 편집용으로 보관. 저장을 선택한 조합만 설정에 저장.
- 앱 선택: 조회 가능한 실행 앱 이름 표시. 선택한 앱 패키지 식별자만 규칙에 저장. `QUERY_ALL_PACKAGES` 권한 없음.
- 로컬 저장: 기능별 ON/OFF, 우클릭 동작, 길게 누르기 옵션, 단축키 조합·동작·대상 앱, 접근성 동의 상태.
- 백업: 앱의 `allowBackup="false"` 설정. 설정 업로드 없음.
- 오류: Android 로컬 시스템 로그에 기술적 오류 메시지가 남을 수 있음. 입력 내용·화면 내용·단축키 사용 이력을 의도적으로 기록하지 않으며 오류 보고 서버로 전송하지 않음. Android·기기 제조사 자체 진단은 각 시스템에서 관리.
- 권한 범위: 접근성 권한 자체는 넓음. 앱에서 사용하는 범위와 Android가 제공할 수 있는 범위는 다를 수 있음. 사용 전 앱 내 고지 확인·동의 후 서비스 활성화.
- 중지: 앱에서 기능별 OFF 또는 Android 접근성 설정에서 **RightPress 입력 도우미** OFF.
- 삭제: 개별 단축키 삭제 가능. 앱 저장공간 삭제·앱 제거 시 로컬 설정·동의 상태 삭제.
- 문의: [GitHub Issues](https://github.com/doctor-clawler/rightpress/issues). 게시물은 공개되므로 개인정보·인증정보·비공개 화면 제외. 보안 제보는 [SECURITY.md](SECURITY.md).
- GitHub 소스·APK 다운로드·문의 기능은 앱과 별도. [GitHub 개인정보 정책](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement) 적용.
- 데이터 처리 변경 시 이 안내 갱신.
