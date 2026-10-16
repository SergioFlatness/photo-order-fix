# Photo Order Fix

Moved from iPhone to Android (for example with Samsung Smart Switch) and now Instagram,
KakaoTalk or other apps show your photos oldest-first or jumbled? This Android app fixes that.

[한국어 설명은 아래에 있어요](#한국어)

**Download the latest test build:**
[photo-order-fix.apk](https://github.com/SergioFlatness/photo-order-fix/releases/download/latest/photo-order-fix.apk)
(open the link on your Android phone, no GitHub account needed)

## Why it happens

Android keeps several dates for every photo:

| Date | Stored where | Who sorts by it |
|---|---|---|
| Taken (capture) date | Inside the file (EXIF / video header) | Samsung Gallery, Google Photos |
| Date added | Android's media list | Instagram and many other in-app pickers |
| Date modified | File system | Some apps |

A bulk transfer adds thousands of files within minutes, and often newest first.
So in apps that sort by *date added*, the transferred photos come out reversed or jumbled,
while your gallery still looks fine.

## What the app does

1. Finds each item's real date: capture info first, then a date in the file name
   (`IMG_20240315_143022.jpg`, `Screenshot_…`, WhatsApp names), then the file time.
2. Plans the minimum work: the oldest stretch that is already in order is left alone.
3. Re-saves the rest, photos and videos together, oldest first, with byte-identical content,
   so the newest lands on top in every app.
4. Asks Android to delete the originals (one confirmation per batch). Declining removes the
   copies, so nothing is duplicated.

Screenshots without a capture date get the date written into the copy (JPEG, PNG, WebP),
so it survives future moves.

Everything runs on the phone. No internet permission, nothing is uploaded.

Requires Android 11 or newer.

## Build

```
./gradlew -p core test        # date and ordering logic, plain JVM tests
./gradlew :app:assembleDebug  # the app
```

Every push builds an APK on GitHub Actions (see the **Actions** tab, artifact
`photo-order-fix-debug-apk`).

## Status

Early version. Next up: restoring iPhone crops and edits from iCloud downloads.

---

## 한국어

아이폰에서 갤럭시로 옮긴 뒤 인스타그램, 카카오톡 같은 앱에서 사진이 오래된 순으로 보이거나
뒤죽박죽이라면 이 앱이 고쳐 줘요.

**최신 테스트 버전 받기:** 안드로이드 폰에서
[photo-order-fix.apk](https://github.com/SergioFlatness/photo-order-fix/releases/download/latest/photo-order-fix.apk)
링크를 열면 바로 받아져요. GitHub 계정이 없어도 돼요.

- **원인**: 이런 앱들은 "찍은 날짜"가 아니라 "폰에 들어온 날짜" 순으로 정렬해요. 한꺼번에 옮기면
  최신 사진부터 들어와서 순서가 뒤집혀요. 갤러리는 찍은 날짜를 보기 때문에 멀쩡해 보여요.
- **해결**: 사진과 동영상의 진짜 날짜를 찾아서 오래된 것부터 똑같은 내용으로 다시 저장해요.
  원본 삭제는 안드로이드가 매번 물어보고, 거절하면 아무것도 바뀌지 않아요.
- **개인정보**: 모든 작업은 폰 안에서만 해요. 인터넷 권한이 없고 아무것도 업로드하지 않아요.

## License

MIT
