import vm from 'node:vm';
import crypto from 'node:crypto';

/**
 * 解析音源脚本头部注释元数据
 */
export function parseScriptHeader(content) {
  const match = content.match(/\/\*[*!]?([\s\S]*?)\*\//);
  const info = {
    name: '未命名音源',
    description: '自定义音源脚本',
    version: '1.0.0',
    author: '',
    homepage: '',
  };
  if (match) {
    const text = match[1];
    for (const line of text.split('\n')) {
      const m = line.match(/\*\s*@([a-zA-Z0-9_-]+)\s*(.*)/);
      if (m) {
        const key = m[1].trim();
        const val = m[2].trim();
        if (key === 'name' && val) info.name = val;
        else if (key === 'description' && val) info.description = val;
        else if (key === 'version' && val) info.version = val;
        else if (key === 'author' && val) info.author = val;
        else if (key === 'homepage' && val) info.homepage = val;
      }
    }
  }
  return info;
}

/**
 * 构造模拟的 globalThis.lx 宿主环境
 */
function createLxEnvironment({ onInited, onRequestRegistered, scriptInfo = {} }) {
  const listeners = new Map();

  const lx = {
    EVENT_NAMES: {
      inited: 'inited',
      request: 'request',
      updateAlert: 'updateAlert',
    },
    version: '2.0.0',
    env: 'desktop',
    currentScriptInfo: {
      name: scriptInfo.name || '测试音源',
      description: scriptInfo.description || '',
      version: scriptInfo.version || '1.0.0',
      author: scriptInfo.author || '',
      homepage: '',
    },
    on(event, handler) {
      listeners.set(event, handler);
      if (event === 'request' && onRequestRegistered) {
        onRequestRegistered(handler);
      }
    },
    send(event, data) {
      if (event === 'inited' && onInited) {
        onInited(data);
      } else if (event === 'updateAlert') {
        // 脚本升级提示：测活环境忽略，仅记录
        if (data && typeof data === 'object') {
          console.warn(`[scriptExecutor] 脚本请求更新提示: ${data.updateUrl || data.log || ''}`);
        }
      }
    },
    request(url, options, callback) {
      if (typeof options === 'function') {
        callback = options;
        options = {};
      }
      const method = (options.method || 'GET').toUpperCase();
      const headers = { ...(options.headers || {}) };
      const binary = options.binary === true;
      let body = options.body;
      if (options.form) {
        body = new URLSearchParams(options.form).toString();
        if (!headers['Content-Type'] && !headers['content-type']) {
          headers['Content-Type'] = 'application/x-www-form-urlencoded';
        }
      }

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), options.timeout || 15000);

      fetch(url, {
        method,
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
          ...headers,
        },
        body: method !== 'GET' && method !== 'HEAD' ? body : undefined,
        signal: controller.signal,
      })
        .then(async (res) => {
          clearTimeout(timeoutId);
          let parsedBody;
          let rawText = '';
          if (binary) {
            parsedBody = Buffer.from(await res.arrayBuffer());
          } else {
            rawText = await res.text();
            parsedBody = rawText;
            try {
              parsedBody = JSON.parse(rawText);
            } catch (_) {}
          }

          callback?.(null, {
            statusCode: res.status,
            statusMessage: res.statusText,
            headers: Object.fromEntries(res.headers.entries()),
            body: parsedBody,
            rawText,
          }, parsedBody);
        })
        .catch((err) => {
          clearTimeout(timeoutId);
          callback?.(err, {
            statusCode: 0,
            statusMessage: '',
            headers: {},
            body: null,
            rawText: '',
          }, null);
        });

      return () => {
        clearTimeout(timeoutId);
        controller.abort();
      };
    },
    utils: {
      buffer: {
        from(data, encoding) {
          return Buffer.from(data, encoding);
        },
        bufToString(buf, encoding = 'utf8') {
          return Buffer.isBuffer(buf) ? buf.toString(encoding) : Buffer.from(buf).toString(encoding);
        },
      },
      crypto: {
        md5(str) {
          return crypto.createHash('md5').update(str).digest('hex');
        },
        aesEncrypt(buffer, mode, key, iv) {
          let cipherMode = 'aes-128-cbc';
          if (mode === 'aes-128-ecb') cipherMode = 'aes-128-ecb';
          const cipher = crypto.createCipheriv(cipherMode, key, iv || '');
          return Buffer.concat([cipher.update(buffer), cipher.final()]);
        },
        rsaEncrypt(buffer, key) {
          return crypto.publicEncrypt(
            {
              key,
              padding: crypto.constants.RSA_PKCS1_PADDING,
            },
            buffer
          );
        },
        randomBytes(size) {
          return crypto.randomBytes(size);
        },
      },
    },
  };

  return { lx, listeners };
}

// 标准 LX Music 音质类型列表
const DEFAULT_TYPES = [
  { type: '128k', size: '3.5M' },
  { type: '320k', size: '8.5M' },
  { type: 'flac', size: '25M' },
  { type: 'flac24bit', size: '40M' },
];

// 常用测活歌曲用例（周杰伦 - 晴天，标准化 LX Music info 结构）
const TEST_CASES = [
  {
    source: 'kw',
    action: 'musicUrl',
    info: {
      type: 'music',
      quality: '128k',
      source: 'kw',
      musicInfo: {
        id: '2689227',
        songmid: '2689227',
        source: 'kw',
        name: '晴天',
        title: '晴天',
        singer: '周杰伦',
        artist: '周杰伦',
        types: DEFAULT_TYPES,
        interval: '04:29',
      },
    },
  },
  {
    source: 'tx',
    action: 'musicUrl',
    info: {
      type: 'music',
      quality: '128k',
      source: 'tx',
      musicInfo: {
        id: '0039MnYb0qxYig',
        songmid: '0039MnYb0qxYig',
        source: 'tx',
        name: '晴天',
        title: '晴天',
        singer: '周杰伦',
        artist: '周杰伦',
        types: DEFAULT_TYPES,
        interval: '04:29',
      },
    },
  },
  {
    source: 'wy',
    action: 'musicUrl',
    info: {
      type: 'music',
      quality: '128k',
      source: 'wy',
      musicInfo: {
        id: '186016',
        songmid: '186016',
        source: 'wy',
        name: '晴天',
        title: '晴天',
        singer: '周杰伦',
        artist: '周杰伦',
        types: DEFAULT_TYPES,
        interval: '04:29',
      },
    },
  },
  {
    source: 'kg',
    action: 'musicUrl',
    info: {
      type: 'music',
      quality: '128k',
      source: 'kg',
      musicInfo: {
        id: 'F16B95079144D9F80B91C8D409FF64D8',
        songmid: 'F16B95079144D9F80B91C8D409FF64D8',
        hash: 'F16B95079144D9F80B91C8D409FF64D8',
        source: 'kg',
        name: '晴天',
        title: '晴天',
        singer: '周杰伦',
        artist: '周杰伦',
        types: DEFAULT_TYPES,
        interval: '04:29',
      },
    },
  },
  {
    source: 'mg',
    action: 'musicUrl',
    info: {
      type: 'music',
      quality: '128k',
      source: 'mg',
      musicInfo: {
        id: '60054701923',
        songmid: '60054701923',
        copyrightId: '60054701923',
        source: 'mg',
        name: '晴天',
        title: '晴天',
        singer: '周杰伦',
        artist: '周杰伦',
        types: DEFAULT_TYPES,
        interval: '04:29',
      },
    },
  },
];

/**
 * 执行并测活脚本
 * @param {string} scriptContent JS 完整代码
 * @returns {Promise<{ ok: boolean, status: string, message: string, meta: any, testResults: any[] }>}
 */
export async function testScriptExecution(scriptContent) {
  let initData = null;
  let requestHandler = null;

  const scriptInfo = parseScriptHeader(scriptContent);

  const { lx } = createLxEnvironment({
    scriptInfo,
    onInited(data) {
      initData = data;
    },
    onRequestRegistered(handler) {
      requestHandler = handler;
    },
  });

  const sandbox = {
    globalThis: {},
    console: {
      log: () => {},
      warn: () => {},
      error: () => {},
      info: () => {},
      debug: () => {},
      trace: () => {},
      group: () => {},
      groupEnd: () => {},
      groupCollapsed: () => {},
      time: () => {},
      timeEnd: () => {},
      table: () => {},
    },
    atob: (s) => Buffer.from(s, 'base64').toString('binary'),
    btoa: (s) => Buffer.from(s, 'binary').toString('base64'),
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    Date,
    Math,
    JSON,
    RegExp,
    Array,
    Object,
    String,
    Number,
    Boolean,
    Promise,
    Map,
    Set,
    Buffer,
    Uint8Array,
    URL,
    URLSearchParams,
    crypto: globalThis.crypto,
    TextEncoder,
    TextDecoder,
  };
  sandbox.globalThis = sandbox;
  sandbox.lx = lx;
  // 兼容第三方脚本对 window / global / self 的引用（与客户端 preload 保持一致）
  sandbox.window = sandbox;
  sandbox.self = sandbox;
  sandbox.global = sandbox;

  const context = vm.createContext(sandbox);

  // 1. 运行初始化代码
  try {
    const script = new vm.Script(scriptContent, { timeout: 8000 });
    script.runInContext(context);
  } catch (err) {
    return {
      ok: false,
      status: 'failed',
      message: `脚本语法或初始化执行错误: ${err.message}`,
      meta: null,
      testResults: [],
    };
  }

  // 等待异步握手（部分脚本启动时有异步网络请求如版本检查）
  const waitStart = Date.now();
  while (!requestHandler && Date.now() - waitStart < 6000) {
    await new Promise((r) => setTimeout(r, 200));
  }

  if (!requestHandler) {
    return {
      ok: false,
      status: 'failed',
      message: '脚本未注册 EVENT_NAMES.request 监听器，非有效音源脚本',
      meta: initData,
      testResults: [],
    };
  }

  // 2. 依次测活支持的平台（只测试脚本实际声明支持的源）
  const testResults = [];
  let atLeastOneSuccess = false;

  const supportedSourceKeys = initData?.sources ? Object.keys(initData.sources) : null;
  const casesToTest = (supportedSourceKeys && supportedSourceKeys.length > 0)
    ? TEST_CASES.filter((tc) => supportedSourceKeys.includes(tc.source))
    : TEST_CASES;

  for (const tc of casesToTest) {
    const start = Date.now();
    let timeoutId = null;
    try {
      const result = await Promise.race([
        requestHandler({
          source: tc.source,
          action: tc.action,
          info: tc.info,
        }),
        new Promise((_, reject) => {
          timeoutId = setTimeout(() => reject(new Error('请求超时 (10s)')), 10_000);
        }),
      ]);

      const costMs = Date.now() - start;
      // 兼容三种返回形态：字符串直链 / { url } / { musicUrl }；并透出脚本自带错误信息
      let url = null;
      let scriptError = null;
      if (typeof result === 'string') {
        url = result;
      } else if (result && typeof result === 'object') {
        url = result.url || result.musicUrl || null;
        scriptError = result.errorMessage || result.error || result.msg || null;
      }

      if (url && /^https?:\/\//i.test(String(url))) {
        atLeastOneSuccess = true;
        testResults.push({
          source: tc.source,
          success: true,
          costMs,
          urlSample: String(url).slice(0, 80) + '...',
        });
      } else {
        testResults.push({
          source: tc.source,
          success: false,
          costMs,
          error: scriptError
            ? String(scriptError)
            : `未返回可用直链（该测试歌曲可能无版权/VIP）: ${JSON.stringify(result).slice(0, 120)}`,
        });
      }
    } catch (e) {
      testResults.push({
        source: tc.source,
        success: false,
        costMs: Date.now() - start,
        error: e.message || String(e),
      });
    } finally {
      if (timeoutId) clearTimeout(timeoutId);
    }
  }

  const status = atLeastOneSuccess ? 'success' : 'failed';
  const successfulSources = testResults.filter((r) => r.success).map((r) => r.source).join(', ');
  const message = atLeastOneSuccess
    ? `测活成功！可用源: [${successfulSources}]`
    : `所有测试源均解析失败: ${testResults.map((r) => `${r.source}: ${r.error}`).join('; ')}`;

  return {
    ok: atLeastOneSuccess,
    status,
    message,
    meta: initData,
    supportedSources: supportedSourceKeys,
    testResults,
  };
}
