import 'dotenv/config';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));

function requireEnv(name) {
  const v = process.env[name];
  if (!v) throw new Error(`缺少环境变量 ${name}，请检查 backend/.env（可参考 .env.example）`);
  return v;
}

const encryptionKeyHex = requireEnv('ENCRYPTION_KEY');
const encryptionKey = Buffer.from(encryptionKeyHex, 'hex');
if (encryptionKey.length !== 32) {
  throw new Error('ENCRYPTION_KEY 必须是 32 字节（64 个 hex 字符）');
}

export const config = {
  port: parseInt(process.env.PORT || '3000', 10),
  host: process.env.HOST || '127.0.0.1',

  // 管理端登录账号
  adminUser: process.env.ADMIN_USER || 'admin',
  adminPassword: process.env.ADMIN_PASSWORD || '',

  // 跨域：'*' = 全放开（默认，学习项目）；逗号分隔白名单（如 'https://a.com,https://b.com'）
  corsOrigin: process.env.CORS_ORIGIN || '*',

  // 限速总开关（调试用；生产建议保持开启）
  rateLimitDisabled: process.env.RATE_LIMIT_DISABLED === '1',

  // 音源密钥加密主密钥（Buffer，32 字节）
  encryptionKey,

  // 数据库：libSQL 连接串
  //   file:./data/music.db          本地文件（单机）
  //   http://<host>:<port>          远端 libSQL 服务（分布式部署，多节点共享中央库）
  //   libsql://xxx.turso.io         Turso Cloud
  dbUrl: process.env.DB_URL || `file:${join(__dirname, '..', '..', 'data', 'music.db')}`,
  dbAuthToken: process.env.DB_AUTH_TOKEN || undefined,

  // 种子数据用（可选）
  kuwoApiKey: process.env.KUWO_API_KEY || '',
  kuwoSecretKey: process.env.KUWO_SECRET_KEY || '',
  chkszApiKey: process.env.CHKSZ_API_KEY || '',
};
