import 'dotenv/config';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));

function intEnv(name, fallback, min = 1) {
  const n = parseInt(process.env[name] || '', 10);
  if (Number.isFinite(n)) return Math.max(min, n);
  return fallback;
}

export const config = {
  port: intEnv('PORT', 8787),
  host: process.env.HOST || '0.0.0.0',

  // 数据库：libSQL 连接串。
  //   file:  本地文件（单机，默认）
  //   http://  -> 远端 libSQL 服务（sqld）
  //   libsql:// -> Turso Cloud
  dbUrl: process.env.DB_URL || `file:${join(__dirname, '..', '..', 'data', 'music.db')}`,
  dbAuthToken: process.env.DB_AUTH_TOKEN || undefined,

  // 反向代理后正确识别客户端 IP（用于限速），默认关闭
  trustProxy: process.env.TRUST_PROXY === '1',
  rateLimitDisabled: process.env.RATE_LIMIT_DISABLED === '1',

  // 会话策略
  sessionTtlDays: intEnv('SESSION_TTL_DAYS', 90),
  maxSessionsPerUser: intEnv('MAX_SESSIONS_PER_USER', 10),

  // 邮件服务（Cloudflare 自建邮件系统）
  mailServiceUrl: process.env.MAIL_SERVICE_URL || 'https://mail.dovislab.com',
  mailAdminAuth: process.env.MAIL_ADMIN_AUTH || 'REDACTED',
  mailFromAddress: process.env.MAIL_FROM_ADDRESS || 'melos@dovislab.com',
  mailFromName: process.env.MAIL_FROM_NAME || 'Melos 音乐',
};