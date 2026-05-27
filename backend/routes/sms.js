const express = require('express');
const router = express.Router();
const SmsLog = require('../models/SmsLog');
const Device = require('../models/Device');
const { authenticateAdmin, authenticateDevice } = require('../middlewares/auth');

// POST /api/sms (Authorized client device posts intercepted SMS log here)
router.post('/sms', authenticateDevice, async (req, res) => {
  const { sender, message, received_at } = req.body;

  if (!sender || !message || !received_at) {
    return res.status(400).json({ error: 'Sender, message, dan received_at wajib dikirim.' });
  }

  try {
    const device = req.registeredDevice;

    // Track active connection timestamp
    device.lastSeen = new Date();
    await device.save();

    const newSms = new SmsLog({
      userId: device.userId,
      deviceId: device._id,
      sender,
      message,
      receivedAt: new Date(received_at)
    });

    await newSms.save();

    res.status(201).json({
      success: true,
      message: 'SMS berhasil disimpan ke database backend.'
    });
  } catch (error) {
    res.status(500).json({ error: 'Kesalahan server saat memproses payload SMS.' });
  }
});

// POST /api/sms/unpair (Client device unpairs/revokes itself)
router.post('/sms/unpair', authenticateDevice, async (req, res) => {
  try {
    const device = req.registeredDevice;
    device.revoked = true;
    await device.save();
    res.json({
      success: true,
      message: 'Device berhasil dilepaskan dari backend.'
    });
  } catch (error) {
    res.status(500).json({ error: 'Gagal melepaskan kaitan device pada backend.' });
  }
});

// GET /api/devices (Admin only - retrieves list of linked devices)
router.get('/devices', authenticateAdmin, async (req, res) => {
  const userId = req.adminUser.userId;

  try {
    const devices = await Device.find({ userId, revoked: { $ne: true } }).sort({ createdAt: -1 });
    res.json(devices);
  } catch (error) {
    res.status(500).json({ error: 'Gagal mengambil data devices.' });
  }
});

// DELETE /api/devices/:id (Admin only - revoke/uninstall a device)
router.delete('/devices/:id', authenticateAdmin, async (req, res) => {
  const userId = req.adminUser.userId;
  const deviceId = req.params.id;

  try {
    const device = await Device.findOne({ _id: deviceId, userId });
    if (!device) {
      return res.status(404).json({ error: 'Device tidak ditemukan.' });
    }

    await device.deleteOne();

    res.json({
      success: true,
      message: 'Device berhasil dihapus secara permanen.'
    });
  } catch (error) {
    res.status(500).json({ error: 'Gagal membatalkan registrasi/revoke device.' });
  }
});

// GET /api/sms (Admin only - retrieves active logs with optional filter by device_id)
router.get('/sms', authenticateAdmin, async (req, res) => {
  const userId = req.adminUser.userId;
  const { device_id } = req.query;

  try {
    const query = { userId };
    if (device_id) {
      query.deviceId = device_id;
    }

    const logs = await SmsLog.find(query)
      .populate('deviceId', 'name model lastSeen')
      .sort({ receivedAt: -1 });

    res.json(logs);
  } catch (error) {
    res.status(500).json({ error: 'Gagal mengambil log sinkronisasi SMS.' });
  }
});

// DELETE /api/sms (Admin only - delete all SMS logs, optionally filtered by device_id)
router.delete('/sms', authenticateAdmin, async (req, res) => {
  const userId = req.adminUser.userId;
  const { device_id } = req.query;
  console.log(`[BACKEND] Menghapus semua SMS untuk userId: ${userId}, deviceId: ${device_id || 'SEMUA'}`);

  try {
    const query = { userId };
    if (device_id) {
      query.deviceId = device_id;
    }

    const result = await SmsLog.deleteMany(query);
    console.log(`[BACKEND] Berhasil menghapus ${result.deletedCount} SMS.`);
    res.json({
      success: true,
      message: `Berhasil menghapus ${result.deletedCount} SMS.`
    });
  } catch (error) {
    console.error('[BACKEND] Gagal menghapus semua SMS:', error);
    res.status(500).json({ error: 'Gagal menghapus semua SMS.' });
  }
});

// DELETE /api/sms/:id (Admin only - delete a single SMS log)
router.delete('/sms/:id', authenticateAdmin, async (req, res) => {
  const userId = req.adminUser.userId;
  const smsId = req.params.id;
  console.log(`[BACKEND] Menghapus satu SMS id: ${smsId} untuk userId: ${userId}`);

  try {
    const result = await SmsLog.deleteOne({ _id: smsId, userId });
    if (result.deletedCount === 0) {
      return res.status(404).json({ error: 'SMS tidak ditemukan.' });
    }
    res.json({ success: true, message: 'SMS berhasil dihapus.' });
  } catch (error) {
    console.error('[BACKEND] Gagal menghapus SMS tunggal:', error);
    res.status(500).json({ error: 'Gagal menghapus SMS.' });
  }
});

module.exports = router;
