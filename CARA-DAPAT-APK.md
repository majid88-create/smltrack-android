# CARA DAPAT FILE APK — Baca ini saja

Android Studio di laptop bermasalah karena Java-nya versi 25 (terlalu baru).
**Lupakan Android Studio.** Pakai cara ini — GitHub yang akan membuatkan APK,
Mas Majid tinggal download. Sekitar 10 menit, sekali setup saja.

## Langkah

**1. Buat repo baru di GitHub**
- Buka github.com, login (akun yang sama dengan majid88-create)
- Klik tombol **+** kanan atas → **New repository**
- Nama: `smltrack-android`
- Pilih **Private**
- JANGAN centang "Add a README file"
- Klik **Create repository**

**2. Upload file lewat web (tanpa perintah git sama sekali)**
- Di halaman repo yang baru dibuat, klik link **"uploading an existing file"**
- Buka folder hasil ekstrak zip di laptop
- **Blok semua isi folder `smltrack`** (Ctrl+A), lalu **seret (drag) ke halaman GitHub**
  - PENTING: yang diseret adalah ISI folder smltrack (folder `app`, `gradle`,
    file `settings.gradle.kts`, dll), bukan folder `smltrack`-nya
  - Kalau folder `.github` tidak ikut terseret (Windows sering menyembunyikan
    folder berawalan titik), aktifkan dulu "Hidden items" di File Explorer:
    tab **View** → centang **Hidden items**
- Tunggu semua file selesai naik, lalu klik **Commit changes**

**3. Tunggu GitHub membuat APK**
- Klik tab **Actions** di atas
- Akan muncul proses berjalan (lingkaran kuning berputar)
- Tunggu 3–5 menit sampai jadi centang hijau ✅

**4. Download APK**
- Klik proses yang sudah hijau tadi
- Scroll ke bawah, ada bagian **Artifacts** → **SMLTrack-APK**
- Klik untuk download (berupa zip)
- Ekstrak, di dalamnya ada `app-debug.apk`

**5. Pasang di HP**
- Kirim `app-debug.apk` ke HP (WhatsApp ke diri sendiri paling gampang)
- Tap file-nya di HP → Install
- Kalau muncul "Install dari sumber tidak dikenal diblokir" → tap Setelan →
  izinkan → kembali → Install

## Kalau di tab Actions muncul MERAH (gagal)

Klik proses yang merah, klik kotak yang ada tanda silangnya, screenshot bagian
yang merah, kirim ke saya. Biasanya cuma penyesuaian kecil.

## Untuk update berikutnya

Kalau nanti saya kirim versi perbaikan, tidak perlu ulang dari nol — cukup upload
file yang berubah ke repo yang sama, GitHub otomatis bikin APK baru lagi.
