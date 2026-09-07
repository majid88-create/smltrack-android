/**
 * ============================================================
 *  SML TRACK - BACKEND SEMENTARA (Google Apps Script + Sheets)
 * ============================================================
 *
 * Ini backend fase-awal sebelum pindah ke VPS. Di-deploy sebagai
 * Web App, lalu URL-nya dipasang di app Android (BuildConfig.API_BASE_URL).
 * Saat pindah ke VPS nanti, cukup ganti URL di app - format request tetap sama.
 *
 * === CARA DEPLOY ===
 * 1. Buat Spreadsheet baru, salin ID-nya (dari URL, bagian setelah /d/).
 * 2. Isi SPREADSHEET_ID di bawah.
 * 3. Isi TRACKING_TOKEN dengan token API server SML asli (yang sudah
 *    Mas Majid punya, untuk ambil daftar geofence /api/project).
 * 4. Isi SECRET_APP dengan sembarang teks rahasia (dipakai app Android
 *    sebagai "kunci" supaya tidak sembarang orang bisa kirim data).
 * 5. Jalankan fungsi setupSheets() sekali (klik Run) - bikin semua tab.
 * 6. Deploy > New deployment > Web app:
 *    - Execute as: Me
 *    - Who has access: Anyone
 *    Salin URL Web App-nya, pasang di app Android.
 * 7. (Opsional) Buat trigger waktu untuk refreshGeofenceCache() tiap 6 jam,
 *    dan untuk hitungDurasiHarian() tiap 15 menit.
 */

// ==================== KONFIGURASI ====================
var SPREADSHEET_ID = 'ISI_ID_SPREADSHEET_DI_SINI';
var TRACKING_TOKEN = 'ISI_TOKEN_SERVER_SML_ASLI';   // untuk ambil geofence
var SECRET_APP      = 'ISI_KUNCI_RAHASIA_BEBAS';    // kunci app -> backend

var TAB_TRACE    = 'trace_log';
var TAB_ABSENSI  = 'absensi';
var TAB_GEOFENCE = 'geofence_cache';
var TAB_USER     = 'user';

var TZ = 'Asia/Jakarta';

// ==================== ROUTER WEB APP ====================
// Semua request dari app masuk lewat sini. Dibedakan pakai parameter "action".

function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents);

    // Cek kunci rahasia - tolak kalau tidak cocok
    if (body.secret !== SECRET_APP) {
      return jsonOut({ status: 'error', message: 'Unauthorized' });
    }

    var action = body.action;
    if (action === 'trace')      return handleTrace(body);
    if (action === 'trace_batch') return handleTraceBatch(body);

    return jsonOut({ status: 'error', message: 'Unknown action: ' + action });
  } catch (err) {
    return jsonOut({ status: 'error', message: 'Server error: ' + err.message });
  }
}

function doGet(e) {
  try {
    var action = e.parameter.action;
    var secret = e.parameter.secret;
    if (secret !== SECRET_APP) {
      return jsonOut({ status: 'error', message: 'Unauthorized' });
    }

    if (action === 'geofence') return handleGetGeofence();
    if (action === 'last_visits') return handleGetLastVisits(e.parameter.username || '');
    if (action === 'ping')     return jsonOut({ status: 'ok', message: 'pong', time: nowIso() });

    return jsonOut({ status: 'error', message: 'Unknown action: ' + action });
  } catch (err) {
    return jsonOut({ status: 'error', message: 'Server error: ' + err.message });
  }
}

// ==================== TERIMA GPS (1 titik) ====================
function handleTrace(body) {
  var sheet = ss().getSheetByName(TAB_TRACE);
  sheet.appendRow([
    nowIso(),                    // waktu server terima
    body.username || '',
    body.deviceId || '',
    body.latitude,
    body.longitude,
    body.accuracy || '',
    body.speed || '',
    body.timestamp || '',        // waktu dari HP
    body.projectId || '',        // area terdaftar tempat titik ini (kalau ada), hasil deteksi HP
    body.projectName || ''
  ]);
  return jsonOut({ status: 'ok', message: 'Titik tersimpan' });
}

// ==================== TERIMA GPS (banyak titik sekaligus) ====================
// Dipakai saat HP habis offline lalu kirim antrian menumpuk - lebih hemat
// daripada 1 request per titik.
function handleTraceBatch(body) {
  var sheet = ss().getSheetByName(TAB_TRACE);
  var points = body.points || [];
  if (points.length === 0) return jsonOut({ status: 'ok', message: 'Kosong' });

  var rows = points.map(function (p) {
    return [
      nowIso(), body.username || '', body.deviceId || '',
      p.latitude, p.longitude, p.accuracy || '', p.speed || '',
      p.timestamp || '', p.projectId || '', p.projectName || ''
    ];
  });
  sheet.getRange(sheet.getLastRow() + 1, 1, rows.length, rows[0].length).setValues(rows);
  return jsonOut({ status: 'ok', message: points.length + ' titik tersimpan' });
}

// ==================== SEDIAKAN GEOFENCE UNTUK APP ====================
// App minta daftar area terdaftar (sudah di-cache dari server SML asli),
// supaya HP bisa deteksi sendiri "sedang di dalam area mana".
function handleGetGeofence() {
  var sheet = ss().getSheetByName(TAB_GEOFENCE);
  var data = sheet.getDataRange().getValues();
  var out = [];
  for (var i = 1; i < data.length; i++) {
    if (!data[i][0]) continue;
    out.push({
      projectId: data[i][0],
      projectName: data[i][1],
      polygon: JSON.parse(data[i][2] || '[]')   // array {lat,lng}
    });
  }
  return jsonOut({ status: 'ok', data: out });
}

// ==================== KUNJUNGAN TERAKHIR PER SITE ====================
// Membaca trace_log dan mengambil titik terbaru untuk setiap project milik user.
// App memakai ini untuk menampilkan "Last visit" pada daftar Lokasi Site.
function handleGetLastVisits(username) {
  if (!username) return jsonOut({ status: 'ok', data: [] });

  var sheet = ss().getSheetByName(TAB_TRACE);
  var data = sheet.getDataRange().getValues();
  var latest = {};

  for (var i = 1; i < data.length; i++) {
    var rowUser = String(data[i][1] || '');
    var projectId = String(data[i][8] || '');
    var projectName = String(data[i][9] || '');
    var timestamp = String(data[i][7] || '');
    if (rowUser !== String(username) || !projectId || !timestamp) continue;

    if (!latest[projectId] || new Date(timestamp).getTime() > new Date(latest[projectId].timestamp).getTime()) {
      latest[projectId] = {
        projectId: projectId,
        projectName: projectName,
        timestamp: timestamp
      };
    }
  }

  return jsonOut({ status: 'ok', data: Object.keys(latest).map(function (id) { return latest[id]; }) });
}

// ==================== AMBIL GEOFENCE DARI SERVER SML ASLI ====================
// Jalankan manual atau via trigger tiap 6 jam. Menyalin polygon area proyek
// dari server SML asli ke tab geofence_cache.
function refreshGeofenceCache() {
  var url = 'https://ideakaryanusa.softindopp.com/api/projects?date=' +
            Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  var res = UrlFetchApp.fetch(url, {
    method: 'get',
    headers: { 'X-Auth-Token': TRACKING_TOKEN },
    muteHttpExceptions: true
  });
  if (res.getResponseCode() !== 200) {
    Logger.log('Gagal ambil geofence: ' + res.getResponseCode() + ' ' + res.getContentText());
    return;
  }

  var projects = JSON.parse(res.getContentText());
  // Bentuk response bisa berupa array langsung atau {data:[...]} - tangani keduanya
  if (projects.data) projects = projects.data;

  var sheet = ss().getSheetByName(TAB_GEOFENCE);
  sheet.clearContents();
  sheet.appendRow(['ProjectID', 'ProjectName', 'PolygonJSON']);

  var rows = [];
  projects.forEach(function (p) {
    if (!p.Geofence || p.Geofence.length === 0) return;
    // Tiap proyek bisa punya beberapa poligon - gabung jadi satu daftar verteks.
    // Untuk kesederhanaan fase awal, ambil poligon pertama.
    var poly = p.Geofence[0].Vertices || p.Geofence[0];
    var points = poly.map(function (v) {
      return { lat: v.Latitude, lng: v.Longitude };
    });
    rows.push([p.ID, p.Name, JSON.stringify(points)]);
  });

  if (rows.length > 0) {
    sheet.getRange(2, 1, rows.length, 3).setValues(rows);
  }
  Logger.log('Geofence cache diperbarui: ' + rows.length + ' area');
}

// ==================== HITUNG DURASI PER AREA (ABSENSI) ====================
// Jalankan via trigger tiap 15 menit. Membaca semua titik trace_log HARI INI,
// mengelompokkan per user, lalu menjumlahkan selisih waktu antar titik yang
// berada di dalam area terdaftar yang sama.
function hitungDurasiHarian() {
  var traceSheet = ss().getSheetByName(TAB_TRACE);
  var data = traceSheet.getDataRange().getValues();
  var today = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');

  // Kumpulkan titik per user, hanya hari ini, urut waktu
  var perUser = {};  // username -> [ {waktu(ms), projectId, projectName} ]
  for (var i = 1; i < data.length; i++) {
    var serverTime = data[i][0];
    if (!serverTime) continue;
    var tanggal = Utilities.formatDate(new Date(serverTime), TZ, 'yyyy-MM-dd');
    if (tanggal !== today) continue;

    var user = data[i][1];
    var projectId = data[i][8];
    var projectName = data[i][9];
    if (!perUser[user]) perUser[user] = [];
    perUser[user].push({
      ms: new Date(serverTime).getTime(),
      projectId: projectId,
      projectName: projectName
    });
  }

  // Untuk tiap user, jumlahkan durasi di tiap area
  // durasi[user][projectId] = { name, detik }
  var hasil = {};
  var MAX_GAP_MS = 10 * 60 * 1000; // kalau jeda antar titik > 10 menit, jangan dihitung
                                    // (kemungkinan HP mati/hilang sinyal, bukan benar2 diam di sana)

  Object.keys(perUser).forEach(function (user) {
    var pts = perUser[user].sort(function (a, b) { return a.ms - b.ms; });
    hasil[user] = {};
    for (var j = 1; j < pts.length; j++) {
      var prev = pts[j - 1];
      var cur = pts[j];
      // Hitung durasi hanya kalau titik SEBELUMnya ada di dalam suatu area,
      // dan jeda waktunya wajar
      if (!prev.projectId) continue;
      var gap = cur.ms - prev.ms;
      if (gap <= 0 || gap > MAX_GAP_MS) continue;

      if (!hasil[user][prev.projectId]) {
        hasil[user][prev.projectId] = { name: prev.projectName, detik: 0 };
      }
      hasil[user][prev.projectId].detik += gap / 1000;
    }
  });

  // Tulis ke tab absensi (timpa data hari ini)
  var absSheet = ss().getSheetByName(TAB_ABSENSI);
  var absData = absSheet.getDataRange().getValues();
  // Hapus baris hari ini yang lama
  for (var r = absData.length - 1; r >= 1; r--) {
    if (absData[r][0] === today) absSheet.deleteRow(r + 1);
  }
  // Tulis yang baru
  var newRows = [];
  Object.keys(hasil).forEach(function (user) {
    Object.keys(hasil[user]).forEach(function (pid) {
      var item = hasil[user][pid];
      newRows.push([
        today, user, pid, item.name,
        Math.round(item.detik),
        (item.detik / 3600).toFixed(2)   // durasi dalam jam
      ]);
    });
  });
  if (newRows.length > 0) {
    absSheet.getRange(absSheet.getLastRow() + 1, 1, newRows.length, 6).setValues(newRows);
  }
  Logger.log('Absensi hari ini dihitung: ' + newRows.length + ' baris');
}

// ==================== SETUP AWAL (jalankan sekali) ====================
function setupSheets() {
  var s = ss();
  ensureTab(s, TAB_TRACE, ['ServerTime', 'Username', 'DeviceId', 'Latitude', 'Longitude',
                            'Accuracy', 'Speed', 'HpTimestamp', 'ProjectId', 'ProjectName']);
  ensureTab(s, TAB_ABSENSI, ['Tanggal', 'Username', 'ProjectId', 'ProjectName', 'DetikTotal', 'JamTotal']);
  ensureTab(s, TAB_GEOFENCE, ['ProjectID', 'ProjectName', 'PolygonJSON']);
  ensureTab(s, TAB_USER, ['Username', 'DeviceId', 'NamaLengkap', 'TerakhirAktif']);
  Logger.log('Semua tab siap.');
}

// ==================== HELPER ====================
function ss() { return SpreadsheetApp.openById(SPREADSHEET_ID); }
function nowIso() { return Utilities.formatDate(new Date(), TZ, "yyyy-MM-dd'T'HH:mm:ss"); }
function jsonOut(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
function ensureTab(spreadsheet, name, headers) {
  var sh = spreadsheet.getSheetByName(name);
  if (!sh) sh = spreadsheet.insertSheet(name);
  if (sh.getLastRow() === 0) sh.appendRow(headers);
}
