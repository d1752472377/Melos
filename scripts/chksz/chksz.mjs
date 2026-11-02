#!/usr/bin/env node
/**
 * ChKSz API 命令行工具
 *
 * 用途：在电脑上先把接口跑通、把歌找对，再导入 App 的音源插件（chksz-source.js）。
 * 也用来把歌直接下载到本地。
 *
 * 用法：
 *   node chksz.mjs build                                   # 生成 dist/chksz-source.local.js（注入 Key）
 *   node chksz.mjs probe                                   # 三个平台各发一次请求，看谁通（消耗 3 次额度）
 *   node chksz.mjs search wy 晴天                          # 网易云搜索
 *   node chksz.mjs search tx 七里香                        # QQ 音乐搜索
 *   node chksz.mjs url wy 2652820720 --probe               # 取直链并探测可访问性
 *   node chksz.mjs url tx 004Z8Ihr0JIu5s --size flac
 *   node chksz.mjs lyric wy 2652820720 --out 晴天.lrc
 *   node chksz.mjs playlist 3778678 --limit 20
 *   node chksz.mjs download wy 2652820720 --out ./music
 *
 * Key 来源（按优先级）：--key 参数 > 环境变量 CHKSZ_API_KEY > 同目录 chksz.key 文件
 * Key 不会写进任何日志或文件（build 生成的 dist/ 已在 .gitignore 里）。
 */

import { createWriteStream, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { pipeline } from 'node:stream/promises';
import { Readable } from 'node:stream';
import { resilientFetch } from './chksz-http.mjs';

const ROOT = dirname(fileURLToPath(import.meta.url));
const API_BASE = 'https://api.chksz.com';
const TEMPLATE = join(ROOT, 'chksz-source.js');
const DIST_DIR = join(ROOT, 'dist');

// ==================== 参数解析 ====================

function parseArgs(argv) {
  const positional = [];
  const flags = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--json') flags.json = true;
    else if (a === '--verbose' || a === '-v') flags.verbose = true;
    else if (a === '--probe') flags.probe = true;
    else if (a === '--help' || a === '-h') flags.help = true;
    else if (a.startsWith('--')) {
      const key = a.slice(2);
      const next = argv[i + 1];
      if (next === undefined || next.startsWith('--')) flags[key] = true;
      else {
        flags[key] = next;
        i++;
      }
    } else positional.push(a);
  }
  return { positional, flags };
}

// ==================== Key ====================

function loadKey(flags) {
  if (typeof flags.key === 'string' && flags.key.trim()) return flags.key.trim();
  if (process.env.CHKSZ_API_KEY && process.env.CHKSZ_API_KEY.trim()) return process.env.CHKSZ_API_KEY.trim();
  const keyFile = join(ROOT, 'chksz.key');
  if (existsSync(keyFile)) {
    const text = readFileSync(keyFile, 'utf8').trim();
    if (text) return text;
  }
  return '';
}

function requireKey(flags) {
  const key = loadKey(flags);
  if (!key) {
    fail(
      '没有找到 API Key。三种给法任选一种：\n' +
        '  1) node chksz.mjs ... --key chksz_xxx\n' +
        '  2) 设置环境变量 CHKSZ_API_KEY\n' +
        `  3) 在 ${join(ROOT, 'chksz.key')} 里写一行 Key`
    );
  }
  if (!key.startsWith('chksz_')) {
    warn('Key 不是以 chksz_ 开头，可能复制错了（继续尝试）');
  }
  return key;
}

// ==================== 输出 ====================

const NO_COLOR = !process.stdout.isTTY || process.env.NO_COLOR;
const paint = (code) => (text) => (NO_COLOR ? String(text) : `\u001b[${code}m${text}\u001b[0m`);
const dim = paint('2');
const bold = paint('1');
const green = paint('32');
const red = paint('31');
const yellow = paint('33');
const cyan = paint('36');

function warn(msg) {
  console.error(yellow('! ' + msg));
}

function fail(msg) {
  console.error(red('× ' + msg));
  // 不用 process.exit()：还有未关闭的请求句柄时强杀会触发 libuv 断言崩溃，
  // 设 exitCode 让事件循环自然排空后退出
  process.exitCode = 1;
  throw new CliExit();
}

/** 中日韩字符占两列，表格对齐必须按显示宽度算，不能按 .length */
function displayWidth(text) {
  let width = 0;
  for (const ch of String(text)) {
    const code = ch.codePointAt(0);
    const wide =
      (code >= 0x1100 && code <= 0x115f) ||
      (code >= 0x2e80 && code <= 0xa4cf) ||
      (code >= 0xac00 && code <= 0xd7a3) ||
      (code >= 0xf900 && code <= 0xfaff) ||
      (code >= 0xfe30 && code <= 0xfe6f) ||
      (code >= 0xff00 && code <= 0xff60) ||
      (code >= 0xffe0 && code <= 0xffe6) ||
      (code >= 0x1f300 && code <= 0x1f64f);
    width += wide ? 2 : 1;
  }
  return width;
}

function pad(text, width) {
  const s = String(text ?? '');
  const padWidth = width - displayWidth(s);
  return padWidth > 0 ? s + ' '.repeat(padWidth) : s;
}

function truncate(text, max) {
  const s = String(text ?? '');
  return displayWidth(s) <= max ? s : s.slice(0, Math.max(1, max - 1)) + '…';
}

function printTable(headers, rows) {
  const widths = headers.map((h, i) =>
    Math.min(42, Math.max(displayWidth(h), ...rows.map((r) => displayWidth(r[i] ?? ''))))
  );
  console.log(headers.map((h, i) => bold(pad(h, widths[i]))).join('  '));
  console.log(dim(widths.map((w) => '─'.repeat(w)).join('  ')));
  for (const row of rows) {
    console.log(row.map((cell, i) => pad(truncate(cell, widths[i]), widths[i])).join('  '));
  }
}

function formatDuration(ms) {
  const total = Math.round(Number(ms) / 1000);
  if (!Number.isFinite(total) || total <= 0) return '-';
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
}

function formatBytes(bytes) {
  const n = Number(bytes);
  if (!Number.isFinite(n) || n <= 0) return '-';
  if (n < 1024) return `${n}B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)}KB`;
  return `${(n / 1024 / 1024).toFixed(1)}MB`;
}

// ==================== HTTP ====================

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** fail() 用来中断命令的哨兵异常：真正的报错已打印，catch 里直接吞掉 */
class CliExit {}

class ApiError extends Error {
  constructor(status, message, headers) {
    super(message);
    this.status = status;
    this.headers = headers || {};
  }
}

/** 把 HTTP 状态翻译成人能看懂的中文（文档第 9 节） */
function describeStatus(status, payload) {
  const msg = payload?.msg || payload?.message || '';
  switch (status) {
    case 400:
      return `参数有误${msg ? '：' + msg : ''}`;
    case 401:
      return 'API Key 无效或未登录（去 api.chksz.com 重新复制）';
    case 402:
      return '免费与付费额度都已用尽（次日 UTC+8 重置，或兑换 LDC）';
    case 403:
      return `用户 / Key / IP 被封禁${msg ? '：' + msg : ''}`;
    case 404:
      return msg || '接口或资源不存在';
    case 429:
      return '触发限流（每 Key 每分钟 20 次）';
    case 502:
      return `上游 502${msg ? '：' + msg : ''}`;
    case 503:
      return `上游暂时不可用${msg ? '：' + msg : ''}`;
    default:
      return `HTTP ${status}${msg ? '：' + msg : ''}`;
  }
}

/**
 * 调一个业务接口：拼 apikey、处理限流、解包 data、打印额度。
 *
 * @returns {Promise<any>} 业务 payload（已剥掉 {code,msg,data} 外壳）
 */
async function api(path, { key, verbose, retries = 3, quiet = false }) {
  const url = `${API_BASE}${path}${path.includes('?') ? '&' : '?'}apikey=${encodeURIComponent(key)}`;
  let lastError = null;

  for (let attempt = 1; attempt <= retries; attempt++) {
    let res;
    try {
      res = await resilientFetch(url, { verbose });
    } catch (err) {
      lastError = new Error(`网络错误：${err.message}`);
      // 网络层失败最多再试一次：这条链路要么马上好，要么就是真的连不上，
      // 反复重试只会让命令卡上好几分钟
      if (attempt < Math.min(retries, 2)) {
        await sleep(800 * attempt);
        continue;
      }
      throw lastError;
    }

    let payload = null;
    try {
      payload = JSON.parse(res.text);
    } catch {
      payload = null;
    }

    const free = res.headers['x-quota-free-remaining'];
    const paid = res.headers['x-quota-paid-remaining'];
    if (verbose && (free !== undefined || paid !== undefined)) {
      console.error(dim(`  额度：免费 ${free ?? '-'} / 付费 ${paid ?? '-'}`));
    }

    // 限流：按 Retry-After 等一次，只重试一次（文档第 9 节）
    if (res.status === 429) {
      const wait = Number(res.headers['retry-after'] || 0);
      if (attempt < retries && wait > 0 && wait <= 30) {
        warn(`触发限流，等待 ${wait} 秒后重试一次`);
        await sleep(wait * 1000);
        continue;
      }
      throw new ApiError(429, describeStatus(429, payload), res.headers);
    }

    // 上游抖动：502/503 值得退避重试一次，因为额度不扣
    if ((res.status === 502 || res.status === 503) && attempt < retries) {
      warn(`${describeStatus(res.status, payload)}，${attempt} 秒后重试`);
      await sleep(1000 * attempt);
      lastError = new ApiError(res.status, describeStatus(res.status, payload), res.headers);
      continue;
    }

    if (res.status < 200 || res.status >= 300) {
      throw new ApiError(res.status, describeStatus(res.status, payload), res.headers);
    }

    // HTTP 200 但 body 里 code 非 200：同样按失败处理（文档第 10 节第 6 条）
    if (payload && typeof payload.code === 'number' && payload.code !== 200) {
      throw new ApiError(payload.code, describeStatus(payload.code, payload), res.headers);
    }

    if (!quiet && free !== undefined) {
      console.error(dim(`今日免费额度剩余 ${free} 次`));
    }

    if (payload && payload.data !== undefined) return payload.data;
    return payload;
  }

  throw lastError || new Error('请求失败');
}

/** 探测音频地址是否真能播：HEAD 不行就退化成一个字节的 Range 请求 */
async function probeAudio(url, { verbose } = {}) {
  const attempt = async (method) => {
    const res = await fetch(url, {
      method,
      signal: AbortSignal.timeout(20_000),
      headers: method === 'GET' ? { Range: 'bytes=0-0' } : {},
      redirect: 'follow',
    });
    return {
      status: res.status,
      type: res.headers.get('content-type') || '',
      length: res.headers.get('content-length') || '',
    };
  };
  let result;
  try {
    result = await attempt('HEAD');
    if ([403, 405, 501].includes(result.status)) result = await attempt('GET');
  } catch (err) {
    if (verbose) warn(`探测失败：${err.message}`);
    return { ok: false, summary: `无法连接（${err.message}）` };
  }
  const audioLike = /audio|octet-stream|video|mpeg/i.test(result.type);
  const ok = (result.status === 200 || result.status === 206) && audioLike;
  return {
    ok,
    summary: `HTTP ${result.status}，${result.type || '未知类型'}${result.length ? '，' + formatBytes(result.length) : ''}`,
  };
}

// ==================== 平台定义 ====================

const PLATFORMS = {
  wy: { label: '网易云', aliases: ['wy', 'netease', '163', 'wangyi'] },
  tx: { label: 'QQ 音乐', aliases: ['tx', 'qq', 'tencent'] },
  kg: { label: '酷狗', aliases: ['kg', 'kugou'] },
};

function platformOf(raw) {
  const key = String(raw || '').toLowerCase();
  for (const [code, def] of Object.entries(PLATFORMS)) {
    if (code === key || def.aliases.includes(key)) return code;
  }
  return null;
}

const NETEASE_LEVELS = ['standard', 'exhigh', 'lossless', 'hires', 'jyeffect', 'sky', 'jymaster'];
const QQKG_SIZES = ['128k', '320k', 'flac', 'hires', 'master'];

/** LX 音质写法 → 接口参数，方便"插件里怎么写、命令行就怎么传" */
function normalizeNeteaseLevel(raw) {
  const value = String(raw || 'lossless').toLowerCase();
  const alias = { '128k': 'standard', '320k': 'exhigh', 'flac': 'lossless', 'flac24bit': 'hires' };
  const level = alias[value] || value;
  if (!NETEASE_LEVELS.includes(level)) fail(`网易云音质只能是 ${NETEASE_LEVELS.join(' / ')}（或 128k/320k/flac/flac24bit）`);
  return level;
}

function normalizeSize(raw) {
  const value = String(raw || 'flac').toLowerCase();
  const alias = { 'flac24bit': 'hires', 'lossless': 'flac', 'standard': '128k', 'exhigh': '320k' };
  const size = alias[value] || value;
  if (!QQKG_SIZES.includes(size)) fail(`QQ/酷狗音质只能是 ${QQKG_SIZES.join(' / ')}`);
  return size;
}

// ==================== 子命令 ====================

async function cmdSearch(positional, flags) {
  const platform = platformOf(positional[0]);
  const keyword = positional.slice(1).join(' ').trim();
  if (!platform) fail('用法：node chksz.mjs search <wy|tx|kg> <关键词> [--limit 10]');
  if (!keyword) fail('缺少搜索关键词');
  const limit = Math.min(50, Math.max(1, Number(flags.limit || 10)));
  const key = requireKey(flags);

  if (platform === 'wy') {
    const data = await api(
      `/api/163_search?keyword=${encodeURIComponent(keyword)}&limit=${limit}&offset=0`,
      { key, verbose: flags.verbose }
    );
    const songs = data?.songs || [];
    if (flags.json) return console.log(JSON.stringify({ total: data?.total, songs }, null, 2));
    if (!songs.length) return console.log('没有搜到结果');
    console.log(bold(`网易云「${keyword}」共 ${data?.total ?? songs.length} 条，显示前 ${songs.length} 条`));
    printTable(
      ['#', 'id', '歌名', '歌手', '专辑', '时长'],
      songs.map((s, i) => [String(i + 1), String(s.id), s.name, s.artists, s.album, formatDuration(s.duration)])
    );
    return;
  }

  if (platform === 'tx') {
    const data = await api(
      `/api/qq_music?msg=${encodeURIComponent(keyword)}&num=${limit}`,
      { key, verbose: flags.verbose }
    );
    const list = data?.list || [];
    if (flags.json) return console.log(JSON.stringify(data, null, 2));
    if (!list.length) return console.log('没有搜到结果');
    console.log(bold(`QQ 音乐「${keyword}」共 ${data?.count ?? list.length} 条，显示前 ${list.length} 条`));
    printTable(
      ['#', 'mid', '歌名', '歌手', '专辑', '付费'],
      list.map((s, i) => [String(s.n ?? i + 1), String(s.mid ?? ''), s.name, s.singer, s.album, s.pay ? '是' : '否'])
    );
    return;
  }

  const data = await api(
    `/api/kugou_music?msg=${encodeURIComponent(keyword)}&num=${limit}`,
    { key, verbose: flags.verbose }
  );
  const list = data?.list || [];
  if (flags.json) return console.log(JSON.stringify(data, null, 2));
  if (!list.length) return console.log('没有搜到结果');
  console.log(bold(`酷狗「${keyword}」共 ${data?.total ?? list.length} 条，显示前 ${list.length} 条`));
  printTable(
    ['#', 'id', '歌名', '歌手', '专辑', '时长'],
    list.map((s, i) => [String(s.n ?? i + 1), String(s.id ?? ''), s.name, s.singer, s.album, formatDuration(s.duration)])
  );
}

async function cmdUrl(positional, flags) {
  const platform = platformOf(positional[0]);
  const id = String(positional[1] || '').trim();
  if (!platform) fail('用法：node chksz.mjs url <wy|tx|kg> <id|mid|hash> [--level|--size] [--probe]');
  if (!id) fail('缺少歌曲 ID');
  const key = requireKey(flags);

  let path;
  if (platform === 'wy') {
    const level = normalizeNeteaseLevel(flags.level || flags.size);
    path = `/api/163_music?id=${encodeURIComponent(id)}&level=${level}&type=json`;
  } else if (platform === 'tx') {
    const size = normalizeSize(flags.size || flags.level);
    path = `/api/qq_music?mid=${encodeURIComponent(id)}&size=${size}&type=json`;
  } else {
    const size = normalizeSize(flags.size || flags.level);
    path = `/api/kugou_music?id=${encodeURIComponent(id)}&size=${size}&type=json`;
  }

  const data = await api(path, { key, verbose: flags.verbose });
  if (flags.json) {
    const probe = flags.probe && data?.url ? await probeAudio(data.url, { verbose: flags.verbose }) : undefined;
    return console.log(JSON.stringify({ ...data, probe }, null, 2));
  }

  console.log(bold(data?.name || '(无标题)') + (data?.artist ? dim(' · ' + data.artist) : ''));
  if (data?.album) console.log(dim(`专辑：${data.album}`));
  if (data?.format || data?.level) console.log(dim(`音质：${data.level || data.format}${data.br ? `，${Math.round(data.br / 1000)}kbps` : ''}`));
  if (data?.size) console.log(dim(`体积：${formatBytes(data.size)}`));
  if (data?.interval) console.log(dim(`时长：${formatDuration(Number(data.interval) * 1000)}`));
  console.log('');
  if (!data?.url) return console.log(red('接口没有返回 url'));
  console.log(data.url);

  if (flags.probe) {
    const probe = await probeAudio(data.url, { verbose: flags.verbose });
    console.log(probe.ok ? green(`✓ 地址可播放（${probe.summary}）`) : red(`× 地址不可用（${probe.summary}）`));
  }
}

async function cmdLyric(positional, flags) {
  const platform = platformOf(positional[0]);
  const id = String(positional[1] || '').trim();
  if (!platform) fail('用法：node chksz.mjs lyric <wy|tx|kg> <id> [--out 文件.lrc]');
  if (!id) fail('缺少歌曲 ID');
  const key = requireKey(flags);

  let lrc = '';
  let extra = {};
  if (platform === 'wy') {
    const data = await api(`/api/163_lyric?id=${encodeURIComponent(id)}`, { key, verbose: flags.verbose });
    lrc = data?.lrc || '';
    extra = { translation: data?.tlyric || '', romanization: data?.romalrc || '' };
  } else if (platform === 'tx') {
    const data = await api(`/api/qq_music?mid=${encodeURIComponent(id)}&type=json`, { key, verbose: flags.verbose });
    lrc = data?.lrc || data?.lyric || '';
    extra = { title: data?.name, artist: data?.singer };
  } else {
    const data = await api(`/api/kugou_music?id=${encodeURIComponent(id)}&type=json`, { key, verbose: flags.verbose });
    lrc = data?.lrc || data?.lyric || '';
    extra = { title: data?.name, artist: data?.singer };
  }

  if (!lrc) return console.log(red('没有拿到歌词（可能是纯音乐或未收录）'));

  if (flags.json) return console.log(JSON.stringify({ lrc, ...extra }, null, 2));

  const out = typeof flags.out === 'string' ? resolve(flags.out) : '';
  if (out) {
    mkdirSync(dirname(out), { recursive: true });
    const text = extra.translation ? `${lrc}\n${extra.translation}` : lrc;
    writeFileSync(out, text, 'utf8');
    console.log(green(`✓ 已写入 ${out}（${text.split('\n').length} 行）`));
    if (extra.translation) console.log(dim('已把翻译歌词追加在末尾'));
    return;
  }

  console.log(lrc);
  if (extra.translation) {
    console.log(dim('\n--- 翻译歌词 ---'));
    console.log(extra.translation);
  }
}

async function cmdPlaylist(positional, flags) {
  const id = String(positional[0] || '').trim();
  if (!id) fail('用法：node chksz.mjs playlist <歌单ID> [--limit 20]');
  const key = requireKey(flags);
  const data = await api(`/api/163_playlist?id=${encodeURIComponent(id)}`, { key, verbose: flags.verbose });

  if (flags.json) return console.log(JSON.stringify(data, null, 2));

  const limit = Math.max(1, Number(flags.limit || 20));
  console.log(bold(data?.name || `歌单 ${id}`));
  if (data?.creator) console.log(dim(`创建者：${data.creator}`));
  if (data?.trackCount) console.log(dim(`曲目数：${data.trackCount}`));
  if (data?.coverImgUrl) console.log(dim(`封面：${data.coverImgUrl}`));
  console.log('');

  // 歌单返回的字段名各版本不完全一致，这里按常见几种兜一下
  const songs = data?.songs || data?.tracks || data?.data?.songs || [];
  if (!songs.length) return console.log(dim('（接口没有返回歌曲列表）'));
  printTable(
    ['#', 'id', '歌名', '歌手', '专辑'],
    songs.slice(0, limit).map((s, i) => [
      String(i + 1),
      String(s.id ?? ''),
      s.name || s.title || '',
      s.artists || s.ar?.map?.((a) => a.name).join('/') || s.singer || '',
      s.album || s.al?.name || '',
    ])
  );
  if (songs.length > limit) console.log(dim(`…还有 ${songs.length - limit} 首（--limit 可调）`));
}

async function cmdDownload(positional, flags) {
  const platform = platformOf(positional[0]);
  const id = String(positional[1] || '').trim();
  if (!platform) fail('用法：node chksz.mjs download <wy|tx|kg> <id> [--out ./music] [--size flac]');
  if (!id) fail('缺少歌曲 ID');
  const key = requireKey(flags);

  const outDir = resolve(typeof flags.out === 'string' ? flags.out : './music');
  mkdirSync(outDir, { recursive: true });

  let detailPath;
  if (platform === 'wy') {
    detailPath = `/api/163_music?id=${encodeURIComponent(id)}&level=${normalizeNeteaseLevel(flags.level || flags.size)}&type=json`;
  } else if (platform === 'tx') {
    detailPath = `/api/qq_music?mid=${encodeURIComponent(id)}&size=${normalizeSize(flags.size || flags.level)}&type=json`;
  } else {
    detailPath = `/api/kugou_music?id=${encodeURIComponent(id)}&size=${normalizeSize(flags.size || flags.level)}&type=json`;
  }

  const data = await api(detailPath, { key, verbose: flags.verbose });
  if (!data?.url) fail('接口没有返回直链');

  const ext = guessExtension(data);
  const baseName = sanitizeFileName(`${data.name || id} - ${data.artist || '未知歌手'}`) || id;
  const filePath = join(outDir, `${baseName}${ext}`);

  console.log(dim(`下载：${data.name || id}${data.artist ? ' · ' + data.artist : ''}`));
  console.log(dim(`音质：${data.level || data.format || '-'}${data.br ? `，${Math.round(data.br / 1000)}kbps` : ''}${data.size ? '，' + formatBytes(data.size) : ''}`));

  const res = await fetch(data.url, { signal: AbortSignal.timeout(120_000) });
  if (!res.ok || !res.body) fail(`下载失败：HTTP ${res.status}`);

  let received = 0;
  const total = Number(data.size) || Number(res.headers.get('content-length')) || 0;
  let lastPrint = 0;
  const stream = Readable.fromWeb(res.body);
  stream.on('data', (chunk) => {
    received += chunk.length;
    const now = Date.now();
    if (now - lastPrint > 500 && process.stderr.isTTY) {
      lastPrint = now;
      const percent = total ? ` ${((received / total) * 100).toFixed(0)}%` : '';
      process.stderr.write(`\r  已下载 ${formatBytes(received)}${percent}   `);
    }
  });

  await pipeline(stream, createWriteStream(filePath));
  if (process.stderr.isTTY) process.stderr.write('\r');
  console.log(green(`✓ 已保存 ${filePath}（${formatBytes(received)}）`));
}

function guessExtension(data) {
  const format = String(data?.format || '').toLowerCase();
  if (format.includes('flac')) return '.flac';
  if (format.includes('mp3')) return '.mp3';
  if (format.includes('m4a') || format.includes('aac')) return '.m4a';
  if (format.includes('ogg')) return '.ogg';
  const url = String(data?.url || '');
  const match = url.match(/\.(flac|mp3|m4a|aac|ogg|wav)(?:\?|$)/i);
  if (match) return '.' + match[1].toLowerCase();
  return '.audio';
}

function sanitizeFileName(name) {
  return String(name)
    .replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 120);
}

/** 三个平台各打一枪，用最少的额度看清"现在到底谁能用" */
async function cmdProbe(flags) {
  const key = requireKey(flags);
  console.log(bold('ChKSz 接口连通性检测') + dim('（每个平台 1 次请求，共 3 次额度）'));
  console.log('');

  const targets = [
    { name: '网易云搜索', run: () => api('/api/163_search?keyword=%E5%91%A8%E6%9D%B0%E4%BC%A6&limit=1&offset=0', { key, verbose: flags.verbose, quiet: true }) },
    { name: 'QQ 音乐搜索', run: () => api(`/api/qq_music?msg=${encodeURIComponent('周杰伦')}&num=1`, { key, verbose: flags.verbose, quiet: true }) },
    { name: '酷狗搜索', run: () => api(`/api/kugou_music?msg=${encodeURIComponent('周杰伦')}&num=1`, { key, verbose: flags.verbose, quiet: true }) },
  ];

  const rows = [];
  for (const target of targets) {
    const started = Date.now();
    try {
      await target.run();
      rows.push([target.name, green('可用'), `${Date.now() - started}ms`, '']);
    } catch (err) {
      const status = err instanceof ApiError ? `HTTP ${err.status}` : '网络';
      rows.push([target.name, red('失败'), `${Date.now() - started}ms`, `${status} ${err.message}`]);
    }
  }
  printTable(['接口', '结果', '耗时', '说明'], rows);
  console.log('');
  console.log(dim('提示：402 是额度用尽，429 是每分钟超过 20 次，503 是上游熔断（不扣额度，稍后再试）。'));
}

/** 把模板里的占位符换成真实 Key，产出可导入 App 的脚本 */
async function cmdBuild(flags) {
  const key = requireKey(flags);
  const template = readFileSync(TEMPLATE, 'utf8');
  const placeholder = '__CHKSZ_API_KEY__';
  if (!template.includes(placeholder)) {
    fail(`${TEMPLATE} 里没有找到 ${placeholder} 占位符，模板可能被改坏了`);
  }
  const output = typeof flags.out === 'string' ? resolve(flags.out) : join(DIST_DIR, 'chksz-source.local.js');
  mkdirSync(dirname(output), { recursive: true });
  // 全量替换：注释里也提到过占位符，只换第一处会漏掉真正的声明（踩过一次）
  const content = template.split(placeholder).join(key);
  writeFileSync(output, content, 'utf8');

  // 生成后自检：占位符必须彻底消失，API_KEY 那一行必须真的带上 Key
  const written = readFileSync(output, 'utf8');
  if (written.includes(placeholder)) fail('生成结果里仍有占位符残留，请检查模板');
  const keyLine = written.split('\n').find((line) => /^(var|const|let)\s+API_KEY/.test(line));
  if (!keyLine || !keyLine.includes(key)) fail('生成结果里的 API_KEY 没有被正确注入');

  console.log(green(`✓ 已生成 ${output}`));
  console.log(dim(`  文件大小 ${(written.length / 1024).toFixed(1)}KB，Key 长度 ${key.length}`));
  console.log('');
  console.log(bold('导入到 App：'));
  console.log('  1. 打开「插件管理」→ 导入 → 选择文件');
  console.log(`  2. 选中 ${output}`);
  console.log('  3. 导入后点「检测」，看到「网易云」正常就说明 Key 生效了');
  console.log('');
  console.log(yellow('注意：这个文件里带着明文 Key，别提交到 Git、别发到群里。'));
}

// ==================== 入口 ====================

const USAGE = `${bold('ChKSz API 命令行工具')}

  node chksz.mjs build                               生成可导入 App 的音源脚本（注入 Key）
  node chksz.mjs probe                               三个平台各发一次请求，看谁能用
  node chksz.mjs search <wy|tx|kg> <关键词> [--limit 10]
  node chksz.mjs url <wy|tx|kg> <id> [--level 音质] [--probe]
  node chksz.mjs lyric <wy|tx|kg> <id> [--out 文件.lrc]
  node chksz.mjs playlist <歌单ID> [--limit 20]
  node chksz.mjs download <wy|tx|kg> <id> [--out ./music] [--size flac]

  通用参数：
    --key <key>    直接指定 API Key（也可用环境变量 CHKSZ_API_KEY 或 chksz.key 文件）
    --json         输出原始 JSON
    --verbose/-v   打印额度、重试、IP 选择等细节

  音质：网易云 standard/exhigh/lossless/hires（也可写 128k/320k/flac/flac24bit）
        QQ 与酷狗 128k/320k/flac/hires/master`;

async function main() {
  const { positional, flags } = parseArgs(process.argv.slice(2));
  const command = positional.shift();

  if (!command || flags.help) {
    console.log(USAGE);
    return;
  }

  switch (command) {
    case 'build':
      return await cmdBuild(flags);
    case 'probe':
      return await cmdProbe(flags);
    case 'search':
      return await cmdSearch(positional, flags);
    case 'url':
      return await cmdUrl(positional, flags);
    case 'lyric':
      return await cmdLyric(positional, flags);
    case 'playlist':
      return await cmdPlaylist(positional, flags);
    case 'download':
      return await cmdDownload(positional, flags);
    default:
      fail(`未知命令：${command}\n\n${USAGE}`);
  }
}

main().catch((err) => {
  // fail() 已经打印过报错并设好 exitCode，这里只需要安静退出
  if (err instanceof CliExit) return;
  if (err instanceof ApiError) {
    console.error(red(`× ${err.message}`));
    if (err.status === 401) console.error(dim('  到 https://api.chksz.com/login 登录后重新复制 Key'));
    if (err.status === 402) console.error(dim('  免费额度每日 50 次（UTC+8 重置），1 LDC 可兑换 10 次'));
  } else {
    console.error(red(`× ${err.message}`));
  }
  if (process.env.CHKSZ_DEBUG) console.error(err);
  // 用 exitCode 而不是 process.exit()：强杀会带着未关闭的请求句柄触发 libuv 断言
  process.exitCode = 1;
});
