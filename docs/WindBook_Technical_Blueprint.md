# WindBook – Technical Implementation Blueprint

PRD v1.0 · Android Native · Offline-first · Personal Use → Commercial Product

Tôi đề xuất xây dựng WindBook như một sản phẩm Android thực tế, không phải ứng dụng demo. Phiên bản đầu tiên sẽ tập trung vào trải nghiệm đọc EPUB/PDF, quản lý thư viện, ghi chú và cá nhân hóa. Kiến trúc phải đủ tốt để sau này phát triển thêm AI Reading Assistant, đồng bộ đa thiết bị và thương mại hóa.

## 1. Các quyết định kiến trúc quan trọng

## WindBook

Your books. Your reading space.

Architecture proposed

Nền tảng

Android Native

Ngôn ngữ

Kotlin

UI Framework

Jetpack Compose

Kiến trúc

Clean + MVVM

Database

Room / SQLite

Cloud

Không bắt buộc

Nguyên tắc: Đọc sách hoàn toàn offline, dữ liệu thuộc về người dùng, quyền truy cập file tối thiểu, không yêu cầu tài khoản và không phụ thuộc backend.

Phạm vi phát hành đầu tiên: Android 10 trở lên, ưu tiên điện thoại, tablet ở giai đoạn sau. Đây là mục tiêu sản phẩm, không phải khẳng định mọi phiên bản thư viện đều tương thích ngay mà không cần kiểm thử.

Tôi chọn Kotlin vì các ứng dụng tiếp theo như VaultPlayer và CallShield sẽ cần truy cập Android system API tương đối sâu. Compose giúp xây dựng design system chung để tái sử dụng cho MoneyFlow, ExamMaster và EduTeach.

## 2. PRD – Product Requirements Document

### 2.1. Vấn đề cần giải quyết

Các ứng dụng đọc sách hiện nay có nhiều chức năng nhưng trải nghiệm quản lý thư viện, ghi chú, đồng bộ, AI và cá nhân hóa thường phân tán.

WindBook hướng tới một ứng dụng đọc sách mà người dùng có thể kiểm soát toàn bộ dữ liệu, giao diện và quy trình học từ sách.

Đối tượng ban đầu: chính bạn, người đọc sách kỹ thuật, tài liệu tiếng Anh, ebook học tập và tài liệu PDF. Khi thương mại hóa, có thể mở rộng tới sinh viên, giáo viên và người đọc chuyên sâu.

### 2.2. Phân cấp yêu cầu

| ID    | Tính năng         | Mức độ | Tiêu chí hoàn thành             |
| ----- | ----------------- | ------ | ------------------------------- |
| FR-01 | Import EPUB       | P0     | Chọn và mở file hợp lệ          |
| FR-02 | Import PDF        | P0     | Hiển thị và điều hướng trang    |
| FR-03 | Thư viện sách     | P0     | Danh sách, tìm kiếm, sắp xếp    |
| FR-04 | EPUB Reader       | P0     | Chỉnh font, cỡ chữ, theme       |
| FR-05 | Reading Progress  | P0     | Khôi phục vị trí đọc            |
| FR-06 | Bookmark          | P0     | Thêm/xóa/đi tới vị trí          |
| FR-07 | Highlight & Notes | P1     | Ghi chú gắn với vị trí          |
| FR-08 | TTS               | P1     | Đọc văn bản bằng giọng hệ thống |
| FR-09 | Full-text Search  | P1     | Tìm trong nội dung EPUB         |
| FR-10 | Backup/Restore    | P1     | Khôi phục dữ liệu chính xác     |
| FR-11 | PDF Annotation    | P2     | Highlight/note trên PDF         |
| FR-12 | AI Assistant      | P2     | Hỏi đáp có trích dẫn nội dung   |
| FR-13 | Cloud Sync        | P2     | Đồng bộ có xử lý xung đột       |

P0 là điều kiện bắt buộc của MVP, P1 là phiên bản 1.x và P2 là giai đoạn mở rộng. Không nên hứa hỗ trợ toàn bộ định dạng ebook ngay từ v1.0.

### 2.3. Non-functional requirements

| Nhóm            | Mục tiêu nghiệm thu ban đầu                        |
| --------------- | -------------------------------------------------- |
| Startup         | Cold start mục tiêu dưới 2 giây trên máy thử chuẩn |
| Import          | EPUB 20 MB mục tiêu dưới 3 giây                    |
| Reader          | Cuộn/chuyển trang mượt, không giật kéo dài         |
| Reliability     | Không mất vị trí đọc khi app bị kill               |
| Offline         | Toàn bộ P0 hoạt động không Internet                |
| Privacy         | Không upload sách nếu chưa có hành động đồng ý     |
| Security        | Không lưu khóa mã hóa dưới dạng plaintext          |
| Accessibility   | Hỗ trợ font scaling và TalkBack ở màn hình chính   |
| Maintainability | Test domain logic, migration và dữ liệu quan trọng |

Các ngưỡng hiệu năng là SLO dự kiến, cần đo bằng benchmark trên thiết bị thật, không phải cam kết đã được kiểm chứng.

## 3. Architecture – Thiết kế hệ thống

### 3.1. Luồng dữ liệu

Jetpack Compose UI

Library · Reader · Book Details · Settings

ViewModel + StateFlow

UI State · User Events · Navigation

Domain Layer

Use Cases · Repository Interfaces · Business Rules

Data & Reader Adapters

Room · DataStore · Readium · File Storage

SQLite

EPUB / PDF

Android SAF

Tôi đề xuất Clean Architecture thực dụng, không tạo quá nhiều abstraction khi ứng dụng còn nhỏ.

- `UI → ViewModel → UseCase → Repository interface`
- Repository implementation làm việc với Room, Readium và Android Storage.
- ViewModel không trực tiếp truy vấn database hay xử lý EPUB.
- Reader engine được bọc sau một interface để có thể thay thế hoặc nâng cấp mà không phá vỡ domain layer.

### 3.2. Tích hợp Readium

Tôi chọn Readium Kotlin Toolkit làm ebook engine.

Theo tài liệu hiện hành, Readium 3.4.0 hỗ trợ EPUB 2/3, PDF và các thành phần như Streamer, Navigator, TTS. Các navigator dạng Compose mới vẫn đang ở trạng thái thử nghiệm, vì vậy v1 nên dùng `EpubNavigatorFragment` hoặc navigator ổn định được tài liệu khuyến nghị, rồi tích hợp vào Compose qua `AndroidViewBinding` hoặc `FragmentContainerView`.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://readium.org\&sz=32)

readium.org

+2



Không nên viết EPUB rendering engine từ đầu. Nó đòi hỏi xử lý HTML/CSS, pagination, CFI, fixed layout, font embedding và rất nhiều trường hợp tương thích.

Quy trình mở sách:

```
User selects file
    ↓
ACTION_OPEN_DOCUMENT
    ↓
Validate MIME / file signature / size
    ↓
Copy into app-owned storage
    ↓
Calculate SHA-256
    ↓
Readium PublicationOpener
    ↓
Extract metadata & cover
    ↓
Save BookEntity in Room
    ↓
Open Navigator
    ↓
Restore saved Locator
```

Tôi ưu tiên copy file vào bộ nhớ riêng của app ở MVP. Điều này giúp app vẫn đọc được khi file nguồn bị di chuyển hoặc quyền URI thay đổi. Đổi lại, ứng dụng phải có tính năng kiểm soát dung lượng và xóa bản sao rõ ràng.

### 3.3. Module breakdown

```
windbook/
├── app/
├── core/
│   ├── common/
│   ├── model/
│   ├── database/
│   ├── datastore/
│   ├── designsystem/
│   ├── file/
│   └── testing/
├── feature/
│   ├── library/
│   ├── importbook/
│   ├── reader/
│   ├── bookmarks/
│   ├── annotations/
│   ├── search/
│   ├── settings/
│   └── backup/
└── reader/
    ├── api/
    └── readium/
```

| Module                | Trách nhiệm                                   |
| --------------------- | --------------------------------------------- |
| `app`                 | Application, dependency injection, navigation |
| `core:model`          | Các domain model dùng chung                   |
| `core:database`       | Room entities, DAO, migration                 |
| `core:designsystem`   | Theme, typography, reusable UI                |
| `core:file`           | Import, checksum, file lifecycle              |
| `feature:library`     | Bookshelf, search, sorting                    |
| `feature:reader`      | Reader UI, controls, preferences              |
| `reader:api`          | Interface của reading engine                  |
| `reader:readium`      | Adapter tích hợp Readium                      |
| `feature:annotations` | Highlight, notes, bookmarks                   |
| `feature:backup`      | Export, import, integrity check               |

Không cần tách tất cả thành Gradle module ngay Sprint 1. Có thể bắt đầu với `app`, `core:database`, `reader:readium` và mở rộng khi dependency boundaries ổn định.

## 4. Database Schema – Room/SQLite

### 4.1. Entity Relationship Diagram

Một sách có nhiều bookmark, annotation và reading session; một bản ghi tiến độ hiện tại trên mỗi sách.

### 4.2. Các bảng chính

books

| Column         | Type     | Mô tả                |
| -------------- | -------- | -------------------- |
| id             | TEXT PK  | UUID                 |
| title          | TEXT     | Tên sách             |
| author         | TEXT     | Tác giả              |
| format         | TEXT     | EPUB/PDF             |
| storage_path   | TEXT     | Đường dẫn nội bộ     |
| sha256         | TEXT     | Phát hiện file trùng |
| cover_path     | TEXT?    | Ảnh bìa              |
| added_at       | INTEGER  | Unix timestamp       |
| last_opened_at | INTEGER? | Lần mở gần nhất      |

reading_progress

| Column       | Type       | Mô tả              |
| ------------ | ---------- | ------------------ |
| book_id      | TEXT PK/FK | Sách               |
| locator_json | TEXT       | Readium Locator    |
| percentage   | REAL       | Tiến độ 0–1        |
| updated_at   | INTEGER    | Thời gian cập nhật |

bookmarks

| Column       | Type    |
| ------------ | ------- |
| id           | TEXT PK |
| book_id      | TEXT FK |
| locator_json | TEXT    |
| label        | TEXT?   |
| created_at   | INTEGER |

annotations

| Column        | Type    |
| ------------- | ------- |
| id            | TEXT PK |
| book_id       | TEXT FK |
| locator_json  | TEXT    |
| selected_text | TEXT?   |
| note          | TEXT?   |
| color         | TEXT    |
| created_at    | INTEGER |
| updated_at    | INTEGER |

reading_sessions

| Column           | Type     |
| ---------------- | -------- |
| id               | TEXT PK  |
| book_id          | TEXT FK  |
| started_at       | INTEGER  |
| ended_at         | INTEGER? |
| duration_seconds | INTEGER  |

Chúng ta lưu Readium Locator dưới dạng JSON vì số trang EPUB có thể thay đổi theo font, màn hình và chế độ hiển thị. Locator là định danh vị trí đọc thích hợp hơn số trang tuyệt đối.

### 4.3. Index và ràng buộc

```
CREATE UNIQUE INDEX idx_books_sha256
ON books(sha256);

CREATE INDEX idx_bookmarks_book
ON bookmarks(book_id);

CREATE INDEX idx_annotations_book
ON annotations(book_id);

CREATE INDEX idx_books_recent
ON books(last_opened_at DESC);
```

Các khóa ngoại phải bật và có chính sách `ON DELETE CASCADE` cho dữ liệu phụ thuộc. File ebook thực tế không nằm trong SQLite; database chỉ lưu metadata và đường dẫn.

## 5. UI/UX – Screen Design

Tôi đề xuất phong cách minimal reading-first, hỗ trợ Material 3, dark mode, sepia mode và typography dễ đọc.

[阅读书架 App | GPT Images](https://images.openai.com/static-rsc-4/ih8sQDCMBopeKGZDf5eQ2BfRlv1gQJIgKd3Jl7ug5cCXFZKpB3jmG2BzIBz4Dtm1mc0k3ZhxCAxRAJUnMCkirZqgnvfmDK2sjuq9ISUCH4nwXgpAF7q5U-9K-Eit1SMMc-JXAuaSpd6HLeSmqHjDc_2Y6-QgIAWt9hg0SQNKo88?purpose=inline)

01\. Library

Continue Reading, Recently Added, Collections, Search và Import.

[The Best Reading App for eBooks – Kobo | Rakuten Kobo Australia](https://images.openai.com/static-rsc-4/TQPtd5ZAeJc4qtUBMUxXtpb3OFcSoUDMNIg8iXsRpmAk6Vih-9XT-w-F6jZyOmVv3weI4rM8d0w_3Pt9Is8QqYvJl-ZRzSDJCKV0Ht2f6K2bzc0Sv_BqAWpXxpXdQ_hBzINACP68poxOljRdnIwgJiyRzMHtdmTSOTkMlW0-Hs4?purpose=inline)

02\. Reader

Không gian đọc tập trung, toolbar ẩn/hiện, điều chỉnh font và theme.

[KyBook 3 Ebook Reader Info on United States App Store - App Marketing Analytics - FoxData](https://images.openai.com/static-rsc-4/GJJAC2ln_1gzsQ2UdvpCNicLjgtqanLrCqIzyGNtnAhh93AT__nOWrvpkcOttFs2DhFlxz3Ay6hkuPkyZ9Ov4YBtL57f8R5Hg-M__cDVwdJ0BmjDodTyJxBPubHrRJ56h97P3K_Wolg1tcA3I0Pqa3cUbWvfkpzsuUet43tgZbo?purpose=inline)

03\. Table of Contents

Danh sách chương, bookmark và vị trí hiện tại.

[Herramientas de formato  |  Gallery  |  Android Developers](https://images.openai.com/static-rsc-4/e2Zpr-X-Sr3eZeKw26y8fnqsyQFl6-vZzVAT3VVRMPjLfDZd5qpqFyAG5k6krdpBw-BKV8NdO4QXnz4inRbPzJEnYDoxwFMkjIF5M9PZTeJ52aiAl6rNASx-AZDBFgOBcq3i_tNcodW6Yo7_tPWc0xVsxYXRSn4mU-wHL6HMX7I?purpose=inline)

04\. Notes

Highlight, ghi chú, tìm lại nội dung và xuất ghi chú.

Đây là tham chiếu phong cách giao diện, không phải mockup chính thức của WindBook.

### Screen map

```
WindBook
├── Library
│   ├── All Books
│   ├── Continue Reading
│   ├── Collections
│   └── Search
├── Book Details
│   ├── Metadata
│   ├── Reading Progress
│   └── Open Book
├── Reader
│   ├── Reading Surface
│   ├── Table of Contents
│   ├── Bookmarks
│   ├── Typography Settings
│   └── Notes
└── Settings
    ├── Appearance
    ├── Reading Preferences
    ├── Storage
    └── Backup
```

### Reader interaction

- Tap giữa màn hình: hiện/ẩn toolbar.
- Tap mép trái/phải: chuyển trang khi ở chế độ paginated.
- Long press: chọn văn bản và hiển thị action menu.
- Swipe: cuộn hoặc chuyển trang tùy chế độ.
- Bottom progress: hiển thị tiến độ và điều hướng.
- Reader settings: font, line height, margins, theme, scroll mode.

Cần tránh tự xử lý gesture trùng với gesture hệ thống Android và gesture của Readium.

## 6. Open-source libraries – Danh sách thư viện

| Nhu cầu              | Thư viện                                                                              | Vai trò                     |
| -------------------- | ------------------------------------------------------------------------------------- | --------------------------- |
| EPUB/PDF engine      | [Readium Kotlin](https://github.com/readium/kotlin-toolkit)                           | Parse, render, locator, TTS |
| UI                   | [Jetpack Compose](https://developer.android.com/jetpack/compose)                      | Native declarative UI       |
| Local database       | [Room](https://developer.android.com/training/data-storage/room)                      | SQLite persistence          |
| Preferences          | [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)     | Theme, settings             |
| Dependency Injection | [Hilt](https://dagger.dev/hilt/)                                                      | Dependency management       |
| Background tasks     | [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) | Backup, indexing            |
| Image loading        | [Coil](https://coil-kt.github.io/coil/)                                               | Book covers                 |
| Unit testing         | [JUnit](https://junit.org/)                                                           | Domain tests                |
| UI testing           | [Compose UI Test](https://developer.android.com/develop/ui/compose/testing)           | Interaction tests           |
| Memory analysis      | [LeakCanary](https://square.github.io/leakcanary/)                                    | Detect memory leaks         |

Quan trọng: Readium 3.4.0 được phát hành tháng 9/2026 và yêu cầu kiểm tra tương thích giữa Kotlin, Gradle, Android Gradle Plugin và compile SDK. Tài liệu Readium cũng yêu cầu bật core library desugaring.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://readium.org\&sz=32)

readium.org

+1



Đối với ứng dụng thương mại, cần lập bảng kiểm kê license của từng dependency và transitive dependency. Đặc biệt kiểm tra PDFium, font, TTS engine và thư viện xử lý nội dung trước khi phân phối.

## 7. Sprint backlog – Kế hoạch 8 tuần

Tôi chia giai đoạn đầu thành 4 sprint, mỗi sprint 2 tuần.

Sprint 1 · Tuần 1–2

### Foundation & Import

1/4

Tạo project Android, Gradle, Git repository

Thiết lập Compose + Material 3 + Hilt

Thiết kế Room entities, DAO, migration

Implement ACTION_OPEN_DOCUMENT

Copy file, checksum SHA-256, kiểm tra trùng

Tạo Library Screen

Definition of Done: Import EPUB/PDF và nhìn thấy sách trong thư viện

Sprint 2 · Tuần 3–4

### Reading Engine

2/4

Tích hợp Readium PublicationOpener

Tích hợp EPUB Navigator

Tích hợp PDF Navigator

Lưu/khôi phục Locator

Reader toolbar, font, theme, pagination

Xử lý file lỗi và vòng đời Activity

Definition of Done: Đọc EPUB/PDF offline, mở lại đúng vị trí

Sprint 3 · Tuần 5–6

### Reading Experience

3/4

Bookmark và chapter navigation

Highlight và ghi chú EPUB

Search nội dung EPUB

Book Details và sort/filter

TTS cho EPUB

Accessibility và UI polish

Definition of Done: Trải nghiệm đọc đủ dùng hằng ngày

Sprint 4 · Tuần 7–8

### Hardening & Release

4/4

Backup/restore database và file

Unit, integration, UI tests

Test với bộ ebook đa dạng

Performance profiling, memory leak testing

Release signing và CI/CD

Cài đặt APK trên thiết bị thật

Definition of Done: WindBook v0.1.0 signed APK, có test report

Nếu muốn rút ngắn tiến độ, bạn có thể phát hành bản alpha ngay sau Sprint 2. Sprint 3–4 hoàn thiện trải nghiệm và độ tin cậy.

## 8. Testing strategy

| Test layer  | Công cụ                  | Kiểm thử                           |
| ----------- | ------------------------ | ---------------------------------- |
| Unit        | JUnit, MockK             | UseCase, progress, metadata        |
| Database    | Room Test                | DAO, FK, migration                 |
| Integration | Android Test             | Import + Readium + Room            |
| UI          | Compose UI Test          | Library, navigation, settings      |
| Device      | Emulator + điện thoại    | Rotation, background, kill process |
| Performance | Macrobenchmark           | Startup, frame timing, import      |
| Security    | Static analysis + manual | URI permissions, backup, secrets   |

### Test cases quan trọng

1. Import cùng một EPUB hai lần → không tạo sách trùng ngoài ý muốn.
2. Đọc đến 37%, đóng app cưỡng bức → mở lại đúng vị trí đã lưu gần nhất.
3. Đổi font size → nội dung không mất, locator vẫn hợp lệ.
4. Import file giả EPUB hoặc ZIP lỗi → app báo lỗi, không crash.
5. Import file rất lớn → không đọc toàn bộ vào RAM.
6. Xóa sách → metadata, annotation và file bản sao được xử lý nhất quán.
7. Backup rồi restore trên máy khác → sách, bookmark, notes và progress được khôi phục.
8. EPUB có HTML/CSS không tin cậy → không được tự ý truy cập file riêng, Internet hoặc thực thi chức năng ngoài sandbox được phép.

Với file EPUB/PDF không đáng tin cậy, cần giới hạn kích thước, kiểm tra ZIP bomb, path traversal và tránh bật các quyền WebView không cần thiết.

## 9. Hướng dẫn tạo và build APK trên Linux

Phần này là quy trình triển khai thực tế cho môi trường Linux. Các lệnh giả định bạn đã cài Android Studio, Android SDK và JDK tương thích với phiên bản Android Gradle Plugin của dự án.

### Bước 1 – Tạo project

Mở Android Studio:

`New Project → Empty Activity`

Thiết lập:

| Trường              | Giá trị              |
| ------------------- | -------------------- |
| Name                | WindBook            |
| Package             | `dev.kent.windbook` |
| Language            | Kotlin               |
| Minimum SDK         | API 29 (Android 10)  |
| Build configuration | Kotlin DSL           |
| UI                  | Jetpack Compose      |

Package name chỉ là ví dụ; nên chọn application ID cố định mà bạn kiểm soát trước khi phân phối.

### Bước 2 – Tạo Git repository

Trong terminal:

```
cd ~/AndroidStudioProjects/WindBook

git init
git branch -M main

git add .
git commit -m "chore: initialize WindBook"
```

Tạo `.gitignore` bao gồm ít nhất:

```
.gradle/
.idea/
build/
**/build/
local.properties
*.jks
*.keystore
keystore.properties
.env
```

Không commit release keystore hoặc mật khẩu lên GitHub.

### Bước 3 – Cấu hình Readium

Trong `app/build.gradle.kts`, thêm các dependency Readium cần thiết vào block `dependencies` hiện có:

```
dependencies {
    val readiumVersion = "3.4.0"

    implementation(
        "org.readium.kotlin-toolkit:readium-shared:$readiumVersion"
    )

    implementation(
        "org.readium.kotlin-toolkit:readium-streamer:$readiumVersion"
    )

    implementation(
        "org.readium.kotlin-toolkit:readium-navigator:$readiumVersion"
    )
}
```

Đây là phần dependency cần ghép vào project Android Studio đã tạo, chưa phải file Gradle hoàn chỉnh. Cần cấu hình thêm PDF adapter, desugaring, repositories và các dependency của Compose/Room theo tài liệu tương thích.

Tài liệu tích hợp: [Readium Kotlin Toolkit](https://readium.org/kotlin-toolkit/latest/).

### Bước 4 – Build debug APK

Từ thư mục gốc dự án:

```
chmod +x gradlew

./gradlew clean
./gradlew :app:assembleDebug
```

Nếu build thành công, APK thường nằm tại:

```
app/build/outputs/apk/debug/app-debug.apk
```

Kiểm tra:

```
ls -lh app/build/outputs/apk/debug/
```

Debug APK được Android build tools ký bằng debug key để thử nghiệm, không nên dùng làm bản phát hành chính thức.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://developer.android.google.cn\&sz=32)

Android Developers

+1



### Bước 5 – Cài lên smartphone

Bật Developer Options và USB Debugging trên điện thoại.

Kết nối USB, sau đó:

```
adb devices
```

Nếu thiết bị được nhận:

```
adb install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

Khởi động:

```
adb shell monkey \
  -p dev.kent.windbook 1
```

Xem log:

```
adb logcat -s AndroidRuntime:E
```

Nếu app mở được, đây mới là kiểm thử smoke test. Bạn vẫn cần import sách thật và thử toàn bộ luồng đọc.

### Bước 6 – Tạo release APK có chữ ký

Dùng Android Studio:

`Build → Generate Signed Bundle / APK → APK`

Tạo một keystore riêng, giữ bản sao an toàn và không chia sẻ private key.

Hoặc tạo bằng `keytool`:

```
keytool -genkeypair \
  -v \
  -keystore windbook-release.jks \
  -alias windbook \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000
```

Sau đó cấu hình Gradle `signingConfigs` sử dụng biến môi trường hoặc file bí mật ngoài Git, rồi chạy:

```
./gradlew :app:assembleRelease
```

Lệnh trên chỉ tạo APK đã ký nếu bạn cấu hình signing cho release. Nếu chưa, hãy sử dụng wizard Generate Signed APK.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://developer.android.google.cn\&sz=32)

Android Developers

+1



Kiểm tra chữ ký:

```
apksigner verify --verbose \
  app/build/outputs/apk/release/app-release.apk
```

Công cụ `apksigner` thường nằm trong thư mục `build-tools/<version>` của Android SDK; có thể cần dùng đường dẫn đầy đủ.

## 10. CI/CD – GitHub Actions

Tôi đề xuất pipeline hai giai đoạn: CI cho mọi pull request và CD khi tạo release tag.

Push / Pull Request

Lint

Unit Tests

Build APK

CI Artifacts – Debug APK

Release tag → Sign → Verify → Publish

Tạo `.github/workflows/android-ci.yml`:

```
name: Android CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest

    permissions:
      contents: read

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: android-actions/setup-android@v3

      - name: Gradle permissions
        run: chmod +x gradlew

      - name: Lint and unit tests
        run: |
          ./gradlew :app:lintDebug
          ./gradlew :app:testDebugUnitTest

      - name: Build debug APK
        run: ./gradlew :app:assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: windbook-debug
          path: app/build/outputs/apk/debug/*.apk
```

Đây là pipeline khởi đầu. Bạn cần chốt và kiểm tra phiên bản JDK, Gradle, Android SDK và GitHub Actions trước khi chạy. Chẳng hạn, nếu dùng toolchain mới hơn của Readium, JDK 17 có thể không còn phù hợp.

Release pipeline nên được thiết kế riêng với các điều kiện:

- Chỉ chạy khi push tag dạng `v*`.
- Tất cả test bắt buộc phải pass.
- Lấy keystore và mật khẩu từ GitHub Secrets.
- Ký APK và kiểm tra chữ ký.
- Tạo SHA-256 checksum.
- Đính kèm APK vào GitHub Release.
- Không upload secrets hoặc keystore làm artifact.

Đối với ứng dụng cá nhân, bạn có thể tải signed APK từ GitHub Release rồi cài trực tiếp. Khi thương mại hóa, bổ sung build AAB và quy trình Play Console.

## 11. Chuẩn bị cho thương mại hóa

Không nên thêm quảng cáo hoặc subscription ngay vào MVP. Trước tiên cần xác định WindBook có đem lại trải nghiệm đọc tốt hơn các ứng dụng hiện có ở điểm nào.

Tôi đề xuất lộ trình:

| Phiên bản | Trọng tâm                             | Mô hình               |
| --------- | ------------------------------------- | --------------------- |
| v0.1–0.3  | Đọc EPUB/PDF, library                 | Cá nhân               |
| v0.4–0.9  | Annotation, TTS, backup               | Closed beta           |
| v1.0      | Stable reader, privacy, accessibility | Miễn phí              |
| v1.5      | Advanced notes, export, customization | Pro một lần           |
| v2.0      | AI Assistant, cloud sync              | Subscription tùy chọn |

Một lợi thế có thể phát triển là WindBook Research Mode: đọc tài liệu tiếng Anh, tra cứu khái niệm, ghi chú có trích dẫn và chuyển nội dung thành flashcard. Điều này phù hợp với người học chuyên sâu, nhưng phải kiểm chứng nhu cầu thực tế trước khi xây dựng.

## 12. Quyết định kỹ thuật cần khóa trước Sprint 1

Chiến lược lưu trữ sách

Copy vào app storage (khuyến nghị)

Giữ URI của file gốc qua SAF

Phạm vi PDF trong MVP

Đọc, zoom, điều hướng trang

Thêm annotation và tìm kiếm PDF

Đồng bộ dữ liệu

Offline-first, backup thủ công trước

Thiết kế cloud sync ngay từ đầu

Chốt kiến trúc và bắt đầu Sprint 1&#x20;

Kết luận: WindBook nên được xây dựng theo hướng một Android app offline-first, sử dụng Readium làm reading engine, Room quản lý dữ liệu và Kotlin/Compose cho UI. Sau khi bản đầu tiên hoạt động ổn định, bạn sẽ có một nền tảng kỹ thuật thực tế để tái sử dụng cho các ứng dụng còn lại.

Điểm quan trọng nhất trong Sprint 1 không phải giao diện đẹp, mà là hoàn thành một luồng end-to-end: import EPUB → lưu thư viện → mở sách → lưu tiến độ → đóng app → mở lại đúng vị trí. Khi luồng này được kiểm thử thành công trên điện thoại thật, WindBook đã có nền móng sản phẩm.