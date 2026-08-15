import crypto from 'node:crypto';

/**
 * 音源配置的加密存储：AES-256-GCM。
 * 加密后的格式：`iv:authTag:ciphertext`（均为 base64）。
 * 主密钥来自环境变量 ENCRYPTION_KEY（32 字节）。
 */

export function encryptSecret(plaintext, key) {
  if (plaintext == null || plaintext === '') return '';
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const enc = Buffer.concat([cipher.update(String(plaintext), 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return [iv.toString('base64'), authTag.toString('base64'), enc.toString('base64')].join(':');
}

export function decryptSecret(payload, key) {
  if (!payload) return '';
  const parts = String(payload).split(':');
  if (parts.length !== 3) throw new Error('密文格式错误');
  const [ivB64, tagB64, dataB64] = parts;
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(ivB64, 'base64'));
  decipher.setAuthTag(Buffer.from(tagB64, 'base64'));
  const dec = Buffer.concat([decipher.update(Buffer.from(dataB64, 'base64')), decipher.final()]);
  return dec.toString('utf8');
}

/** 对音源 config 对象整体加密（返回加密字符串） */
export function encryptConfig(configObj, key) {
  return encryptSecret(JSON.stringify(configObj || {}), key);
}

/** 解密音源 config 字符串（返回对象） */
export function decryptConfig(configStr, key) {
  if (!configStr) return {};
  try {
    return JSON.parse(decryptSecret(configStr, key));
  } catch {
    // 兼容：若本身是明文 JSON（历史数据），直接解析
    try { return JSON.parse(configStr); } catch { return {}; }
  }
}

/** 脱敏：把敏感字段打码，供管理 API 展示用 */
export function maskConfig(configObj) {
  const sensitive = ['apiKey', 'secretKey', 'cookie', 'token', 'password'];
  const masked = {};
  for (const [k, v] of Object.entries(configObj || {})) {
    if (sensitive.includes(k) && typeof v === 'string' && v) {
      masked[k] = v.slice(0, 4) + '****' + v.slice(-4);
    } else {
      masked[k] = v;
    }
  }
  return masked;
}
