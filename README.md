# LiteTube A7 — playback core

Mục tiêu của bản này:
- Không dùng WebView/YouTube Player chính thức.
- Phát stream bằng AndroidX Media3.
- Chọn 360p / 480p / 720p / 1080p.
- 720p là mặc định để hợp Galaxy A7 2018, nhưng KHÔNG khóa 720p.
- URL dạng `/shorts/` bị chặn.
- Tắt màn hình: chuyển sang audio-only nhưng vẫn tiếp tục phát.
- Bật màn hình: quay lại video tại vị trí gần tương ứng.
- Nhận URL qua Android Share.

## Cách mở
1. Mở thư mục bằng Android Studio.
2. Dùng JDK 17.
3. Sync Gradle.
4. Build APK.
5. Cài lên máy.

## Lưu ý kỹ thuật
NewPipe Extractor là GPL-3.0-or-later. Nếu phân phối ứng dụng có liên kết thư viện này,
cần tuân thủ giấy phép tương ứng và cung cấp source theo điều kiện GPL.

Đây là "playback core", chưa giả vờ rằng OAuth/lịch sử/đề xuất đã hoàn thiện.
Google OAuth cần OAuth Client ID của chính dự án Google Cloud của bạn.
YouTube Data API không cung cấp lịch sử xem đầy đủ hay Home recommendation giống app YouTube.

## Điểm có thể phải cập nhật
YouTube thường xuyên thay đổi cơ chế stream. Khi extraction lỗi, ưu tiên nâng
NewPipeExtractor lên bản stable mới nhất trước khi sửa player.

## Shorts
Bản này chặn URL `/shorts/`. Khi bổ sung Home/Search, nên áp dụng thêm:
- Không hiển thị shelf Shorts.
- Không hiển thị item nhận diện là Shorts.
- Có tùy chọn "lọc video <= 180 giây" riêng, không nên coi mọi video ngắn là Shorts.
