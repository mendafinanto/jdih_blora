# JDIH Kabupaten Blora Android

Aplikasi Android WebView untuk membuka https://jdih.blorakab.go.id/mobile.

## Build otomatis
1. Buka tab **Actions** pada repositori GitHub.
2. Pilih workflow **Build Android APK and AAB**.
3. Klik **Run workflow**.
4. Setelah selesai, unduh artifact **jdih-blora-android**.

APK debug dapat dipasang untuk pengujian. Untuk distribusi Play Store, AAB wajib ditandatangani dengan keystore milik penerbit. Simpan file keystore dan kata sandinya sebagai GitHub Actions secrets; jangan unggah ke repositori atau kirim kata sandi melalui chat.

- Application ID: go.id.blorakab.jdih
- Minimum Android: 7.0 (API 24)
- WebView URL: https://jdih.blorakab.go.id/mobile
