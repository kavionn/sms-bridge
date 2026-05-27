const express = require('express');
const router = express.Router();
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const User = require('../models/User');

// POST /api/auth/admin/register
router.post('/admin/register', async (req, res) => {
  const { email, password } = req.body;

  if (!email || !password) {
    return res.status(400).json({ error: 'Email dan password wajib diisi.' });
  }

  try {
    const existingUser = await User.findOne({ email });
    if (existingUser) {
      return res.status(400).json({ error: 'Email admin sudah terdaftar.' });
    }

    const saltRounds = 10;
    const passwordHash = await bcrypt.hash(password, saltRounds);

    const newUser = new User({
      email,
      passwordHash
    });

    await newUser.save();

    res.status(201).json({
      success: true,
      message: 'Registrasi akun admin berhasil.'
    });
  } catch (error) {
    res.status(500).json({ error: 'Terjadi kesalahan sistem saat registrasi.' });
  }
});

// POST /api/auth/admin/login
router.post('/admin/login', async (req, res) => {
  const { email, password } = req.body;

  if (!email || !password) {
    return res.status(400).json({ error: 'Email dan password wajib diisi.' });
  }

  try {
    const expectedPin = process.env.PIN || process.env.ADMIN_PASSWORD || '123456';
    if (password !== expectedPin) {
      return res.status(401).json({ error: 'Password PIN Admin salah. Silakan periksa konfigurasi env server Anda.' });
    }

    // Direct match with ENV PIN. Now make sure this user exists in DB with the hash so other associations remain valid.
    let user = await User.findOne({ email });
    const saltRounds = 10;
    const passwordHash = await bcrypt.hash(expectedPin, saltRounds);

    if (!user) {
      user = new User({
        email,
        passwordHash
      });
      await user.save();
    } else {
      user.passwordHash = passwordHash;
      await user.save();
    }

    const token = jwt.sign(
      { userId: user._id, email: user.email },
      process.env.JWT_SECRET || 'super_secure_jwt_token_secret_for_admin_sessions',
      { expiresIn: '7d' }
    );

    res.json({
      success: true,
      token,
      admin: {
        id: user._id,
        email: user.email
      }
    });
  } catch (error) {
    res.status(500).json({ error: 'Terjadi kesalahan sistem saat login: ' + error.message });
  }
});

module.exports = router;
