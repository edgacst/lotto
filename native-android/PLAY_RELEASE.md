# Google Play 등록 가이드

## 1. 업로드 파일 (이미 빌드됨)

| 파일 | 경로 |
|------|------|
| **AAB (업로드용)** | `app/build/outputs/bundle/release/app-release.aab` |
| 버전 | `1.0.2` (versionCode 3) |
| 패키지명 | `com.luckypick.app` |

## 2. 스토어 그래픽

| 항목 | 파일 |
|------|------|
| 앱 아이콘 512×512 | `play-store/icon-512.png` |
| 피처 그래픽 1024×500 | `play-store/feature-graphic.png` |
| 스토어 문구 | `play-store/store-listing-ko.txt` |
| 개인정보처리방침 HTML | `play-store/privacy-policy.html` |

> **개인정보처리방침 URL:** https://edgacst.github.io/lotto/privacy-policy.html  
> GitHub Pages 활성화: 저장소 Settings → Pages → Source: `main` / root

## 3. Play Console 등록 순서

1. [Google Play Console](https://play.google.com/console) 접속
2. **개발자 계정 등록** (1회 등록비 약 $25)
3. **앱 만들기** → 앱 이름: `십이지신이 추천하는, 행운의 로또번호`
4. **프로덕션** (또는 내부 테스트) → **새 버전 만들기**
5. `app-release.aab` 업로드
6. **스토어 설정 → 기본 스토어 등록정보**
   - 짧은 설명 / 전체 설명: `store-listing-ko.txt` 참고
   - 앱 아이콘: `icon-512.png`
   - 피처 그래픽: `feature-graphic.png`
   - 연락처 이메일 입력
7. **앱 콘텐츠**
   - 개인정보처리방침 URL 입력
   - 광고 포함 여부: 예 (베니사운드 배너)
   - 데이터 보안 설문 작성 (아래 참고)
   - 콘텐츠 등급 설문 (IARC)
   - 타겟 연령층 설정
8. **검토 제출**

## 4. 데이터 보안 설문 (참고)

| 데이터 | 수집 | 공유 | 목적 |
|--------|------|------|------|
| 생년월일시 | 예 (입력) | 아니오 | 앱 기능(번호 생성) |
| 저장/생성 기록 | 예 | 아니오 | 앱 기능 |
| 위치 | 예 (선택, 권한) | 아니오 | 주변 판매점 찾기 |
| 사진/카메라 | 예 (선택, QR) | 아니오 | QR 스캔 |

- 데이터는 **기기 내부**에 저장
- **암호화 전송**: 당첨번호/판매점 조회 시 HTTPS 사용
- **삭제**: 앱 삭제 시 기기 데이터 삭제

## 5. 서명 키 (중요)

- 업로드 키스토어: `keystore/luckypick-upload.jks`
- 설정: `keystore.properties`

> 출시 전 `keystore.properties`의 기본 비밀번호 `change-this-password`를 **반드시** 변경하고 키스토어를 안전하게 백업하세요.  
> 키를 잃으면 이후 업데이트가 불가능합니다.

## 6. 빌드 명령 (다음 버전부터)

```powershell
cd native-android
C:\Users\USER\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat bundleRelease
```

업로드 전 `app/build.gradle`의 `versionCode`를 이전보다 크게 올리세요.
