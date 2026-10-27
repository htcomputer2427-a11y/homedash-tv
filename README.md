# Home Dash – màn hình chính tiếng Việt cho tivi Android

Home Dash thay màn hình chính đầy quảng cáo của tivi Android TV / Google TV bằng một bảng thông tin gọn cho cả nhà:

- **Lịch**: ngày dương, ngày âm (năm, tháng, ngày can chi), Rằm / Mùng 1, ngày lễ. Thứ Bảy xanh, Chủ nhật và ngày nghỉ đỏ.
- **Đồng hồ, lời chào, thời tiết** nơi bạn ở (Open-Meteo).
- **Giá vàng** SJC, nhẫn 999.9, vàng 610 · **tỷ giá USD** Vietcombank · **giá xăng dầu** Petrolimex. Tự cập nhật 30 phút một lần. Bấm OK trên thẻ để tivi đọc to giá.
- **Nhạc**: top thịnh hành tuần của YouTube Music Việt Nam, phát ngay trong thẻ, bài đã nghe trong tuần được bỏ qua.
- **Ra lệnh bằng giọng nói** (giữ OK hoặc nút micro): "giá vàng", "tỷ giá", "giá xăng", "thời tiết", "mấy giờ", "hôm nay ngày mấy", "mở nhạc", "qua bài", "dừng nhạc", "mở nhạc top 1", "phát bài …", "mở Netflix"…
- Nút YouTube, Netflix, Cửa hàng Play và "Tất cả app".

Không cần root, không gỡ app nào của tivi, gỡ Home Dash ra là tivi về như cũ.

## Tivi nào dùng được?

| Tivi / hộp | Dùng được? |
|---|---|
| Tivi và box **Android TV / Google TV** từ Android 8.0: Xiaomi, TCL, Sony, Casper, Coocaa, Philips, Toshiba, Mi Box, Chromecast with Google TV, FPT Play Box… | **Có** |
| **Samsung** (Tizen), **LG** (webOS) | **Không**: không phải Android |
| **Amazon Fire TV** | Cài được như app, nhưng Fire OS không cho đổi màn hình chính |

Đã thử thật trên Xiaomi TV S Mini LED 75 2025 (Google TV 14), cài và gỡ bằng đúng script trong repo. Các máy khác chưa thử. Nếu bạn cài được (hoặc không), hãy mở một [Issue](../../issues) ghi tên model để mọi người cùng biết.

**Lưu ý với Google TV:** trên nhiều máy, launcher gốc của Google giữ nút Home và không nhường cho app khác. Khi đó hãy làm [Bước 0](#bước-0-dọn-tivi-bằng-cleanyourtv-khuyên-dùng) trước.

## Bước 0: dọn tivi bằng CleanYourTV (khuyên dùng)

Home Dash chỉ là màn hình chính. Việc **tắt quảng cáo, app rác, dịch vụ thu thập dữ liệu và launcher gốc** do
[**CleanYourTV**](https://github.com/Gathin23/cleanyourtv) của tác giả **Gathin23** đảm nhận. Đây là dự án mã nguồn mở
(giấy phép MIT), đã chạy trên hơn 240 model của gần 70 hãng và có [hướng dẫn tiếng Việt](https://github.com/Gathin23/cleanyourtv/blob/main/README.vi.md).
Chiếc tivi Xiaomi dùng để làm Home Dash cũng được dọn bằng công cụ này trước. Cảm ơn tác giả!

CleanYourTV không gỡ app nào, chỉ tắt (`pm disable-user`), và có lệnh khôi phục. Cách cài nhanh nhất là nhờ một trợ lý
lập trình chạy trên máy tính, ví dụ [Claude Code](https://claude.com/product/claude-code), Codex hoặc Gemini CLI, làm theo các bước sau:

1. **Trên tivi:** bật Gỡ lỗi USB như mục [Chuẩn bị trên tivi](#1-chuẩn-bị-trên-tivi) bên dưới.
2. **Trên máy tính:** tải mã nguồn CleanYourTV về:

   ```bash
   git clone https://github.com/Gathin23/cleanyourtv
   ```

   Mở thư mục đó trong trợ lý. Dán nội dung file [`PROMPT.md`](https://github.com/Gathin23/cleanyourtv/blob/main/PROMPT.md) vào trợ lý và ghi rõ model tivi của bạn.
3. **Trả lời trợ lý:** bấm **Cho phép** khi tivi hỏi gỡ lỗi. Cho trợ lý biết bạn dùng những app nào (YouTube, Netflix, FPT Play…) và có muốn giữ trợ lý giọng nói, Chromecast, cập nhật hệ thống hay không.
4. **Kiểm tra:** thử lại remote, nút Home, chuyển nguồn HDMI và giọng nói. Hỏi trợ lý cách khôi phục, rồi giữ lại bản ghi khôi phục mà công cụ tạo ra.

Muốn tự làm bằng tay (Python, `tools/disable.sh`, `launcher/build-and-install.sh`) thì xem mục *Tự làm bằng tay*
trong [hướng dẫn tiếng Việt của CleanYourTV](https://github.com/Gathin23/cleanyourtv/blob/main/README.vi.md).

Ba lưu ý quan trọng:

- Trên nhiều máy Google TV, mạng Wi-Fi lưu lúc cài đặt ban đầu thuộc về trình cài đặt `setupwraith`. Hãy **quên mạng Wi-Fi rồi kết nối lại** trong Cài đặt trước khi dọn, nếu không tivi có thể mất mạng.
- Nếu bạn chọn tắt các gói **cập nhật hệ thống**, tivi sẽ không nhận bản vá bảo mật nữa. Hãy cân nhắc trước khi tắt.
- Mỗi model có danh sách tắt riêng. **Đừng dùng danh sách của model khác** cho tivi của mình.

Dọn xong, CleanYourTV trở thành màn hình chính. Cài Home Dash ở bước tiếp theo sẽ đặt Home Dash lên trước. Lưới
"Tất cả app", nút nguồn vào (HDMI) và phím Apps trên remote vẫn mở CleanYourTV. Gỡ Home Dash thì nút Home trở về
CleanYourTV. Chạy lệnh khôi phục của CleanYourTV thì tivi trở về như lúc mua.

Không muốn dọn tivi? Cứ bỏ qua bước này và cài thẳng Home Dash. Trên tivi Android TV thường, Home Dash vẫn nhận được nút Home.

## Cài đặt Home Dash (khoảng 10 phút)

### 1. Chuẩn bị trên tivi

1. **Bật chế độ nhà phát triển.** Vào Cài đặt > Hệ thống > Giới thiệu (About) và bấm OK 7 lần vào dòng **Bản dựng Android TV OS** (Build) cho đến khi tivi báo bạn đã là nhà phát triển.
2. **Bật gỡ lỗi.** Vào Cài đặt > Hệ thống > Tùy chọn nhà phát triển và bật **Gỡ lỗi USB** (USB debugging). Máy nào có thêm mục **Gỡ lỗi ADB qua mạng** thì bật luôn mục đó.
3. **Lấy địa chỉ IP của tivi.** Xem ở Cài đặt > Mạng & Internet > tên Wi-Fi đang kết nối (dạng `192.168.1.50`). Máy tính và tivi phải dùng chung một mạng.

### 2. Chuẩn bị trên máy tính

1. Tải mã nguồn: bấm nút xanh **Code > Download ZIP** ở đầu trang rồi giải nén.
2. Tải **HomeDash.apk** ở mục [Releases](../../releases) và để vào thư mục vừa giải nén.
3. Tải **SDK Platform-Tools** của Google tại https://developer.android.com/tools/releases/platform-tools (chọn bản Windows, Mac hoặc Linux). Giải nén thư mục `platform-tools` vào cùng thư mục trên.

### 3. Cài

**Windows:** mở thư mục, bấm chuột phải vào chỗ trống, chọn *Open in Terminal* (hoặc *Mở PowerShell tại đây*), rồi gõ lệnh (thay bằng IP tivi của bạn):

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1 -TvIp 192.168.1.50
```

**macOS / Linux:**

```bash
./install.sh 192.168.1.50
```

Tivi sẽ hỏi *Cho phép gỡ lỗi?*: chọn **Luôn cho phép** rồi OK. Script sẽ cài app, mở thử, rồi mới giao nút Home cho Home Dash. Nếu app không mở được thì màn hình chính cũ vẫn giữ nguyên.

Một số tùy chọn khác:

- Chỉ cài như app bình thường, không đổi màn hình chính: thêm `-AppOnly` (Windows) hoặc `--app-only` (Mac/Linux).
- Kèm ảnh nền riêng (ảnh JPG 1920×1080): thêm `-Wallpaper D:\anh\nen.jpg` hoặc `--wallpaper ~/nen.jpg`.

### 4. Lần đầu mở

Home Dash sẽ hỏi **lời chào** và **nơi xem thời tiết**. Có sẵn 34 tỉnh, thành. Nơi khác (huyện, thị xã, phường…) chọn **Tìm nơi khác** rồi gõ tên. Muốn đổi sau thì bấm nút **bánh răng** ở góc trên. Màn đó cũng có lối vào Cài đặt của tivi.

Cài xong, nên **tắt Gỡ lỗi USB** cho an toàn. Khi cần cập nhật hoặc gỡ thì bật lại.

## Gỡ ra

```powershell
powershell -ExecutionPolicy Bypass -File .\uninstall.ps1 -TvIp 192.168.1.50
```

```bash
./uninstall.sh 192.168.1.50
```

Nút Home sẽ trả về màn hình chính trước đó, rồi Home Dash bị gỡ. Bạn cũng có thể gỡ ngay trên tivi: Cài đặt > Ứng dụng > Home Dash > Gỡ cài đặt.

## Giới hạn, đọc trước khi dùng

- **Giá vàng, giá xăng** lấy từ trang webgia.tv. Dự phòng là API của PNJ và bảng tỷ giá XML của Vietcombank. Đây đều **không phải API chính thức**. Khi trang nguồn đổi cấu trúc, thẻ giữ số cũ cho đến khi có bản sửa. Số liệu chỉ để tham khảo, không dùng để mua bán.
- **Nhạc** phát qua trình phát nhúng của YouTube, chưa đăng nhập Premium, nên có thể có quảng cáo. Bài mà chủ sở hữu không cho nhúng sẽ bị bỏ qua.
- **Giọng nói** dùng nhận dạng và giọng đọc tiếng Việt của Google. Tivi cần có giọng đọc tiếng Việt (Cài đặt > Hệ thống > Hỗ trợ tiếp cận > Chuyển văn bản thành giọng nói).
- Phần mềm được cung cấp **nguyên trạng, không bảo hành**. Bật gỡ lỗi và đổi màn hình chính là việc bạn tự quyết trên tivi của mình.

## Tự build từ mã nguồn

Cần JDK 17 và Android SDK 34.

```bash
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

File APK nằm ở `app/build/outputs/apk/release/app-release.apk`. Các script cài sẽ tự tìm file này.

- Muốn ký bằng khóa riêng: tạo file `keystore.properties` gồm `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. File này nằm ngoài git, đừng bao giờ commit nó.
- Mặc định (khi tivi chưa cài đặt gì) như lời chào, thời tiết, app ở các nút nằm trong `app/src/main/res/values/config.xml`.

Đóng góp, báo lỗi, thêm ngày lễ hay thêm nguồn giá đều được chào đón qua Issue hoặc Pull Request.

## Bản quyền

Mã nguồn theo giấy phép [MIT](LICENSE). Font và biểu tượng của bên thứ ba: xem [NOTICE.md](NOTICE.md). Home Dash không liên quan đến Google, YouTube, Xiaomi, SJC, PNJ, Vietcombank, Petrolimex hay webgia.tv. Các tên này chỉ dùng để mô tả nguồn dữ liệu.

---

**English:** Home Dash is a Vietnamese home screen for Android TV / Google TV (Android 8+): solar and lunar calendar, clock, weather, gold / USD / fuel prices, YouTube Music's Vietnam chart playing in a card, and Vietnamese voice commands. Install with `install.ps1` / `install.sh` over ADB; `uninstall.*` puts the previous Home back. On Google TV builds whose stock launcher keeps the Home button, install [CleanYourTV](https://github.com/Gathin23/cleanyourtv) first. MIT licensed.
