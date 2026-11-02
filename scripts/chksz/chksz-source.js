/*!
 * @name ChKSz 聚合音源
 * @version 1.4.0
 * @description 通过 ChKSz API 解析网易云 / QQ / 酷狗的播放直链与歌词
 * @author Melos
 * @homepage https://api.chksz.com
 */

/**
 * ChKSz 音源插件（LX Music 自定义源，协议文档：https://lxmusic.toside.cn/mobile/custom-source）
 *
 * 这份文件是**模板**：`API_KEY` 的占位符由 `node chksz.mjs build` 替换成真实 Key，
 * 产物落在 `dist/chksz-source.local.js`，导入 App 的是那个文件。
 * 之所以不把 Key 直接写在这里：本仓库是 Git 仓库，密钥进了版本历史就再也拿不出来了。
 *
 * ## 协议要点（严格照官方文档实现）
 *  - 非本地源的 actions 固定为 ['musicUrl']；lyric 虽然不在声明里，
 *    但部分宿主（如本仓库的 Melos App）仍会以 action='lyric' 调用脚本，所以做了支持；
 *  - musicUrl 的 info 是 {type, musicInfo}：音质取 info.type（官方），
 *    兼容 info.quality（Melos App 的字段名），两者取其一即可；
 *  - lyric 的 info 是 {musicInfo}，返回值须为 {lyric, tlyric, rlyric, lxlyric} 结构
 *    （tlyric=翻译，rlyric=罗马音，lxlyric=逐字），Melos App 从返回对象里读 lyric 字段，天然兼容；
 *  - 沙箱里只有 setTimeout / clearTimeout 和 lx.* 可用，没有 fetch / URL / console.group 等；
 *  - 单次请求超时 12 秒：官方宿主没有明确的等待上限，但 Melos App 只等 15 秒（musicUrl）/
 *    10 秒（lyric），12 秒 + 0.5 秒定时器余量已贴近上限，且**不本地重试**——
 *    失败如实上抛，重试交给宿主的换源策略。
 *
 * ## 解析策略（薄客户端：每个动作最多两次请求）
 *  1. 按请求音质解析一次；拿到 url 就结束（正常路径只耗 1 次额度）；
 *  2. HTTP 200 但无 url（该音质无版权，如 QQ 付费歌只有 128k 试听）→ 降一档音质再试一次；
 *  3. 404（此源未收录）→ 按"歌名+歌手"取第 1 条（只兜一次）。
 *  其余错误（5xx/401/402/429/超时）立即如实上抛：每次失败重试都在烧每日额度
 *  （免费 50 次/天），上游熔断时换参数再打结果不会变，只会白烧。
 *
 * ## 平台 ID 语义（决定了接口怎么拼）
 *  - 网易云：musicInfo.id 是数字歌曲 id → /api/163_music?id=
 *  - QQ：musicInfo.songmid → /api/qq_music?mid=
 *  - 酷狗：musicInfo.hash（FileHash）→ /api/kugou_music?id=
 *    （ChKSz 文档写酷狗 id 是"搜索结果中的歌曲 ID"，与 FileHash 是否同义取决于上游，
 *     404 时有歌名兜底，所以不怕对不上）
 */

'use strict';

const { EVENT_NAMES, request, on, send } = globalThis.lx;

// ==================== 配置 ====================

const API_BASE = 'https://api.chksz.com';

/** 个人 API Key（由 build 注入；宿主可预先设置 globalThis.CHKSZ_API_KEY 覆盖） */
const API_KEY = '__CHKSZ_API_KEY__';

/** 单次请求超时（毫秒）：不本地重试，见文件头 */
const REQUEST_TIMEOUT_MS = 12000;

/** 直链缓存：直链带签名会过期，8 分钟覆盖"切歌重试"场景足够 */
const URL_TTL_MS = 8 * 60 * 1000;

/** 歌词缓存：歌词不会变，缓存久一点省额度 */
const LRC_TTL_MS = 60 * 60 * 1000;

/** 404 时是否退回"按歌名+歌手取第 1 条"。播错版本好过播不了；不要就改 false */
const ALLOW_SEARCH_FALLBACK = true;

// ==================== 音质映射 ====================

/** LX 音质 → 网易云 level（一一对应，不升档） */
const NETEASE_LEVEL = {
  '128k': 'standard',
  '320k': 'exhigh',
  flac: 'lossless',
  flac24bit: 'hires',
};

/** level → 降一档（无 url 时用；null = 没得降） */
const NETEASE_LOWER = {
  standard: null,
  exhigh: 'standard',
  lossless: 'exhigh',
  hires: 'lossless',
};

/** LX 音质 → QQ/酷狗 size（音乐源原生值，服务端不做别名/降级，文档 7、8 节） */
const QQKG_SIZE = {
  '128k': '128k',
  '320k': '320k',
  flac: 'flac',
  flac24bit: 'hires',
};

const QQKG_LOWER = {
  '128k': null,
  '320k': '128k',
  flac: '320k',
  hires: 'flac',
};

// ==================== 通用工具 ====================

/** 简易 TTL 缓存 */
const cache = {};

function cacheGet(key) {
  const hit = cache[key];
  if (!hit) return null;
  if (hit.expireAt < Date.now()) {
    delete cache[key];
    return null;
  }
  return hit.value;
}

function cacheSet(key, value, ttl) {
  cache[key] = { value, expireAt: Date.now() + ttl };
  return value;
}

/** 服务端返回 JSON 对象时 body 已是对象；数组/纯文本时是字符串，统一成对象 */
function parseJson(body) {
  if (body === null || body === undefined) return null;
  if (typeof body === 'object') return body;
  if (typeof body !== 'string') return null;
  const text = body.trim();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (e) {
    return null;
  }
}

/** 取响应头（大小写不确定，三档都查） */
function header(headers, name) {
  if (!headers) return '';
  for (const key of [name.toLowerCase(), name, name.toUpperCase()]) {
    if (headers[key] !== undefined && headers[key] !== null) return String(headers[key]);
  }
  return '';
}

/** HTTP 状态 → 中文错误（文档第 9 节），err.status 供后续判断 */
function apiError(status, env, headers) {
  const msg = (env && (env.msg || env.message)) || '';
  let text;
  switch (status) {
    case 400: text = '请求参数有误' + (msg ? '：' + msg : ''); break;
    case 401: text = 'API Key 无效或未登录，请到 api.chksz.com 重新获取'; break;
    case 402: text = '免费与付费额度均已用尽，等次日（UTC+8）重置或兑换额度'; break;
    case 403: text = '用户、Key 或 IP 被封禁' + (msg ? '：' + msg : ''); break;
    case 404: text = msg || '接口路径或资源不存在'; break;
    case 429: {
      const wait = header(headers, 'retry-after');
      text = '触发限流（每 Key 每分钟 20 次）' + (wait ? '，需等待 ' + wait + ' 秒' : '');
      break;
    }
    case 502: text = '上游返回 502' + (msg ? '：' + msg : ''); break;
    case 503: text = '上游暂时不可用' + (msg ? '：' + msg : ''); break;
    default: text = 'HTTP ' + status + (msg ? '：' + msg : '');
  }
  const err = new Error(text);
  err.status = status;
  return err;
}

/**
 * 发一次 GET 并解包：成功 resolve 业务 payload（{code,msg,data} 外壳剥掉），
 * 失败 reject 带 status 的中文错误（HTTP 4xx/5xx，或 HTTP 200 但 body code 非 200，文档第 10 节第 6 条）。
 */
function getJson(path) {
  const url =
    API_BASE + path + (path.includes('?') ? '&' : '?') + 'apikey=' + encodeURIComponent(API_KEY);
  return new Promise((resolve, reject) => {
    let done = false;
    const timer = setTimeout(() => {
      if (done) return;
      done = true;
      reject(new Error('请求超时（' + REQUEST_TIMEOUT_MS / 1000 + ' 秒），请稍后重试'));
    }, REQUEST_TIMEOUT_MS + 500);

    request(
      url,
      {
        method: 'get',
        timeout: REQUEST_TIMEOUT_MS,
        headers: { Accept: 'application/json, text/plain, */*' },
      },
      (err, resp) => {
        if (done) return;
        done = true;
        clearTimeout(timer);
        if (err) {
          reject(new Error('网络请求失败：' + (err.message || String(err))));
          return;
        }
        const status = (resp && resp.statusCode) || 0;
        const headers = (resp && resp.headers) || {};
        const env = parseJson(resp && resp.body);
        const free = header(headers, 'x-quota-free-remaining');
        if (free) console.log('[ChKSz] 今日免费额度剩余 ' + free + ' 次');
        if (status < 200 || status >= 300) {
          reject(apiError(status, env, headers));
          return;
        }
        if (env && typeof env === 'object' && typeof env.code === 'number' && env.code !== 200) {
          reject(apiError(env.code, env, headers));
          return;
        }
        resolve(env && typeof env === 'object' && env.data !== undefined ? env.data : env);
      }
    );
  });
}

// ==================== 取链核心 ====================

/** musicInfo 里的 ID：不同平台字段名不同，宿主会把同一个值塞进多个字段 */
function pickId(info) {
  if (!info) return '';
  for (const field of ['id', 'songmid', 'mid', 'hash', 'copyrightId', 'rid']) {
    const raw = info[field];
    if (raw === null || raw === undefined) continue;
    const s = String(raw).trim();
    if (s && s !== '0' && s !== 'undefined' && s !== 'null') return s;
  }
  return '';
}

/** "歌名 歌手"：宿主同时塞了 title/name、artist/singer 两套字段 */
function pickKeyword(info) {
  if (!info) return '';
  let title = String(info.title || info.name || '').trim();
  let artist = info.artist || info.singer || '';
  if (typeof artist === 'object' && artist && typeof artist.map === 'function') {
    artist = artist.map((x) => (x && (x.name || x.title)) || '').join('/');
  }
  return (title + ' ' + String(artist).trim()).trim();
}

/**
 * 完整取链流程（网易云/QQ/酷狗共用，见文件头"解析策略"）：
 *  ① 主参数 @ 请求音质 —— 正常情况到这就结束
 *  ② ①返回 404 → 按歌名取第 1 条（只兜一次）
 *  ③ ①②HTTP 200 但无 url → 同参数 @ 降一档（只降一次）
 */
function resolveSongUrl({ primary, keyword, buildPath, requested, lower, cacheKey, label }) {
  const hit = cacheGet(cacheKey);
  if (hit) return Promise.resolve(hit);

  if (!primary && !keyword) {
    return Promise.reject(new Error(label + '：缺少歌曲 ID，且没有歌名可供回查'));
  }

  /** 打一次接口，resolve url 或 null（= HTTP 200 但无 url） */
  const fetchOnce = (param, size) =>
    getJson(buildPath(param, size)).then((data) => (data && data.url) || null);

  /** ③ 无 url 时降一档再试；没得降或仍无 → 如实报错 */
  const withQualityFallback = (url, param) => {
    if (url) return url;
    if (!lower) {
      throw new Error(label + '：该歌曲没有可用直链（VIP/版权受限或未收录）');
    }
    console.log('[ChKSz] ' + label + '：' + requested + ' 无直链，降档到 ' + lower + ' 重试');
    return fetchOnce(param, lower).then((url2) => {
      if (url2) return url2;
      throw new Error(label + '：' + requested + ' 与 ' + lower + ' 均无可用直链（VIP/版权受限）');
    });
  };

  /** ② 按歌名取第 1 条 */
  const byKeyword = () => {
    console.log('[ChKSz] ' + label + '：ID 未收录，按歌名「' + keyword + '」回查');
    return fetchOnce(keyword, requested)
      .then((url) => withQualityFallback(url, keyword))
      .catch((err) => {
        if (err && err.status === 404) {
          throw new Error(label + '：ID 与歌名均未在此音源找到该歌曲');
        }
        throw err;
      });
  };

  // ① 主参数；没有主参数（只有网易云会出现）直接走 ②
  const step1 = primary
    ? fetchOnce(primary, requested)
        .then((url) => withQualityFallback(url, primary))
        .catch((err) => (err && err.status === 404 && ALLOW_SEARCH_FALLBACK && keyword ? byKeyword() : Promise.reject(err)))
    : ALLOW_SEARCH_FALLBACK && keyword
      ? byKeyword()
      : Promise.reject(new Error(label + '：缺少歌曲 ID'));

  return step1.then((url) => cacheSet(cacheKey, { url }, URL_TTL_MS));
}

// ==================== 三个平台的取链实现 ====================

/**
 * 网易云取链。歌名兜底需要三步（搜索回查 id → 按 id 取链），与通用流程不同，单独实现：
 *  ① 数字 id @ 请求音质 → ② 无 url 时降一档 → ③ 仍无 → 404/无 id 时按歌名搜索回查再取链
 */
function musicUrlNetease(musicInfo, quality) {
  const level = NETEASE_LEVEL[quality];
  const lower = NETEASE_LOWER[level];
  const rawId = pickId(musicInfo);
  const keyword = pickKeyword(musicInfo);
  const numericId = /^\d+$/.test(rawId) ? rawId : '';
  const cacheKey = 'wy:' + (numericId || keyword) + ':' + quality;

  const hit = cacheGet(cacheKey);
  if (hit) return Promise.resolve(hit);

  const fetchById = (id, size) =>
    getJson('/api/163_music?id=' + encodeURIComponent(id) + '&level=' + size + '&type=json').then(
      (data) => (data && data.url) || null
    );

  /** 无 url 时降一档再试；没得降或仍无 → 抛"无可用直链" */
  const withFallback = (urlPromise, param, size) =>
    urlPromise.then((url) => {
      if (url) return url;
      if (!lower) {
        const e = new Error('网易云：该歌曲没有可用直链（VIP/版权受限或未收录）');
        e.noUrl = true;
        throw e;
      }
      console.log('[ChKSz] 网易云：' + size + ' 无直链，降档到 ' + lower + ' 重试');
      return fetchById(param, lower).then((url2) => {
        if (url2) return url2;
        const e = new Error('网易云：' + size + ' 与 ' + lower + ' 均无可用直链（VIP/版权受限）');
        e.noUrl = true;
        throw e;
      });
    });

  // ① 有数字 id：直接取链
  if (numericId) {
    return withFallback(fetchById(numericId, level), numericId, level)
      .then((url) => cacheSet(cacheKey, { url }, URL_TTL_MS))
      .catch((err) => {
        // ③ id 在此源 404（如跨平台换源时拿到别家 id）：按歌名搜索回查一次
        if (err && err.status !== 404) throw err;
        if (!ALLOW_SEARCH_FALLBACK || !keyword) throw err;
        return musicUrlNeteaseByKeyword(keyword, level, cacheKey);
      });
  }

  // 没有 id（跨平台导入等场景）：直接走歌名回查
  if (!ALLOW_SEARCH_FALLBACK || !keyword) {
    return Promise.reject(new Error('网易云：缺少歌曲 ID，且没有歌名可供回查'));
  }
  return musicUrlNeteaseByKeyword(keyword, level, cacheKey);
}

/** 网易云歌名兜底：搜索回查数字 id（1 次）→ 按 id 取链（1 次），取到才入缓存 */
function musicUrlNeteaseByKeyword(keyword, level, cacheKey) {
  console.log('[ChKSz] 网易云：ID 未收录，按歌名「' + keyword + '」回查');
  return getJson('/api/163_search?keyword=' + encodeURIComponent(keyword) + '&limit=1&offset=0')
    .then((data) => {
      const first = data && data.songs && data.songs[0];
      if (!first || !first.id) {
        throw new Error('网易云：按歌名「' + keyword + '」没有找到匹配歌曲');
      }
      console.log('[ChKSz] 网易云：回查到 id=' + first.id + '（' + first.name + '）');
      return getJson(
        '/api/163_music?id=' + encodeURIComponent(first.id) + '&level=' + level + '&type=json'
      );
    })
    .then((data) => {
      const url = (data && data.url) || '';
      if (!url) throw new Error('网易云：该歌曲没有可用直链（VIP/版权受限或未收录）');
      return cacheSet(cacheKey, { url }, URL_TTL_MS);
    });
}

function musicUrlQq(musicInfo, size, lower, quality) {
  const mid = pickId(musicInfo);
  const keyword = pickKeyword(musicInfo);

  return resolveSongUrl({
    primary: mid,
    keyword,
    buildPath: (param, s) =>
      param === keyword
        ? '/api/qq_music?msg=' + encodeURIComponent(keyword) + '&n=1&size=' + s + '&type=json'
        : '/api/qq_music?mid=' + encodeURIComponent(param) + '&size=' + s + '&type=json',
    requested: size,
    lower,
    cacheKey: 'tx:' + (mid || keyword) + ':' + quality,
    label: 'QQ',
  });
}

function musicUrlKugou(musicInfo, size, lower, quality) {
  const hash = pickId(musicInfo);
  const keyword = pickKeyword(musicInfo);

  return resolveSongUrl({
    primary: hash,
    keyword,
    buildPath: (param, s) =>
      param === keyword
        ? '/api/kugou_music?msg=' + encodeURIComponent(keyword) + '&n=1&size=' + s + '&type=json'
        : '/api/kugou_music?id=' + encodeURIComponent(param) + '&size=' + s + '&type=json',
    requested: size,
    lower,
    cacheKey: 'kg:' + (hash || keyword) + ':' + quality,
    label: '酷狗',
  });
}

// ==================== 歌词（官方结构 {lyric, tlyric, rlyric, lxlyric}） ====================

/** 网易云：优先数字 id，否则按歌名回查一次（163_lyric 直接带原文/翻译/罗马音） */
function lyricNetease(musicInfo) {
  const rawId = pickId(musicInfo);
  const keyword = pickKeyword(musicInfo);
  const cacheKey = 'wylrc:' + rawId;
  const hit = cacheGet(cacheKey);
  if (hit) return Promise.resolve(hit);

  const idPromise = /^\d+$/.test(rawId)
    ? Promise.resolve(rawId)
    : keyword
      ? getJson('/api/163_search?keyword=' + encodeURIComponent(keyword) + '&limit=1&offset=0').then((data) => {
          const first = data && data.songs && data.songs[0];
          return first && first.id ? String(first.id) : '';
        })
      : Promise.resolve('');

  return idPromise
    .then((id) => {
      if (!id) throw new Error('网易云：缺少歌曲 ID，无法取歌词');
      return getJson('/api/163_lyric?id=' + encodeURIComponent(id));
    })
    .then((data) => {
      // 官方 lyric 结构：lyric 原文 / tlyric 翻译 / rlyric 罗马音 / lxlyric 逐字
      const lyric = (data && data.lrc) || '';
      const tlyric = (data && data.tlyric) || '';
      const rlyric = (data && data.romalrc) || '';
      if (!lyric && !tlyric) throw new Error('网易云没有返回歌词（纯音乐或未收录）');
      return cacheSet(cacheKey, { lyric, tlyric, rlyric, lxlyric: '' }, LRC_TTL_MS);
    });
}

/** QQ/酷狗：详情接口自带 lrc 字段；404 按歌名兜底一次，其余错误原样上抛 */
function lyricByDetail(musicInfo, label, buildDetailPath, buildSearchPath) {
  const id = pickId(musicInfo);
  const cacheKey = label + 'lrc:' + id;
  const hit = cacheGet(cacheKey);
  if (hit) return Promise.resolve(hit);
  if (!id) return Promise.reject(new Error(label + '：缺少歌曲 ID，无法取歌词'));

  return getJson(buildDetailPath(id))
    .then((data) => {
      const lyric = (data && (data.lrc || data.lyric)) || '';
      if (!lyric) throw new Error(label + '没有返回歌词');
      return cacheSet(cacheKey, { lyric, tlyric: '', rlyric: '', lxlyric: '' }, LRC_TTL_MS);
    })
    .catch((err) => {
      if (!err || err.status !== 404 || !ALLOW_SEARCH_FALLBACK) throw err;
      const keyword = pickKeyword(musicInfo);
      if (!keyword) throw err;
      return getJson(buildSearchPath(keyword)).then((data) => {
        const lyric = (data && (data.lrc || data.lyric)) || '';
        if (!lyric) throw err;
        return cacheSet(cacheKey, { lyric, tlyric: '', rlyric: '', lxlyric: '' }, LRC_TTL_MS);
      });
    });
}

function lyricQq(musicInfo) {
  return lyricByDetail(
    musicInfo,
    'QQ',
    (mid) => '/api/qq_music?mid=' + encodeURIComponent(mid) + '&type=json',
    (keyword) => '/api/qq_music?msg=' + encodeURIComponent(keyword) + '&n=1&type=json'
  );
}

function lyricKugou(musicInfo) {
  return lyricByDetail(
    musicInfo,
    '酷狗',
    (id) => '/api/kugou_music?id=' + encodeURIComponent(id) + '&type=json',
    (keyword) => '/api/kugou_music?msg=' + encodeURIComponent(keyword) + '&n=1&type=json'
  );
}

// ==================== 协议入口 ====================

const RESOLVERS = {
  wy: {
    musicUrl(musicInfo, quality) {
      return musicUrlNetease(musicInfo, quality);
    },
    lyric: lyricNetease,
  },
  tx: {
    musicUrl(musicInfo, quality) {
      return musicUrlQq(musicInfo, QQKG_SIZE[quality], QQKG_LOWER[QQKG_SIZE[quality]], quality);
    },
    lyric: lyricQq,
  },
  kg: {
    musicUrl(musicInfo, quality) {
      return musicUrlKugou(musicInfo, QQKG_SIZE[quality], QQKG_LOWER[QQKG_SIZE[quality]], quality);
    },
    lyric: lyricKugou,
  },
};

on(EVENT_NAMES.request, ({ source, action, info }) => {
  // 诊断 trace：收到事件本身、以及字段解析结果（logcat: UserApi-JS）
  console.log(
    '[ChKSz] 收到请求 | source=' + source +
    ' action=' + action +
    (info && info.musicInfo ? ' id=' + String(info.musicInfo.id || info.musicInfo.songmid || info.musicInfo.hash || '?').slice(0, 40) : '')
  );
  const platform = RESOLVERS[source];
  if (!platform) return Promise.reject(new Error('不支持的音乐源：' + source));

  switch (action) {
    case 'musicUrl': {
      /**
       * 音质字段的判别——官方 LX 与 Melos App 的 info 结构在这里冲突，必须两边都照顾：
       *  - 官方 LX：{type: 'flac', musicInfo}，type 就是音质
       *  - Melos App：{type: 'music', quality: 'flac', musicInfo}，type 是源类型、quality 才是音质
       * 所以：quality 优先；info.type 只有是合法音质值（128k/320k/flac/flac24bit）才采用，
       * 否则（如 'music'）忽略——两种宿主下都能取到正确音质。
       */
      const VALID_QUALITY = ['128k', '320k', 'flac', 'flac24bit'];
      const typeField = info && info.type;
      const quality =
        (info && info.quality && VALID_QUALITY.includes(String(info.quality)) && info.quality) ||
        (typeField && VALID_QUALITY.includes(String(typeField)) && typeField) ||
        '128k';
      console.log('[ChKSz] 音质判定=' + quality + '（info.type=' + typeField + ', info.quality=' + (info && info.quality) + '）');
      return platform.musicUrl(info && info.musicInfo, String(quality));
    }
    case 'lyric':
      // 官方协议：lyric 的 info 是 {musicInfo}（无音质字段）
      return platform.lyric(info && info.musicInfo);
    default:
      return Promise.reject(new Error('不支持的 action：' + action));
  }
});

// 脚本初始化完成后必须发送 inited 事件；发送前任何错误都会被判为初始化失败
send(EVENT_NAMES.inited, {
  openDevTools: false, // 移动版无此选项，保留只为兼容桌面版
  sources: {
    wy: {
      name: '网易云音乐（ChKSz）',
      type: 'music',
      actions: ['musicUrl'], // 非本地源固定为 ['musicUrl']（官方文档）
      qualitys: ['128k', '320k', 'flac', 'flac24bit'],
    },
    tx: {
      name: 'QQ 音乐（ChKSz）',
      type: 'music',
      actions: ['musicUrl'],
      qualitys: ['128k', '320k', 'flac', 'flac24bit'],
    },
    kg: {
      name: '酷狗音乐（ChKSz）',
      type: 'music',
      actions: ['musicUrl'],
      qualitys: ['128k', '320k', 'flac', 'flac24bit'],
    },
  },
});

// ==================== 自诊断（logcat tag: UserApi-JS，用 adb logcat -s UserApi-JS 查看） ====================

/**
 * 为什么有这段：宿主在真机上跑不通时，靠猜是猜不出来的（我方沙箱模拟器全绿，
 * 真机却失败）。这里的每条 log 都对应一个"跑不通"的候选断点，逐条排掉就能定位。
 */
function selfDiagnose() {
  try {
    const checks = [];
    checks.push('lx=' + (typeof globalThis.lx === 'object' ? 'ok' : 'MISSING'));
    checks.push('EVENT_NAMES=' + (EVENT_NAMES && EVENT_NAMES.request === 'request' ? 'ok' : 'BAD'));
    checks.push('utils.buffer=' + (lx.utils && lx.utils.buffer && typeof lx.utils.buffer.from === 'function' ? 'ok' : 'MISSING'));
    checks.push('setTimeout=' + (typeof setTimeout === 'function' ? 'ok' : 'MISSING'));
    checks.push('encodeURIComponent=' + (typeof encodeURIComponent === 'function' ? 'ok' : 'MISSING'));
    checks.push('Promise=' + (typeof Promise === 'function' ? 'ok' : 'MISSING'));
    console.log('[ChKSz] 自检 | ' + checks.join(' | '));
  } catch (e) {
    console.log('[ChKSz] 自检失败：' + (e && e.message));
  }
}
selfDiagnose();

console.log('[ChKSz] 音源已加载，Key 长度 ' + String(API_KEY).length + '（不打印内容）');
