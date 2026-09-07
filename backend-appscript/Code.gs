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

var TAB_TRACE       = 'trace_log';
var TAB_ABSENSI     = 'absensi';
var TAB_GEOFENCE    = 'geofence_cache';
var TAB_USER        = 'user';
var TAB_ABSEN_EVENT = 'absen_event';   // check-in/out
var TAB_LAPORAN     = 'laporan';
var TAB_JADWAL      = 'jadwal';
var TAB_SITE_CP     = 'site_cp';        // proyek di mana user jadi CP/PIC

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
    if (action === 'trace')       return handleTrace(body);
    if (action === 'trace_batch') return handleTraceBatch(body);
    if (action === 'checkin')     return handleCheckInOut(body, 'checkin');
    if (action === 'checkout')    return handleCheckInOut(body, 'checkout');
    if (action === 'laporan')     return handleCreateLaporan(body);

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

    if (action === 'geofence')  return handleGetGeofence();
    if (action === 'home')      return handleGetHome(e.parameter.username);
    if (action === 'recap')     return handleGetRecap(e.parameter.month, e.parameter.year);
    if (action === 'laporan_list') return handleGetLaporanList(e.parameter.username);
    if (action === 'jadwal')    return handleGetJadwal(e.parameter.username);
    if (action === 'ping')      return jsonOut({ status: 'ok', message: 'pong', time: nowIso() });

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
      projectId: String(data[i][0]),   // paksa jadi teks - Sheets sering simpan sebagai angka
      projectName: data[i][1],
      polygon: JSON.parse(data[i][2] || '[]')   // array {lat,lng}
    });
  }
  return jsonOut({ status: 'ok', data: out });
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

// ==================== CHECK-IN / CHECK-OUT ====================
function handleCheckInOut(body, tipe) {
  var sheet = ss().getSheetByName(TAB_ABSEN_EVENT);
  sheet.appendRow([
    nowIso(),
    Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd'),
    body.username || '',
    tipe,                          // 'checkin' atau 'checkout'
    body.latitude || '',
    body.longitude || '',
    body.projectId || '',
    body.projectName || '',
    body.address || ''
  ]);
  return jsonOut({ status: 'ok', message: tipe + ' tercatat' });
}

// ==================== BUAT LAPORAN ====================
function handleCreateLaporan(body) {
  var sheet = ss().getSheetByName(TAB_LAPORAN);
  var id = 'LAP-' + new Date().getTime();
  var fileUrl = '';

  // Kalau ada foto (base64), simpan ke Drive
  if (body.photoBase64) {
    try {
      var folder = getOrCreateFolder('SML_Laporan_Foto');
      var blob = Utilities.newBlob(
        Utilities.base64Decode(body.photoBase64),
        body.photoMime || 'image/jpeg',
        id + '.jpg'
      );
      var file = folder.createFile(blob);
      file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
      fileUrl = file.getUrl();
    } catch (err) {
      fileUrl = 'ERROR: ' + err.message;
    }
  }

  sheet.appendRow([
    id,
    nowIso(),
    body.username || '',
    body.projectId || '',
    body.projectName || '',
    body.tanggal || Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd'),
    body.deskripsi || '',
    fileUrl,
    'Terkirim'    // status awal
  ]);
  return jsonOut({ status: 'ok', message: 'Laporan tersimpan', id: id, fileUrl: fileUrl });
}

// ==================== DATA HOME ====================
// Menyediakan data untuk layar Home: lokasi terakhir, aktivitas hari ini
// (site yang dikunjungi HARI INI), dan daftar site di mana user jadi CP/PIC.
function handleGetHome(username) {
  if (!username) return jsonOut({ status: 'error', message: 'username kosong' });
  var today = Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');

  // Lokasi terakhir dari trace_log
  var traceSheet = ss().getSheetByName(TAB_TRACE);
  var traceData = traceSheet.getDataRange().getValues();
  var lastLat = null, lastLng = null, lastTime = null, lastArea = null;
  for (var i = traceData.length - 1; i >= 1; i--) {
    if (traceData[i][1] === username) {
      lastLat = traceData[i][3];
      lastLng = traceData[i][4];
      lastTime = traceData[i][0];
      lastArea = traceData[i][9];
      break;
    }
  }

  // AKTIVITAS HARI INI = site yang dikunjungi user HARI INI (dari trace_log
  // yang punya projectName), diambil waktu pertama & terakhir tiap site.
  var visitMap = {}; // projectName -> {first, last}
  for (var k = 1; k < traceData.length; k++) {
    if (traceData[k][1] !== username || !traceData[k][9]) continue;
    var t = traceData[k][0];
    var tgl = Utilities.formatDate(new Date(t), TZ, 'yyyy-MM-dd');
    if (tgl !== today) continue;
    var pname = traceData[k][9];
    if (!visitMap[pname]) visitMap[pname] = { first: t, last: t };
    else {
      if (new Date(t) < new Date(visitMap[pname].first)) visitMap[pname].first = t;
      if (new Date(t) > new Date(visitMap[pname].last)) visitMap[pname].last = t;
    }
  }
  var aktivitas = Object.keys(visitMap).map(function (name) {
    return {
      projectName: name,
      jamMasuk: Utilities.formatDate(new Date(visitMap[name].first), TZ, 'HH:mm'),
      jamAkhir: Utilities.formatDate(new Date(visitMap[name].last), TZ, 'HH:mm')
    };
  }).sort(function (a, b) { return a.jamMasuk.localeCompare(b.jamMasuk); });

  // LOKASI SITE (CP) = proyek di mana user jadi PIC/PIC2 (dari server SML asli).
  // Di-cache di tab site_cp supaya tidak fetch tiap kali. Kalau cache kosong,
  // kembalikan array kosong (nanti refreshSiteCP mengisinya via trigger).
  var sites = [];
  var cpSheet = ss().getSheetByName(TAB_SITE_CP);
  if (cpSheet) {
    var cpData = cpSheet.getDataRange().getValues();
    for (var c = 1; c < cpData.length; c++) {
      if (cpData[c][0] === username && cpData[c][1]) {
        sites.push({
          projectName: cpData[c][1],
          address: cpData[c][2] || '',
          role: cpData[c][3] || 'CP'
        });
      }
    }
  }

  return jsonOut({
    status: 'ok',
    data: {
      lastLat: lastLat, lastLng: lastLng,
      lastTime: lastTime ? Utilities.formatDate(new Date(lastTime), TZ, 'HH:mm') : null,
      lastArea: lastArea || null,
      aktivitas: aktivitas,
      sites: sites
    }
  });
}

// ==================== REFRESH SITE-CP DARI SERVER SML ASLI ====================
// Jalankan via trigger tiap 6 jam. Ambil semua proyek, catat siapa PIC/PIC2-nya,
// simpan ke tab site_cp: [Username, ProjectName, Address, Role].
// Username SML dicocokkan dari nama PIC (perlu tab pemetaan nama->username kalau
// nama tidak sama persis dengan username; untuk awal, pakai nama PIC apa adanya).
function refreshSiteCP() {
  var url = 'https://ideakaryanusa.softindopp.com/api/projects?date=' +
            Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd');
  var res = UrlFetchApp.fetch(url, {
    method: 'get',
    headers: { 'X-Auth-Token': TRACKING_TOKEN },
    muteHttpExceptions: true
  });
  if (res.getResponseCode() !== 200) {
    Logger.log('Gagal ambil proyek untuk site-CP: ' + res.getResponseCode());
    return;
  }
  var projects = JSON.parse(res.getContentText());
  if (projects.data) projects = projects.data;

  // Ambil pemetaan nama->username dari tab user (kolom NamaLengkap & Username)
  var userSheet = ss().getSheetByName(TAB_USER);
  var userData = userSheet.getDataRange().getValues();
  var namaToUsername = {};
  for (var u = 1; u < userData.length; u++) {
    if (userData[u][2] && userData[u][0]) {
      namaToUsername[String(userData[u][2]).trim().toLowerCase()] = userData[u][0];
    }
  }

  var rows = [];
  projects.forEach(function (p) {
    var address = p.Address || '';
    var pics = [];
    // PIC utama
    if (p.PIC && p.PIC.Name) pics.push(p.PIC.Name);
    // PIC2 (bisa array)
    if (p.PIC2 && p.PIC2.length) {
      p.PIC2.forEach(function (x) { if (x.Name) pics.push(x.Name); });
    }
    pics.forEach(function (namaPic) {
      var key = String(namaPic).trim().toLowerCase();
      var uname = namaToUsername[key] || namaPic; // fallback: pakai nama kalau tak ketemu
      rows.push([uname, p.Name, address, 'CP']);
    });
  });

  var cpSheet = ss().getSheetByName(TAB_SITE_CP);
  if (!cpSheet) cpSheet = ss().insertSheet(TAB_SITE_CP);
  cpSheet.clearContents();
  cpSheet.appendRow(['Username', 'ProjectName', 'Address', 'Role']);
  if (rows.length > 0) {
    cpSheet.getRange(2, 1, rows.length, 4).setValues(rows);
  }
  Logger.log('Site-CP diperbarui: ' + rows.length + ' baris');
}

// ==================== REKAP ABSENSI (per user & per proyek) ====================
function handleGetRecap(month, year) {
  var absSheet = ss().getSheetByName(TAB_ABSENSI);
  var data = absSheet.getDataRange().getValues();
  var m = month ? parseInt(month) : (new Date().getMonth() + 1);
  var y = year ? parseInt(year) : new Date().getFullYear();

  // perUser[username][hari] = totalJam ; perProyek[projectName][hari] = totalJam
  var perUser = {}, perProyek = {};
  for (var i = 1; i < data.length; i++) {
    var tgl = data[i][0]; // yyyy-MM-dd
    if (!tgl) continue;
    var parts = String(tgl).split('-');
    if (parseInt(parts[0]) !== y || parseInt(parts[1]) !== m) continue;
    var hari = parseInt(parts[2]);
    var user = data[i][1];
    var pname = data[i][3];
    var jam = parseFloat(data[i][5]) || 0;

    if (!perUser[user]) perUser[user] = {};
    perUser[user][hari] = (perUser[user][hari] || 0) + jam;

    if (pname) {
      if (!perProyek[pname]) perProyek[pname] = {};
      perProyek[pname][hari] = (perProyek[pname][hari] || 0) + jam;
    }
  }

  return jsonOut({ status: 'ok', data: { perUser: perUser, perProyek: perProyek, month: m, year: y } });
}

// ==================== DAFTAR LAPORAN ====================
function handleGetLaporanList(username) {
  var sheet = ss().getSheetByName(TAB_LAPORAN);
  var data = sheet.getDataRange().getValues();
  var out = [];
  for (var i = data.length - 1; i >= 1; i--) {
    if (username && data[i][2] !== username) continue;
    out.push({
      id: data[i][0],
      projectName: data[i][4],
      tanggal: data[i][5],
      deskripsi: data[i][6],
      fileUrl: data[i][7],
      status: data[i][8]
    });
  }
  return jsonOut({ status: 'ok', data: out });
}

// ==================== JADWAL ====================
function handleGetJadwal(username) {
  var sheet = ss().getSheetByName(TAB_JADWAL);
  var data = sheet.getDataRange().getValues();
  var out = [];
  for (var i = 1; i < data.length; i++) {
    if (!data[i][0]) continue;
    if (username && data[i][1] && data[i][1] !== username) continue;
    out.push({
      tanggal: data[i][0],
      username: data[i][1],
      judul: data[i][2],
      projectName: data[i][3],
      keterangan: data[i][4]
    });
  }
  return jsonOut({ status: 'ok', data: out });
}

function getOrCreateFolder(name) {
  var folders = DriveApp.getFoldersByName(name);
  if (folders.hasNext()) return folders.next();
  return DriveApp.createFolder(name);
}

// ==================== SETUP AWAL (jalankan sekali) ====================
function setupSheets() {
  var s = ss();
  ensureTab(s, TAB_TRACE, ['ServerTime', 'Username', 'DeviceId', 'Latitude', 'Longitude',
                            'Accuracy', 'Speed', 'HpTimestamp', 'ProjectId', 'ProjectName']);
  ensureTab(s, TAB_ABSENSI, ['Tanggal', 'Username', 'ProjectId', 'ProjectName', 'DetikTotal', 'JamTotal']);
  ensureTab(s, TAB_GEOFENCE, ['ProjectID', 'ProjectName', 'PolygonJSON']);
  ensureTab(s, TAB_USER, ['Username', 'DeviceId', 'NamaLengkap', 'TerakhirAktif']);
  ensureTab(s, TAB_ABSEN_EVENT, ['ServerTime', 'Tanggal', 'Username', 'Tipe',
                                  'Latitude', 'Longitude', 'ProjectId', 'ProjectName', 'Address']);
  ensureTab(s, TAB_LAPORAN, ['ID', 'ServerTime', 'Username', 'ProjectId', 'ProjectName',
                              'Tanggal', 'Deskripsi', 'FileUrl', 'Status']);
  ensureTab(s, TAB_JADWAL, ['Tanggal', 'Username', 'Judul', 'ProjectName', 'Keterangan']);
  ensureTab(s, TAB_SITE_CP, ['Username', 'ProjectName', 'Address', 'Role']);
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
