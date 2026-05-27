const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const PairCode = require('../models/PairCode');
const Device = require('../models/Device');
const { authenticateAdmin } = require('../middlewares/auth');

// POST /api/pairing/create (Admin only - generates 6 digit pairing code valid for 5 min)
router.post('/create', authenticateAdmin, async (req, res) => {
  const userId = req.adminUser.userId;

  try {
    let code;
    let codeExists = true;

    // Ensure collision-safe pairing codes
    while (codeExists) {
      code = Math.floor(100000 + Math.random() * 900000).toString();
      const existingCode = await PairCode.findOne({ code, usedAt: null, expiresAt: { $gt: new Date() } });
      if (!existingCode) codeExists = false;
    }

    const expiresAt = new Date();
    expiresAt.setHours(expiresAt.getHours() + 24); // Expires in 24 hours

    const newPairCode = new PairCode({
      userId,
      code,
      expiresAt
    });

    await newPairCode.save();

    res.status(201).json({
      success: true,
      code,
      expires_at: expiresAt
    });
  } catch (error) {
    res.status(500).json({ error: 'Gagal membuat kode pairing.' });
  }
});

// POST /api/pairing/claim (Client APK calls this to request registration credentials)
router.post('/claim', async (req, res) => {
  const { code, device_name, device_model } = req.body;

  if (!device_name || !device_model) {
    return res.status(400).json({ error: 'Nama device, dan model device wajib dikirim.' });
  }

  try {
    let targetUserId = null;

    if (code === "AUTO_PAIR") {
      const User = require('../models/User');
      const user = await User.findOne().sort({ createdAt: 1 });
      if (!user) {
        return res.status(400).json({ error: 'Belum ada administrator yang terdaftar di sistem server.' });
      }
      targetUserId = user._id;
    } else {
      if (!code) {
        return res.status(400).json({ error: 'Kode pairing wajib dikirim atau gunakan AUTO_PAIR.' });
      }
      // Find matching active code
      const pairCode = await PairCode.findOne({
        code,
        usedAt: null,
        expiresAt: { $gt: new Date() }
      });

      if (!pairCode) {
        return res.status(400).json({ error: 'Kode pairing tidak valid, telah kedaluwarsa (expired), atau sudah terpakai.' });
      }

      // Mark code as used immediately to avoid replay attacks
      pairCode.usedAt = new Date();
      await pairCode.save();
      targetUserId = pairCode.userId;
    }

    // Create a robust cryptographic device token
    const rawDeviceToken = crypto.randomBytes(32).toString('hex');
    const tokenHash = crypto.createHash('sha256').update(rawDeviceToken).digest('hex');

    let device = await Device.findOne({
      userId: targetUserId,
      name: device_name,
      model: device_model
    });

    if (device) {
      device.tokenHash = tokenHash;
      device.revoked = false;
      device.lastSeen = new Date();
      await device.save();
    } else {
      device = new Device({
        userId: targetUserId,
        name: device_name,
        model: device_model,
        tokenHash
      });
      await device.save();
    }

    res.status(200).json({
      device_id: device._id,
      device_token: rawDeviceToken
    });
  } catch (error) {
    res.status(500).json({ error: 'Gagal memproses registrasi device.' });
  }
});

module.exports = router;
