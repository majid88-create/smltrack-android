# SML Track (clone)

App Android (Kotlin, native) yang mengirim titik GPS ke backend yang sama dengan
app "SML" resmi (`ideakaryanusa.softindopp.com`), dibuat dari nol — bukan hasil
salin kode/aset dari APK aslinya.

## Kenapa native Kotlin, bukan Flutter (seperti aslinya)?

App aslinya Flutter, tapi untuk kebutuhan **pelacakan lokasi latar belakang yang
andal berjam-jam di lapangan**, foreground service Android native lebih stabil
dan lebih mudah didiagnosis kalau ada masalah baterai/OS killing process.
Kalau nanti ingin versi Flutter (misalnya biar bisa iOS juga), source ini bisa
jadi acuan alur logikanya.

## Cara build - Opsi A: GitHub Actions (paling gampang, TANPA install apa-apa di laptop)

Ini cara paling gampang kalau tidak mau ribet install Android Studio. GitHub yang
akan "memasak" source code ini jadi file APK otomatis, tinggal download.

1. Buka github.com, login pakai akun yang sama dengan yang dipakai untuk
   `majid88-create` (repo monitoring-ikn dulu).
2. Buat repository baru (New repository), kasih nama misal `smltrack-android`,
   pilih **Private** kalau tidak mau kode ini kelihatan orang lain, biarkan
   kosong (jangan centang "Add README").
3. Di laptop, buka folder hasil ekstrak zip ini lewat Command Prompt/Terminal,
   lalu jalankan:
   ```
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/USERNAME/smltrack-android.git
   git push -u origin main
   ```
   (ganti `USERNAME` dan nama repo sesuai punya Mas Majid)
4. Buka tab **Actions** di halaman repo GitHub tadi. Akan otomatis jalan proses
   build (icon kuning berputar → hijau kalau sukses, merah kalau gagal).
   Prosesnya sekitar 3-5 menit.
5. Kalau sudah hijau, klik hasil run-nya, scroll ke bawah ke bagian
   **Artifacts**, download `smltrack-debug-apk` (berupa file .zip berisi
   `app-debug.apk` di dalamnya).
6. Ekstrak, pindahkan `app-debug.apk` ke HP (lewat kabel USB, WhatsApp ke diri
   sendiri, Google Drive, apa saja), lalu tap file itu di HP untuk install.
   Mungkin perlu izinkan dulu "Install dari sumber tidak dikenal" di HP.

Kalau tab Actions menunjukkan **merah** (gagal), klik untuk lihat log errornya,
screenshot bagian yang merah, kirim ke saya - biasanya cuma soal versi library
yang perlu disesuaikan.

## Cara build - Opsi B: Android Studio di laptop

1. Install **Android Studio** (Ladybug/Koala ke atas) di PC/laptop.
2. Buka folder ini sebagai project ("Open" di Android Studio, bukan "Import").
3. Biarkan Gradle sync (butuh internet, akan download dependency dari Google/Maven).
4. Sambungkan HP Android (mode USB debugging) atau pakai emulator, lalu Run.

Saya tidak bisa mengompilasi APK langsung dari sandbox ini (tidak ada Android
SDK/Gradle di lingkungan ini), jadi yang diberikan adalah **source code lengkap**
yang tinggal dibuka & di-build di Android Studio.

## Yang sudah diimplementasikan (v0.2 - update mengikuti SML v1.0.4)

- Login ke `POST /api/auth` → simpan token terenkripsi (EncryptedSharedPreferences)
- Foreground service yang ambil titik GPS tiap ~1 menit (bisa diubah di
  `LocationTrackingService.INTERVAL_MS`)
- Setiap titik GPS **disimpan dulu ke database lokal (Room)** sebelum dikirim —
  ini niru pola offline-first yang ditemukan di app aslinya ("Saving current
  trace log locally" kalau gagal kirim)
- Kalau kirim gagal (tidak ada internet dsb), data tetap tersimpan dan akan
  dicoba ulang otomatis tiap 15 menit lewat `TraceLogSyncWorker` (WorkManager)
- Layar status: toggle aktif/nonaktif tracking, jumlah antrian belum terkirim
- Restart otomatis tracking setelah HP reboot (`BootReceiver`), kalau masih login
- **Laporan** — daftar (`GET /api/laporan`), buat baru dengan foto + lokasi GPS
  (`POST /api/laporan`, multipart, retry 3x kalau timeout - niru perilaku app
  asli), dan detail (`GET /api/laporan/{id}`)
- **Jadwal** — daftar jadwal dari `GET /api/schedules`
- **Rekap Absensi** — daftar dari `GET /api/absence`

Navigasi: dari layar utama (setelah login) ada 3 tombol baru — Laporan, Jadwal,
Rekap Absensi — di bawah tombol toggle tracking.

## Yang BELUM ada (fitur app asli yang belum dibuatkan)

- Halaman peta (app asli pakai OpenStreetMap tile + OSRM untuk rute)
- Daftar proyek & deteksi geofence (masuk/keluar area proyek otomatis)
- Halaman profil
- Edit/hapus laporan (baru ada create + list + detail, belum update/delete)
- Antrian offline untuk Laporan kalau gagal upload (Laporan langsung retry 3x
  lalu menyerah dengan pesan error - beda dengan trace-log yang punya antrian
  Room permanen). Bisa ditambahkan kalau ternyata koneksi di lapangan sering putus.

Beri tahu saya kalau salah satu dari ini mau dikerjakan berikutnya.

## Arsitektur keandalan GPS (v0.3)

Ini bagian yang paling penting untuk pemakaian lapangan sungguhan. Empat lapisan
yang saling menopang:

1. **Antrian offline (Room)** — titik GPS SELALU disimpan lokal dulu sebelum
   dikirim. Kalau tidak ada sinyal data, data tidak hilang, cuma menunggu.
   `TraceLogSyncWorker` sekarang menguras SELURUH antrian dalam satu jalan
   (bukan cuma 50 item), penting kalau HP offline berjam-jam.
2. **Kirim instan begitu sinyal kembali** — `LocationTrackingService` mendaftar
   `ConnectivityManager.NetworkCallback`. Begitu koneksi data terdeteksi lagi
   (misal keluar dari area blank spot), sinkronisasi dipicu SAAT ITU JUGA, tidak
   menunggu jadwal WorkManager 15 menit berikutnya.
3. **Watchdog independen (`WatchdogReceiver`)** — jalan lewat `AlarmManager`
   (exact alarm kalau diizinkan, inexact kalau tidak), terpisah total dari
   mekanisme restart Android biasa. Tiap ~15 menit cek "heartbeat" terakhir dari
   service; kalau sudah >20 menit tidak update padahal tracking seharusnya
   aktif, service di-restart paksa. Ini jaring pengaman untuk kasus di mana
   service benar-benar mati tanpa sempat memicu callback apa pun.
4. **`onTaskRemoved` override** — beberapa ROM (Xiaomi/Oppo/Vivo dkk) tetap
   membunuh foreground service saat app di-swipe dari recent apps. Begitu itu
   terjadi, service dijadwalkan restart lewat alarm 1 detik kemudian.

**Yang WAJIB dilakukan manual di tiap HP** (tidak bisa dipaksa lewat kode saja,
ini keterbatasan Android, bukan kekurangan app):
- Tombol "Matikan Optimasi Baterai untuk App Ini" — minta pengecualian dari
  Android Battery Optimization standar.
- Tombol "Buka Pengaturan Autostart HP Ini" — buka halaman khusus pabrikan
  (MIUI Autostart, ColorOS Startup Manager, dst). Ini yang PALING sering jadi
  penyebab tracking berhenti diam-diam di HP Xiaomi/Oppo/Vivo — pengaturan baterai
  Android biasa saja TIDAK cukup di HP-HP ini.
- Banner kuning otomatis muncul di layar utama kalau optimasi baterai belum
  dimatikan, supaya kelihatan jelas ke user/admin.

**Catatan jujur soal batas kemampuan**: tidak ada cara 100% menjamin foreground
service tidak pernah mati sama sekali di semua HP - ini keterbatasan platform
Android, bukan sesuatu yang bisa "diperbaiki total" lewat kode. Yang bisa
dilakukan (dan sudah dilakukan di sini) adalah membuat kemungkinan matinya
sekecil mungkin DAN memastikan kalaupun mati, otomatis hidup lagi secepat
mungkin (maksimal ~15-20 menit lewat watchdog, atau ~1 detik lewat
onTaskRemoved untuk kasus swipe-dari-recents).

Belum ditambahkan (bisa menyusul kalau ternyata masih kurang handal setelah
dites di lapangan):
- Fallback ke `LocationManager` GPS langsung kalau Google Play Services tidak
  tersedia di device (jarang terjadi, tapi ada di sebagian kecil device Huawei)
- Interval adaptif (perpanjang interval kalau device diam di tempat lama, biar
  hemat baterai)
- Filter/tandai titik dengan akurasi sangat buruk (>200m) secara terpisah biar
  gampang dibedakan dari titik GPS asli saat dianalisis nanti

## ⚠️ Yang WAJIB diverifikasi sebelum dipakai serius

Endpoint dan nama field di bawah ini saya temukan dari **string yang tertanam**
di file biner `libapp.so` APK asli (bukan dari dokumentasi resmi), jadi
strukturnya kemungkinan besar benar tapi belum saya uji ke server sungguhan:

- Base URL: `https://ideakaryanusa.softindopp.com/`
- Endpoint login: `POST /api/auth`
- Endpoint kirim lokasi: `POST /api/trace-log`
- Header auth: `X-Auth-Token: <token>` (dikonfirmasi sama dengan yang dipakai
  di sistem dashboard monitoring gabungan)
- Field trace log: `latitude, longitude, accuracy, altitude, heading, speed,
  timestamp, deviceId`

**Sebelum dipasang di HP karyawan lapangan sungguhan**, coba dulu manual pakai
`curl` atau Postman dengan akun asli:

```bash
curl -X POST https://ideakaryanusa.softindopp.com/api/auth \
  -H "Content-Type: application/json" \
  -d '{"username":"...","password":"...","deviceId":"test-device"}'
```

Lihat bentuk JSON response-nya (apakah `token` ada di root, atau nested di
`data.token`?), lalu cocokkan dengan `LoginResponse` di `model/ApiModels.kt`.
Kalau beda, cukup sesuaikan field-nya — tidak perlu ubah struktur lain.

Kalau responsenya beda dan Mas Majid kirim contoh JSON-nya (boleh sensor
username/password), saya sesuaikan modelnya langsung.

### Bagian Laporan / Schedule / Recap — level kepastian lebih rendah

Field-field untuk 3 fitur ini (title, description, category, status, image,
location, latitude, longitude, project_id, user_id, created_at, updated_at
untuk Laporan; start_date/end_date/start_time/end_time/location/repeat untuk
Schedule; date/status/check_in/check_out untuk Absence) ditemukan sebagai
string lepas di binary, **bukan** dari struktur JSON utuh yang saya lihat
langsung — jadi ada beberapa hal yang masih tebakan dan perlu dicek:

1. **Apakah response dibungkus `{"data": ...}`?** Saya asumsikan begitu (gaya
   umum Laravel API Resource). Kalau ternyata array/objek langsung di root,
   tinggal hapus wrapper `data` di `LaporanListResponse` / `ScheduleListResponse`
   / `AbsenceListResponse`.
2. **Nama field multipart foto laporan** — saya pakai `"image"`. Kalau server
   menolak upload (400/422), coba ganti ke `"photo"` atau `"file"`.
3. **Endpoint recap** — saya pakai `/api/absence` (tunggal, sesuai yang
   ditemukan di app mobile). Kalau 404, coba `/api/absences` (jamak, dipakai
   dashboard monitoring gabungan yang sudah pernah dibuat sebelumnya).
4. **`project_id` di form Laporan** saat ini dikirim `null` — kalau server
   mewajibkan project tertentu, perlu ditambahkan dropdown pilih proyek
   (datanya bisa diambil dari `GET /api/project` yang sudah ada di app asli).
5. **Foto di halaman detail laporan diambil tanpa header `X-Auth-Token`** —
   ini oke kalau URL foto memang publik (biasanya begitu untuk link S3/CDN),
   tapi kalau server menolak (gambar tidak muncul), berarti perlu header auth
   juga saat mengambil gambar - tinggal beri tahu saya, saya tambahkan.

Cara paling cepat memastikan semua ini: pasang app, coba tiap fitur, kalau ada
respons gagal kirim screenshot pesan error / kode HTTP-nya ke saya, saya
sesuaikan.

## Izin yang perlu di-approve manual di HP

- Lokasi "Allow all the time" (bukan cuma "while using app") — di Android 10+
  ini biasanya perlu user buka Settings sendiri, tidak bisa lewat dialog biasa.
  Kalau perlu, saya tambahkan alur yang mengarahkan user ke halaman setting itu.
- Notifikasi (Android 13+) — wajib untuk foreground service bisa jalan.
- Pilih foto untuk Laporan pakai system photo picker (`GetContent`), jadi
  **tidak perlu** izin storage/media tambahan di Android 11+.
