#!/usr/bin/env node
/**
 * 音源脚本沙箱测试器
 *
 * 作用：**不用装 App**，在电脑上把 `chksz-source.js` 按 App 的沙箱规则跑一遍，
 * 验证「脚本能加载 → 能声明音源 → 能解析直链 → 能取歌词」。
 *
 * 为什么要单独写一个：App 里跑的是 QuickJS + user-api-preload.js 这套精简环境，
 * 和 Node 的差异恰恰是最容易翻车的地方（没有 fetch、body 有时是对象有时是字符串、
 * 宿主只从返回值里认 url/lyric）。这些差异在这里被如实模拟，脚本一旦不兼容会立刻报出来。
 *
 * 用法：
 *   node sandbox-test.mjs                          # 用 dist/chksz-source.local.js 跑内置用例
 *   node sandbox-test.mjs --script ./x.js          # 指定脚本
 *   node sandbox-test.mjs --id 2652820720 --quality flac
 *   node sandbox-test.mjs --source tx --id 004Z8Ihr0JIu5s
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import { resilientFetch } from './chksz-http.mjs';

const ROOT = dirname(fileURLToPath(import.meta.url));

function parseArgs(argv) {
  const flags = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith('--')) {
      const next = argv[i + 1];
      if (next === undefined || next.startsWith('--')) flags[a.slice(2)] = true;
      else {
        flags[a.slice(2)] = next;
        i++;
      }
    }
  }
  return flags;
}

const flags = parseArgs(process.argv.slice(2));
const scriptPath = resolve(
  typeof flags.script === 'string' ? flags.script : join(ROOT, 'dist', 'chksz-source.local.js')
);

if (!existsSync(scriptPath)) {
  console.error(`× 找不到脚本：${scriptPath}`);
  console.error('  先跑一次：node chksz.mjs build');
  process.exit(1);
}

const code = readFileSync(scriptPath, 'utf8');

// ==================== 模拟 App 的 lx 沙箱 ====================

/** 与 user-api-preload.js 一致：body 是 JSON 对象时给对象，是数组/纯文本时给字符串 */
function parseBody(text) {
  try {
    const parsed = JSON.parse(text);
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed;
  } catch {
    /* 非 JSON，按纯文本给 */
  }
  return text;
}

const state = {
  inited: null,
  requestHandler: null,
  httpLog: [],
};

const EVENT_NAMES = { request: 'request', inited: 'inited', updateAlert: 'updateAlert' };

const lx = {
  EVENT_NAMES,
  version: '2.0.0',
  env: 'mobile',
  currentScriptInfo: { name: 'sandbox-test', description: '', version: '0.0.0', author: '', homepage: '', rawScript: '' },
  utils: {
    // 沙箱里没有 crypto 模块，脚本也不该依赖它；这里只提供协议要求的空壳
    crypto: { md5: () => '' },
    buffer: { from: (v) => v, bufToString: (v) => String(v) },
  },
  on(eventName, handler) {
    if (eventName === EVENT_NAMES.request) {
      state.requestHandler = handler;
      return Promise.resolve();
    }
    return Promise.reject(new Error('The event is not supported: ' + eventName));
  },
  send(eventName, data) {
    if (eventName === EVENT_NAMES.inited) {
      state.inited = data;
      return Promise.resolve();
    }
    return Promise.resolve();
  },
  /** 与 preload 的 lx.request 签名一致：(url, options, callback(err, resp, body)) */
  request(url, options, callback) {
    let aborted = false;
    const started = Date.now();
    // 用带 IP 兜底的请求器：这台机器到 Cloudflare 的连接时好时坏，
    // 直接 fetch 会把"网络抽风"伪装成"脚本写错了"
    resilientFetch(url, {
      method: String(options?.method || 'get').toUpperCase(),
      headers: options?.headers || {},
      timeoutMs: options?.timeout || 15000,
    })
      .then((res) => {
        const body = parseBody(res.text);
        state.httpLog.push({ url, status: res.status, ms: Date.now() - started });
        if (aborted) return;
        callback(null, { statusCode: res.status, headers: res.headers, body }, body);
      })
      .catch((err) => {
        state.httpLog.push({ url, status: 0, ms: Date.now() - started, error: err.message });
        if (aborted) return;
        callback(err, { statusCode: 0, headers: {}, body: null }, null);
      });
    return () => {
      aborted = true;
    };
  },
};

const context = vm.createContext({
  lx,
  console: {
    log: (...args) => console.log('  [script]', ...args),
    info: (...args) => console.log('  [script]', ...args),
    warn: (...args) => console.log('  [script:warn]', ...args),
    error: (...args) => console.log('  [script:error]', ...args),
  },
  setTimeout,
  clearTimeout,
  setInterval,
  clearInterval,
  fetch: undefined, // 沙箱里没有 fetch，脚本用了就会立刻暴露
  URL: undefined,
  URLSearchParams: undefined,
});

console.log(`脚本：${scriptPath}`);
console.log(`大小：${(code.length / 1024).toFixed(1)}KB`);

try {
  vm.runInContext(code, context, { filename: scriptPath, timeout: 10000 });
} catch (err) {
  console.error(`× 脚本加载失败：${err.message}`);
  process.exit(1);
}

console.log('✓ 脚本加载成功');

if (!state.inited) {
  console.error('× 脚本没有调用 lx.send(inited)，App 里会显示"未声明音源"');
  process.exit(1);
}

const sources = state.inited.sources || {};
const declared = Object.entries(sources).map(
  ([k, v]) => `${k}(${v.type}, actions=${(v.actions || []).join('/')}, qualitys=${(v.qualitys || []).join('/')})`
);
console.log(`✓ 声明音源：${declared.join('，') || '（空）'}`);

if (!state.requestHandler) {
  console.error('× 脚本没有注册 lx.on(request)');
  process.exit(1);
}
console.log('✓ 已注册 request 事件');
console.log('');

// ==================== 跑真实用例 ====================

function buildEvent({ source, action, quality, musicInfo }) {
  return {
    source,
    action,
    info: {
      type: 'music',
      source,
      quality,
      musicInfo,
    },
  };
}

/** 与 UserApiEngine 传进来的字段保持一致：同一个 id 会同时出现在 id/songmid/hash 上 */
function musicInfoOf(id, extra = {}) {
  return {
    id,
    songmid: id,
    hash: id,
    copyrightId: id,
    title: extra.title || '',
    name: extra.title || '',
    artist: extra.artist || '',
    singer: extra.artist || '',
    album: extra.album || '',
    platform: extra.platform || '',
    source: extra.platform || '',
    types: [{ type: '128k' }, { type: '320k' }, { type: 'flac' }, { type: 'flac24bit' }],
  };
}

async function runCase(label, event) {
  console.log(`—— ${label} ——`);
  const started = Date.now();
  try {
    const result = await state.requestHandler(event);
    const ms = Date.now() - started;
    if (typeof result === 'string') {
      console.log(`  ✓ ${ms}ms 返回字符串（${result.length} 字符）`);
      console.log('  ' + result.split('\n').slice(0, 4).join('\n  '));
      return result;
    }
    if (result && typeof result === 'object') {
      if (result.url) {
        console.log(`  ✓ ${ms}ms 拿到直链：`);
        console.log('  ' + result.url.slice(0, 160) + (result.url.length > 160 ? '…' : ''));
        const probe = await probeUrl(result.url);
        console.log(`  ${probe.ok ? '✓' : '×'} 直链探测：${probe.summary}`);
        return result;
      }
      const lrc = result.rawLrc || result.lrc || result.lyric;
      if (lrc) {
        console.log(`  ✓ ${ms}ms 拿到歌词（${String(lrc).split('\n').length} 行）`);
        console.log('  ' + String(lrc).split('\n').slice(0, 3).join('\n  '));
        return result;
      }
      console.log(`  ? ${ms}ms 返回了不认识的结构：${JSON.stringify(result).slice(0, 200)}`);
      return result;
    }
    console.log(`  ? ${ms}ms 返回 ${typeof result}`);
    return result;
  } catch (err) {
    console.log(`  × ${Date.now() - started}ms 失败：${err.message}`);
    return null;
  }
}

async function probeUrl(url) {
  try {
    const res = await fetch(url, { method: 'HEAD', signal: AbortSignal.timeout(15000), redirect: 'follow' });
    const type = res.headers.get('content-type') || '';
    const length = res.headers.get('content-length') || '';
    const ok = (res.status === 200 || res.status === 206) && /audio|octet-stream|mpeg/i.test(type);
    return { ok, summary: `HTTP ${res.status}，${type || '未知类型'}${length ? '，' + Math.round(Number(length) / 1024) + 'KB' : ''}` };
  } catch (err) {
    return { ok: false, summary: `无法连接（${err.message}）` };
  }
}

const source = typeof flags.source === 'string' ? flags.source : 'wy';
const quality = typeof flags.quality === 'string' ? flags.quality : 'flac';
const id = typeof flags.id === 'string' ? flags.id : source === 'wy' ? '2652820720' : '';
const title = typeof flags.title === 'string' ? flags.title : '';
const artist = typeof flags.artist === 'string' ? flags.artist : '';

if (!id) {
  console.error('× 该平台需要 --id 参数（QQ 传 songmid，酷狗传 FileHash）');
  process.exit(1);
}

const info = musicInfoOf(id, { title, artist, platform: source });

await runCase(`${source} musicUrl（${quality}）`, buildEvent({ source, action: 'musicUrl', quality, musicInfo: info }));
await runCase(`${source} lyric`, buildEvent({ source, action: 'lyric', quality, musicInfo: info }));

console.log('');
console.log('—— HTTP 请求记录 ——');
for (const entry of state.httpLog) {
  // 日志里不打印 apikey，避免把密钥落进终端历史
  const safeUrl = entry.url.replace(/apikey=[^&]*/, 'apikey=***');
  console.log(`  ${String(entry.status).padEnd(4)} ${String(entry.ms).padStart(5)}ms  ${safeUrl}`);
}
