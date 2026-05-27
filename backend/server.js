require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const path = require('path');
const fs = require('fs');

const MONGODB_URI = process.env.MONGODB_URI;
const DB_CONFIG_FILE = path.join(__dirname, 'database.json');
console.log('MongoDB URI:', MONGODB_URI);

const authRouter = require('./routes/auth');
const pairingRouter = require('./routes/pairing');
const smsRouter = require('./routes/sms');

const app = express();
const PORT = process.env.PORT || 7860;

let cachedServerIp = 'localhost';
let dbStatus = "Disconnected";
let connectionError = null;
const PANEL_PIN = process.env.PIN;

// Function to check and perform auto-connect
function checkAutoConnect() {
  if (fs.existsSync(DB_CONFIG_FILE)) {
    try {
      const config = JSON.parse(fs.readFileSync(DB_CONFIG_FILE, 'utf8'));
      if (config.ip === cachedServerIp && config.uri) {
        console.log("IP Cocok dengan riwayat koneksi. Mencoba auto-connect...");
        dbStatus = "Connecting...";
        mongoose.connect(config.uri)
          .then(() => {
            dbStatus = "Connected";
            console.log("Auto-connect Berhasil.");
          })
          .catch(err => {
            dbStatus = "Disconnected";
            connectionError = "Auto-connect gagal: " + err.message;
            console.error("Auto-connect Gagal:", err.message);
          });
      } else {
        console.log("IP berbeda atau data tidak cocok. Menunggu koneksi manual.");
      }
    } catch (e) {
      console.error("Gagal memproses database.json:", e.message);
    }
  }
}

// Function to fetch public IP
async function updatePublicIp() {
  try {
    const response = await fetch('http://ip-api.com/json');
    const data = await response.json();
    if (data && data.query) {
      cachedServerIp = data.query;
      console.log('Public IP Updated:', cachedServerIp);
      // Cek auto-connect setelah IP berhasil didapatkan
      checkAutoConnect();
    }
  } catch (err) {
    console.error('Failed to get public IP:', err.message);
  }
}

// Update IP on startup
updatePublicIp();

// Middleware for parsing json and encoded express payloads
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Routing Middleware Definitions
app.use('/api/auth', authRouter);
app.use('/api/pairing', pairingRouter);
app.use('/api', smsRouter); // Mount GET /api/devices, DELETE /api/devices/:id, POST /api/sms

// Global root sanity route
app.get('/', (req, res) => {
  const serverIp = cachedServerIp;
  const message = req.query.msg || "";
  const isError = req.query.error === "true";

  res.send(`
    <!DOCTYPE html>
    <html lang="id">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
      <title>SMS Bridge Control Panel</title>
      <style>
        * { box-sizing: border-box; }
        body { 
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; 
          display: flex; 
          align-items: center; 
          justify-content: center; 
          height: 100vh; 
          width: 100vw;
          margin: 0; 
          background: #f0f2f5; 
          color: #1c1e21; 
          overflow: hidden;
        }
        .card { 
          background: white; 
          padding: 2rem; 
          border-radius: 16px; 
          box-shadow: 0 8px 24px rgba(0,0,0,0.1); 
          text-align: center; 
          width: 90%; 
          max-width: 400px;
          max-height: 95vh;
          display: flex;
          flex-direction: column;
          justify-content: center;
        }
        h1 { margin-top: 0; margin-bottom: 0.5rem; font-size: 1.4rem; color: #007bff; }
        p { color: #606770; line-height: 1.4; margin-bottom: 1rem; font-size: 0.9rem; }
        .status { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 0.8rem; font-weight: 600; margin-bottom: 1rem; align-self: center; }
        .status.disconnected { background: #ffebe9; color: #d73a49; }
        .status.connected { background: #dcffe4; color: #28a745; }
        .status.loading { background: #fff5d5; color: #856404; }
        .ip-box { background: #f8f9fa; border: 1px dashed #dee2e6; padding: 8px; border-radius: 8px; font-family: monospace; font-size: 0.8rem; margin-bottom: 1rem; word-break: break-all; }
        .pin-input { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 8px; margin-bottom: 0.8rem; font-size: 0.9rem; text-align: center; }
        .btn { display: block; width: 100%; padding: 12px; color: white; border: none; border-radius: 10px; font-size: 0.95rem; font-weight: bold; cursor: pointer; transition: transform 0.2s, background 0.2s; margin-bottom: 0.4rem; }
        .btn-connect { background: #007bff; }
        .btn-connect:hover { background: #0056b3; }
        .btn-disconnect { background: #d73a49; }
        .btn-disconnect:hover { background: #b62b39; }
        .btn:disabled { background: #ccc; cursor: not-allowed; }
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
        .spinner { display: inline-block; width: 16px; height: 16px; border: 2px solid rgba(255,255,255,0.3); border-radius: 50%; border-top-color: #fff; animation: spin 1s ease-in-out infinite; margin-right: 8px; vertical-align: middle; }
        .alert { padding: 8px; border-radius: 8px; margin-bottom: 0.8rem; font-size: 0.8rem; }
        .alert-error { background: #ffebe9; color: #d73a49; border: 1px solid #ffd3d0; }
        .alert-success { background: #dcffe4; color: #28a745; border: 1px solid #bef5cb; }
        .error-details { font-size: 0.7rem; color: #d73a49; background: #fff5f5; padding: 6px; border-radius: 6px; margin-top: 0.8rem; display: ${connectionError ? 'block' : 'none'}; text-align: left; overflow-y: auto; max-height: 60px; }
      </style>
    </head>
    <body>
      <div class="card">
        <h1>SMS Bridge Control Panel</h1>
        <p>Kelola koneksi database MongoDB Atlas Anda dengan aman.</p>
        
        <div class="ip-box">IP Server: ${serverIp}</div>
        
        <div class="status ${dbStatus.toLowerCase().replace('...', '')}">Status: ${dbStatus}</div>

        ${message ? `<div class="alert ${isError ? 'alert-error' : 'alert-success'}">${message}</div>` : ''}
        
        <form id="control-form" method="POST">
          <input type="password" name="pin" class="pin-input" placeholder="Masukkan PIN Keamanan" required>
          
          ${dbStatus === 'Connected' ? '' : `
            <input type="text" name="mongo_url" class="pin-input" placeholder="Custom MongoDB URL (Opsional)" style="font-size: 0.85rem;">
          `}
          
          ${dbStatus === 'Connected' ? `
            <button type="submit" formaction="/disconnect-db" class="btn btn-disconnect">Putuskan Koneksi</button>
          ` : `
            <button type="submit" formaction="/connect-db" id="connect-btn" class="btn btn-connect" ${dbStatus === 'Connecting...' ? 'disabled' : ''}>
              ${dbStatus === 'Connecting...' ? '<span class="spinner"></span>Menghubungkan...' : 'Hubungkan ke MongoDB'}
            </button>
          `}
        </form>

        <div class="error-details">
          <strong>Terakhir Error:</strong><br>
          ${connectionError || ''}
        </div>
      </div>
      <script>
        const form = document.getElementById('control-form');
        const buttons = document.querySelectorAll('.btn');
        form.onsubmit = () => {
          const connectBtn = document.getElementById('connect-btn');
          if (connectBtn) {
            connectBtn.innerHTML = '<span class="spinner"></span>Menghubungkan...';
          }
          buttons.forEach(btn => btn.disabled = true);
        };
      </script>
    </body>
    </html>
  `);
});

app.post('/connect-db', (req, res) => {
  const { pin, mongo_url } = req.body;

  if (pin !== PANEL_PIN) {
    return res.redirect('/?error=true&msg=PIN+Salah!');
  }

  if (dbStatus === 'Connected' || dbStatus === 'Connecting...') {
    return res.redirect('/');
  }

  dbStatus = "Connecting...";
  connectionError = null;
  
  const targetUri = mongo_url || MONGODB_URI;
  console.log("Memulai koneksi database ke:", targetUri ? targetUri.replace(/\/\/.*@/, "//***:***@") : "NULL");
  
  if (!targetUri) {
    dbStatus = "Disconnected";
    connectionError = "MONGODB_URI tidak ditemukan di .env dan tidak ada URL kustom yang disediakan.";
    return res.redirect('/?error=true&msg=URL+MongoDB+Kosong');
  }

  mongoose.connect(targetUri)
    .then(() => {
      dbStatus = "Connected";
      console.log("Database terhubung.");
      // Simpan konfigurasi untuk auto-connect
      try {
        fs.writeFileSync(DB_CONFIG_FILE, JSON.stringify({ ip: cachedServerIp, uri: targetUri }));
        console.log("Konfigurasi auto-connect disimpan.");
      } catch (e) {
        console.error("Gagal menyimpan database.json:", e.message);
      }
      res.redirect('/?msg=Koneksi+Berhasil');
    })
    .catch((err) => {
      dbStatus = "Disconnected";
      connectionError = err.message;
      console.error("Gagal koneksi:", err.message);
      res.redirect('/?error=true&msg=Koneksi+Gagal');
    });
});

app.post('/disconnect-db', (req, res) => {
  const { pin } = req.body;

  if (pin !== PANEL_PIN) {
    return res.redirect('/?error=true&msg=PIN+Salah!');
  }

  if (dbStatus !== 'Connected') {
    return res.redirect('/');
  }

  console.log("Memutuskan koneksi database...");
  mongoose.disconnect()
    .then(() => {
      dbStatus = "Disconnected";
      console.log("Database terputus.");
      // Hapus konfigurasi auto-connect saat diputus manual
      if (fs.existsSync(DB_CONFIG_FILE)) {
        try {
          fs.unlinkSync(DB_CONFIG_FILE);
          console.log("Konfigurasi auto-connect dihapus.");
        } catch (e) {
          console.error("Gagal menghapus database.json:", e.message);
        }
      }
      res.redirect('/?msg=Koneksi+Diputuskan');
    })
    .catch((err) => {
      console.error("Gagal memutus koneksi:", err.message);
      res.redirect('/?error=true&msg=Gagal+Memutus+Koneksi');
    });
});

// Global error handling middleware
app.use((err, req, res, next) => {
  console.error("Unhandled Error Log:", err);
  res.status(500).json({ error: "Terjadi kesalahan internal pada server backend." });
});

// Start app listener immediately
app.listen(PORT, '0.0.0.0', () => {
  console.log(`\n=========================================`);
  console.log(`SMS Bridge Backend SIAP`);
  console.log(`Akses Control Panel di: http://${cachedServerIp}:${PORT}`);
  console.log(`=========================================\n`);
});