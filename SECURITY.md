# Security

## Supported version

Only the latest published beta receives fixes. This is an early public beta; response times are not guaranteed.

## Report a vulnerability

Use [Security → Report a vulnerability](https://github.com/doctor-clawler/rightpress/security/advisories/new) for a private report when available. Include the affected version, impact and minimal reproduction steps. Remove credentials, personal data and unrelated device logs.

If private reporting is unavailable, open a public issue requesting a private contact route **without exploit details or sensitive information**. Do not publish an unfixed vulnerability's reproduction steps in a normal issue.

## Trust boundary

RightPress uses Android accessibility access to handle pointer input and global keyboard shortcuts. Install APKs only from a source you trust. Official beta APKs are attached to GitHub Releases; Actions artifacts and locally built debug APKs use different signing keys.

Release keys and passwords are not stored in this repository or CI. Disabling the RightPress accessibility service stops its input handling. See [PRIVACY.md](PRIVACY.md) for data handling.

## 한국어

- 지원: 가장 최근 공개 베타. 대응 시간 보장 없음.
- 취약점: [Security → Report a vulnerability](https://github.com/doctor-clawler/rightpress/security/advisories/new)에서 비공개 제보.
- 메뉴가 없으면 상세 내용 없이 비공개 연락 경로만 Issues로 요청.
- 일반 Issues에 미수정 취약점 재현 방법·인증정보·개인정보 게시 금지.
- 앱 버전·영향·최소 재현 단계 포함. 불필요한 기기 로그 제외.
- 공식 베타 APK는 Releases에서 확인. Actions·직접 빌드한 debug APK와 서명 다름.
