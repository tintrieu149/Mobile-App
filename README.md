# Ghi Nhanh (app Android)

App độc lập để chụp ảnh hiện trường, vẽ chú thích lên ảnh và ghi chú nhanh. Không gọi Backend, không có bí mật nào trong app.

## Tính năng

- **Chụp / Chọn ảnh** rồi vẽ ngay: bút tự do, mũi tên, khung chữ nhật, hình tròn, đánh số ①②③, chèn chữ.
- 6 màu, 3 độ dày nét, hoàn tác / làm lại.
- Ghi chú chữ kèm ảnh, hoặc ghi chú riêng không có ảnh.
- Danh sách có tìm kiếm. Chạm để sửa lại (vẽ thêm, đổi ghi chú); nhấn giữ để xóa.
- **Lưu**: bản đã vẽ lưu trong app và vào thư viện ảnh máy (`Pictures/GhiNhanh`). **Chia sẻ**: gửi ảnh kèm ghi chú qua Zalo, Gmail…

Dữ liệu chỉ nằm trên điện thoại (`files/entries.json` và `files/images/`), không đẩy lên máy chủ. Android 10 trở lên.

## Build

- Android Studio: mở thư mục gốc repo, chạy `app`.
- Dòng lệnh: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`). APK ở `app/build/outputs/apk/debug/app-debug.apk`.
- GitHub Actions `Build Android APK` build sẵn APK debug, tải ở mục Artifacts của lần chạy.

Cài APK: chép file vào điện thoại, cho phép "cài từ nguồn không xác định".
