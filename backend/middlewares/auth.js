const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const Device = require('../models/Device');

/**
 * Middleware strictly validates JWT authentication for administrative endpoints.
 */
const authenticateAdmin = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Format token tidak valid atau tidak ditemukan.' });
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET || 'super_secure_jwt_token_secret_for_admin_sessions');
    req.adminUser = decoded;
    next();
  } catch (error) {
    return res.status(401).json({ error: 'Sesi kedaluwarsa atau token tidak valid.' });
  }
};

/**
 * Middleware validates the device token passed by our background services.
 * Hashing token in SHA-256 and querying the database ensures database leaks do not compromise active nodes.
 */
const authenticateDevice = async (req, res, next) => {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Device token tidak ditemukan.' });
  }

  const rawToken = authHeader.split(' ')[1];
  const tokenHash = crypto.createHash('sha256').update(rawToken).digest('hex');

  try {
    const device = await Device.findOne({ tokenHash, revoked: false });
    if (!device) {
      return res.status(403).json({ error: 'Device tidak dikenal atau akses telah dicekal/revoked.' });
    }

    req.registeredDevice = device;
    next();
  } catch (error) {
    return res.status(500).json({ error: 'Kesalahan internal server saat authentikasi device.' });
  }
};

module.exports = {
  authenticateAdmin,
  authenticateDevice
};
