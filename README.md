# 📱 SMS Bridge (Android)

[![Manual Android APK Release](https://github.com/kavionn/sms-bridge/actions/workflows/manual-release.yml/badge.svg)](https://github.com/kavionn/sms-bridge/actions/workflows/manual-release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-5.0+-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)

Aplikasi Android cerdas untuk menangkap SMS masuk dan meneruskannya ke backend secara real-time. Dilengkapi dengan dukungan antrean offline untuk memastikan tidak ada data yang hilang saat koneksi terputus. 🚀

---

## ✨ Fitur Utama

- 🔗 **Pairing Perangkat:** Hubungkan perangkat dengan mudah via kode pairing unik.
- 📤 **Forward Real-time:** SMS masuk langsung diteruskan ke endpoint backend Anda.
- 📥 **Offline Queue:** Menggunakan Room database untuk menyimpan data saat offline + retry otomatis.
- 🛡️ **Admin Mode:** Pantau status perangkat dan log SMS secara langsung dari aplikasi.
- ⚙️ **Foreground Service:** Menjamin stabilitas sinkronisasi di latar belakang.

## 🛠️ Stack Teknologi

- **Bahasa:** Kotlin
- **UI:** Jetpack Compose (Modern & Deklaratif)
- **Database:** Room (Local Persistence)
- **Network:** Retrofit + OkHttp
- **Asynchronous:** Coroutines + StateFlow

## 📂 Struktur Proyek

- `app/src/main/java/com/example/MainActivity.kt` → Pusat UI utama.
- `app/src/main/java/com/example/ui/MainViewModel.kt` → Otak logika bisnis & state.
- `app/src/main/java/com/example/service/SmsSyncService.kt` → Penjaga sinkronisasi data.
- `app/src/main/java/com/example/receiver/SmsReceiver.kt` → Pendengar SMS masuk.
- `app/src/main/java/com/example/data/` → Manajemen data (DB, DAO, Prefs).

---

## 🚀 Build APK via GitHub Actions

Gunakan workflow **Manual Android APK Release** untuk membuat build release secara otomatis.

### 🔑 Konfigurasi GitHub Secrets

Pastikan rahasia berikut sudah dikonfigurasi di repositori Anda agar proses signing berhasil:

| Secret Name | Deskripsi |
| :--- | :--- |
| `KEYSTORE_BASE64` | String base64 dari file `.jks` Anda. |
| `STORE_PASSWORD` | Password untuk keystore. |
| `KEY_PASSWORD` | Password untuk key alias. |

> **Tips:** Gunakan `base64 -w 0 my-upload-key.jks > keystore_base64.txt` untuk mendapatkan string base64.

---

## 🛠️ Menjalankan Secara Lokal

1. Clone repositori ini.
2. Buka di **Android Studio** terbaru.
3. Pastikan menggunakan **JDK 17**.
4. Jalankan aplikasi ke perangkat favorit Anda! 📲

## 📜 Izin (Permissions)

Aplikasi ini membutuhkan akses berikut agar berfungsi maksimal:
- `RECEIVE_SMS` & `READ_SMS`
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE` & `DATA_SYNC`
- `INTERNET`

---

## 🛡️ Lisensi

Proyek ini dilisensikan di bawah [Lisensi MIT](LICENSE). Dibuat dengan ❤️ oleh [kavionn](https://github.com/kavionn).
