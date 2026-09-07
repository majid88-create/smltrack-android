# Backend SML Track (Apps Script) — Panduan Setup

Backend sementara sebelum pindah ke VPS. Pakai Google Apps Script + Sheets.

## Langkah setup

1. Buat **Spreadsheet baru** di Google Drive. Dari URL-nya, salin ID
   (bagian panjang setelah `/d/` dan sebelum `/edit`).

2. Buka **Extensions → Apps Script** dari spreadsheet itu. Hapus kode contoh,
   paste seluruh isi `Code.gs`.

3. Isi 3 konfigurasi di bagian atas `Code.gs`:
   - `SPREADSHEET_ID` — ID spreadsheet tadi
   - `TRACKING_TOKEN` — token API server SML asli (yang sudah Mas Majid punya,
     dipakai untuk ambil daftar geofence dari `/api/projects`)
   - `SECRET_APP` — kunci rahasia bebas (misal `sml-rahasia-2026`). Ini harus
     SAMA dengan `APP_SECRET` yang dipasang di app Android.

4. Jalankan fungsi **`setupSheets`** sekali (pilih di dropdown, klik Run).
   Akan diminta izin akses — izinkan. Ini membuat semua tab yang diperlukan.

5. Jalankan **`refreshGeofenceCache`** sekali untuk mengambil daftar area
   terdaftar dari server SML asli. Cek tab `geofence_cache` — harusnya terisi.

6. **Deploy sebagai Web App**:
   - Klik **Deploy → New deployment**
   - Pilih tipe **Web app**
   - Execute as: **Me**
   - Who has access: **Anyone**
   - Klik Deploy, salin **URL Web App**-nya (bentuknya
     `https://script.google.com/macros/s/AKfy........./exec`)

7. Di app Android (`app/build.gradle.kts`), isi:
   - `BACKEND_BASE_URL` — URL Web App tadi TANPA `exec` di ujung, dan
     DIAKHIRI `/`. Contoh: `https://script.google.com/macros/s/AKfy.../`
   - `APP_SECRET` — sama persis dengan `SECRET_APP` di Apps Script

8. **Buat trigger otomatis** (Apps Script → ikon jam "Triggers"):
   - `refreshGeofenceCache` → time-driven → tiap 6 jam
   - `hitungDurasiHarian` → time-driven → tiap 15 menit

## Cara cek berhasil

- Buka di browser: `URL_WEBAPP?action=ping&secret=KUNCI_RAHASIA`
  Harusnya balas `{"status":"ok","message":"pong",...}`
- Setelah app kirim GPS, cek tab `trace_log` — baris baru muncul
- Setelah trigger `hitungDurasiHarian` jalan, cek tab `absensi` — durasi per area

## Struktur tab

- `trace_log` — semua titik GPS mentah (waktu, user, koordinat, area terdeteksi)
- `absensi` — hasil hitungan durasi per user per area per hari
- `geofence_cache` — daftar area terdaftar + polygon (di-cache dari server SML)
- `user` — daftar user & device (untuk keperluan nanti)

## Catatan batas kuota

Apps Script Web App gratis dibatasi ~20.000 request/hari. Karena app mengirim
titik tiap ~1 menit, satu HP = ~1.440 request/hari. Jadi realistis untuk
belasan HP. Kalau nanti karyawan makin banyak DAN mau interval lebih rapat,
itu tandanya sudah waktunya pindah ke VPS (tinggal ganti `BACKEND_BASE_URL`
di app). App sudah pakai pengiriman batch saat habis offline untuk menghemat.

## Saat pindah ke VPS nanti

Cukup 2 hal:
1. Bikin endpoint di VPS yang menerima format JSON yang sama (lihat
   `BackendModels.kt` di app untuk bentuk persisnya)
2. Ganti `BACKEND_BASE_URL` di app ke alamat VPS, build ulang

Tidak perlu ubah logika app sama sekali.
