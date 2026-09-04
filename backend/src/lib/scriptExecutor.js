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
      }
    },
    request(url, options, callback) {
      if (typeof options === 'function') {
        callback = options;
        options = {};
      }
      const method = options.method || 'GET';
      const headers = options.headers || {};
      const body = options.body || (options.form ? new URLSearchParams(options.form).toString() : undefined);

      if (options.form && !headers['Content-Type'] && !headers['content-type']) {
        headers['Content-Type'] = 'application/x-www-form-urlencoded';
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
          const rawText = await res.text();
          let parsedBody = rawText;
          try {
            parsedBody = JSON.parse(rawText);
          } catch (_) {}

          callback?.(null, {
            statusCode: res.status,
            headers: Object.fromEntries(res.headers.entries()),
            body: parsedBody,
            rawText,
          });
        })
        .catch((err) => {
          clearTimeout(timeoutId);
          callback?.(err, {
            statusCode: 0,
            headers: {},
            body: null,
            rawText: '',
          }, null);
        });
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

// 常用测活歌曲用例（周杰伦 - 晴天，标准化 LX Music info 结构）
const TEST_CASES = [
  { source: 'kw', action: 'musicUrl', info: { type: 'music', quality: '128k', musicInfo: { songmid: '2689227' } } },
  { source: 'tx', action: 'musicUrl', info: { type: 'music', quality: '128k', musicInfo: { songmid: '0039MnYb0qxYig' } } },
  { source: 'wy', action: 'musicUrl', info: { type: 'music', quality: '128k', musicInfo: { songmid: '186016' } } },
  { source: 'kg', action: 'musicUrl', info: { type: 'music', quality: '128k', musicInfo: { hash: 'F16B95079144D9F80B91C8D409FF64D8' } } },
  { source: 'mg', action: 'musicUrl', info: { type: 'music', quality: '128k', musicInfo: { copyrightId: '60054701923' } } },
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
    },
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
    Buffer,
    Uint8Array,
    URL,
    URLSearchParams,
  };
  sandbox.globalThis = sandbox;
  sandbox.globalThis.lx = lx;

  const context = vm.createContext(sandbox);

  // 1. 运行初始化代码
  try {
    const script = new vm.Script(scriptContent, { timeout: 8000 });
    script.runInContext(context);
  } catch (err) {
    return {
      ok: false,
      status: 'error',
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
      status: 'error',
      message: '脚本未注册 EVENT_NAMES.request 监听器，非有效音源脚本',
      meta: initData,
      testResults: [],
    };
  }

  // 2. 依次测活支持的平台
  const testResults = [];
  let atLeastOneSuccess = false;

  for (const tc of TEST_CASES) {
    const start = Date.now();
    try {
      const result = await Promise.race([
        requestHandler({
          source: tc.source,
          action: tc.action,
          info: tc.info,
        }),
        new Promise((_, reject) => setTimeout(() => reject(new Error('请求超时 (10s)')), 10000)),
      ]);

      const costMs = Date.now() - start;
      const url = typeof result === 'string' ? result : result?.url;

      if (url && (url.startsWith('http://') || url.startsWith('https://'))) {
        atLeastOneSuccess = true;
        testResults.push({
          source: tc.source,
          success: true,
          costMs,
          urlSample: url.slice(0, 80) + '...',
        });
      } else {
        testResults.push({
          source: tc.source,
          success: false,
          costMs,
          error: `返回的 URL 无效: ${JSON.stringify(result)}`,
        });
      }
    } catch (e) {
      testResults.push({
        source: tc.source,
        success: false,
        costMs: Date.now() - start,
        error: e.message || String(e),
      });
    }
  }

  const status = atLeastOneSuccess ? 'healthy' : 'error';
  const successfulSources = testResults.filter((r) => r.success).map((r) => r.source).join(', ');
  const message = atLeastOneSuccess
    ? `测活成功！可用源: [${successfulSources}]`
    : `所有测试源均解析失败: ${testResults.map((r) => `${r.source}: ${r.error}`).join('; ')}`;

  return {
    ok: atLeastOneSuccess,
    status,
    message,
    meta: initData,
    testResults,
  };
}
