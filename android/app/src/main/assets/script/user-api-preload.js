'use strict';

(function () {
  // 1. 全局环境与别名 Polyfill
  globalThis.window = globalThis;
  globalThis.global = globalThis;
  globalThis.self = globalThis;

  // 2. 补齐 console 方法（避免第三方脚本因未实现的 group/debug/trace 等方法崩溃）
  if (typeof globalThis.console === 'undefined') {
    globalThis.console = {
      log: () => {},
      info: () => {},
      warn: () => {},
      error: () => {},
      debug: () => {},
      trace: () => {},
      table: () => {},
      group: () => {},
      groupEnd: () => {},
      groupCollapsed: () => {},
      time: () => {},
      timeEnd: () => {},
    };
  } else {
    const noop = () => {};
    const methods = ['debug', 'trace', 'table', 'group', 'groupEnd', 'groupCollapsed', 'time', 'timeEnd'];
    for (const m of methods) {
      if (typeof globalThis.console[m] !== 'function') {
        globalThis.console[m] = noop;
      }
    }
  }

  // 核心初始化逻辑
  function initLxEnvironment(key, id, name, description, version, author, homepage, rawScript) {
    const _nativeCall = globalThis.__lx_native_call__;

    const checkLength = (str, length = 1048576) => {
      if (typeof str === 'string' && str.length > length) throw new Error('Input too long');
      return str;
    };

    const nativeFuncNames = [
      '__lx_native_call__set_timeout',
      '__lx_native_call__utils_str2b64',
      '__lx_native_call__utils_b642buf',
      '__lx_native_call__utils_str2md5',
      '__lx_native_call__utils_aes_encrypt',
      '__lx_native_call__utils_rsa_encrypt',
    ];
    const nativeFuncs = {};
    for (const n of nativeFuncNames) {
      const fn = globalThis[n];
      if (typeof fn === 'function') {
        nativeFuncs[n.replace('__lx_native_call__', '')] = (...args) => {
          for (const arg of args) checkLength(arg);
          return fn(...args);
        };
      }
    }

    const KEY_PREFIX = {
      publicKeyStart: '-----BEGIN PUBLIC KEY-----',
      publicKeyEnd: '-----END PUBLIC KEY-----',
      privateKeyStart: '-----BEGIN PRIVATE KEY-----',
      privateKeyEnd: '-----END PRIVATE KEY-----',
    };
    const RSA_PADDING = {
      OAEPWithSHA1AndMGF1Padding: 'RSA/ECB/OAEPWithSHA1AndMGF1Padding',
      NoPadding: 'RSA/ECB/NoPadding',
    };
    const AES_MODE = {
      CBC_128_PKCS7Padding: 'AES/CBC/PKCS7Padding',
      ECB_128_NoPadding: 'AES',
    };

    const nativeCall = (action, data) => {
      const str = JSON.stringify(data);
      checkLength(str, 4194304);
      if (_nativeCall) _nativeCall(key, action, str);
    };

    const callbacks = new Map();
    let timeoutId = 0;
    const _setTimeout = (callback, timeout = 0, ...params) => {
      if (typeof callback !== 'function') throw new Error('callback required a function');
      if (typeof timeout !== 'number' || timeout < 0) timeout = 0;
      if (timeoutId > 90000000000) timeoutId = 0;
      const tId = timeoutId++;
      callbacks.set(tId, {
        callback(...args) {
          callback(...args);
        },
        params,
      });
      if (nativeFuncs.set_timeout) {
        nativeFuncs.set_timeout(tId, parseInt(timeout));
      }
      return tId;
    };
    const _clearTimeout = (tId) => {
      callbacks.delete(tId);
    };
    const handleSetTimeout = (tId) => {
      const target = callbacks.get(tId);
      if (!target) return;
      callbacks.delete(tId);
      target.callback(...target.params);
    };

    // 字节数组与 UTF-8 编解码
    function bytesToString(bytes) {
      let result = '';
      let i = 0;
      while (i < bytes.length) {
        const byte = bytes[i];
        if (byte < 128) {
          result += String.fromCharCode(byte);
          i++;
        } else if (byte >= 192 && byte < 224) {
          result += String.fromCharCode(((byte & 31) << 6) | (bytes[i + 1] & 63));
          i += 2;
        } else {
          result += String.fromCharCode(((byte & 15) << 12) | ((bytes[i + 1] & 63) << 6) | (bytes[i + 2] & 63));
          i += 3;
        }
      }
      return result;
    }

    function stringToBytes(inputString) {
      const bytes = [];
      for (let i = 0; i < inputString.length; i++) {
        const charCode = inputString.charCodeAt(i);
        if (charCode < 128) {
          bytes.push(charCode);
        } else if (charCode < 2048) {
          bytes.push((charCode >> 6) | 192);
          bytes.push((charCode & 63) | 128);
        } else {
          bytes.push((charCode >> 12) | 224);
          bytes.push(((charCode >> 6) & 63) | 128);
          bytes.push((charCode & 63) | 128);
        }
      }
      return bytes;
    }

    const NATIVE_EVENTS_NAMES = {
      init: 'init',
      showUpdateAlert: 'showUpdateAlert',
      request: 'request',
      cancelRequest: 'cancelRequest',
      response: 'response',
    };
    const EVENT_NAMES = {
      request: 'request',
      inited: 'inited',
      updateAlert: 'updateAlert',
    };
    const eventNames = Object.values(EVENT_NAMES);
    const events = {
      request: null,
    };
    const allSources = ['kw', 'kg', 'tx', 'wy', 'mg', 'local'];
    const supportQualitys = {
      kw: ['128k', '320k', 'flac', 'flac24bit'],
      kg: ['128k', '320k', 'flac', 'flac24bit'],
      tx: ['128k', '320k', 'flac', 'flac24bit'],
      wy: ['128k', '320k', 'flac', 'flac24bit'],
      mg: ['128k', '320k', 'flac', 'flac24bit'],
      local: [],
    };
    const supportActions = {
      kw: ['musicUrl'],
      kg: ['musicUrl'],
      tx: ['musicUrl'],
      wy: ['musicUrl'],
      mg: ['musicUrl'],
      local: ['musicUrl', 'lyric', 'pic'],
    };

    const requestQueue = new Map();
    let isInitedApi = false;
    let isShowedUpdateAlert = false;

    const sendNativeRequest = (url, options, callback) => {
      const requestKey = Math.random().toString();
      const requestInfo = {
        aborted: false,
        abort: () => {
          nativeCall(NATIVE_EVENTS_NAMES.cancelRequest, requestKey);
        },
      };
      requestQueue.set(requestKey, {
        callback,
        requestInfo,
      });

      nativeCall(NATIVE_EVENTS_NAMES.request, { requestKey, url, options });
      return requestInfo;
    };

    const handleNativeResponse = ({ requestKey, error, response }) => {
      const targetRequest = requestQueue.get(requestKey);
      if (!targetRequest) return;
      requestQueue.delete(requestKey);
      targetRequest.requestInfo.aborted = true;
      if (error == null) {
        targetRequest.callback(null, response, response ? response.body : null);
      } else {
        targetRequest.callback(
          new Error(error),
          response || { statusCode: 0, headers: {}, body: null, rawText: '' },
          null
        );
      }
    };

    const handleRequest = ({ requestKey, data }) => {
      if (!events.request) {
        return nativeCall(NATIVE_EVENTS_NAMES.response, {
          requestKey,
          status: false,
          errorMessage: 'Request event is not defined in script',
        });
      }
      try {
        Promise.resolve(events.request(data))
          .then((result) => {
            nativeCall(NATIVE_EVENTS_NAMES.response, { requestKey, status: true, result });
          })
          .catch((err) => {
            nativeCall(NATIVE_EVENTS_NAMES.response, {
              requestKey,
              status: false,
              errorMessage: err ? err.message || String(err) : 'Unknown error',
            });
          });
      } catch (err) {
        nativeCall(NATIVE_EVENTS_NAMES.response, {
          requestKey,
          status: false,
          errorMessage: err ? err.message || String(err) : 'Unknown sync error',
        });
      }
    };

    const jsCall = (action, data) => {
      switch (action) {
        case '__run_error__':
          if (!isInitedApi) isInitedApi = true;
          return;
        case '__set_timeout__':
          handleSetTimeout(data);
          return;
        case 'request':
          handleRequest(data);
          return;
        case 'response':
          handleNativeResponse(data);
          return;
      }
      return 'Unknown action: ' + action;
    };

    globalThis.__lx_native__ = (_key, action, data) => {
      if (key !== _key) return 'Invalid key';
      return data == null ? jsCall(action) : jsCall(action, JSON.parse(data));
    };

    const handleInit = (info) => {
      if (!info) {
        nativeCall(NATIVE_EVENTS_NAMES.init, {
          info: null,
          status: false,
          errorMessage: 'Missing required parameter init info',
        });
        return;
      }
      const sourceInfo = {
        sources: {},
      };
      try {
        for (const source of allSources) {
          const userSource = info.sources ? info.sources[source] : null;
          if (!userSource || userSource.type !== 'music') continue;
          const qualitys = supportQualitys[source] || [];
          const actions = supportActions[source] || [];
          sourceInfo.sources[source] = {
            type: 'music',
            actions: actions.filter((a) => userSource.actions && userSource.actions.includes(a)),
            qualitys: qualitys.filter((q) => userSource.qualitys && userSource.qualitys.includes(q)),
          };
        }
      } catch (error) {
        nativeCall(NATIVE_EVENTS_NAMES.init, { info: null, status: false, errorMessage: error.message });
        return;
      }
      nativeCall(NATIVE_EVENTS_NAMES.init, { info: sourceInfo, status: true });
    };

    const handleShowUpdateAlert = (data, resolve, reject) => {
      if (!data || typeof data !== 'object') return reject(new Error('parameter format error.'));
      nativeCall(NATIVE_EVENTS_NAMES.showUpdateAlert, { log: data.log, updateUrl: data.updateUrl, name });
      resolve();
    };

    const dataToB64 = (data) => {
      if (typeof data === 'string') return nativeFuncs.utils_str2b64 ? nativeFuncs.utils_str2b64(data) : '';
      else if (Array.isArray(data) || ArrayBuffer.isView(data)) return utils.buffer.bufToString(data, 'base64');
      throw new Error('data type error: ' + typeof data);
    };

    const utils = {
      crypto: {
        aesEncrypt(buffer, mode, k, iv) {
          switch (mode) {
            case 'aes-128-cbc':
              return utils.buffer.from(
                nativeFuncs.utils_aes_encrypt(dataToB64(buffer), dataToB64(k), dataToB64(iv), AES_MODE.CBC_128_PKCS7Padding),
                'base64'
              );
            case 'aes-128-ecb':
              return utils.buffer.from(
                nativeFuncs.utils_aes_encrypt(dataToB64(buffer), dataToB64(k), '', AES_MODE.ECB_128_NoPadding),
                'base64'
              );
            default:
              throw new Error('Unsupported AES mode: ' + mode);
          }
        },
        rsaEncrypt(buffer, k) {
          if (typeof k !== 'string') throw new Error('Invalid RSA key');
          const cleanKey = k
            .replace(KEY_PREFIX.publicKeyStart, '')
            .replace(KEY_PREFIX.publicKeyEnd, '')
            .replace(/\s+/g, '');
          return utils.buffer.from(
            nativeFuncs.utils_rsa_encrypt(dataToB64(buffer), cleanKey, RSA_PADDING.NoPadding),
            'base64'
          );
        },
        randomBytes(size) {
          const byteArray = new Uint8Array(size);
          for (let i = 0; i < size; i++) {
            byteArray[i] = Math.floor(Math.random() * 256);
          }
          return byteArray;
        },
        md5(str) {
          if (typeof str !== 'string') throw new Error('param required a string');
          return nativeFuncs.utils_str2md5 ? nativeFuncs.utils_str2md5(encodeURIComponent(str)) : '';
        },
      },
      buffer: {
        from(input, encoding = 'utf8') {
          if (typeof input === 'string') {
            switch (encoding) {
              case 'binary':
                return stringToBytes(input);
              case 'base64':
                return nativeFuncs.utils_b642buf ? JSON.parse(nativeFuncs.utils_b642buf(input)) : [];
              case 'hex':
                const matches = input.match(/.{1,2}/g);
                return matches ? matches.map((byte) => parseInt(byte, 16)) : [];
              case 'utf8':
              case 'utf-8':
              default:
                return stringToBytes(input);
            }
          } else if (Array.isArray(input)) {
            return new Uint8Array(input);
          } else if (ArrayBuffer.isView(input)) {
            return input;
          } else {
            throw new Error('Unsupported input type for buffer: ' + typeof input);
          }
        },
        bufToString(buf, format = 'utf8') {
          if (Array.isArray(buf) || ArrayBuffer.isView(buf)) {
            switch (format) {
              case 'binary':
                return bytesToString(Array.from(buf));
              case 'hex':
                return Array.from(buf).reduce((str, byte) => str + byte.toString(16).padStart(2, '0'), '');
              case 'base64':
                return nativeFuncs.utils_str2b64 ? nativeFuncs.utils_str2b64(bytesToString(Array.from(buf))) : '';
              case 'utf8':
              case 'utf-8':
              default:
                return bytesToString(Array.from(buf));
            }
          } else {
            throw new Error('Input is not a valid buffer: ' + buf);
          }
        },
      },
    };

    // 全局 atob / btoa 注入
    if (typeof globalThis.atob === 'undefined') {
      globalThis.atob = (str) => {
        const buf = utils.buffer.from(str, 'base64');
        return bytesToString(Array.from(buf));
      };
    }
    if (typeof globalThis.btoa === 'undefined') {
      globalThis.btoa = (str) => {
        return nativeFuncs.utils_str2b64 ? nativeFuncs.utils_str2b64(str) : '';
      };
    }

    globalThis.lx = {
      EVENT_NAMES,
      request(url, options = {}, callback) {
        if (typeof arguments[1] === 'function') {
          callback = arguments[1];
          options = {};
        }
        let { method = 'get', timeout, headers, body, form, formData, binary } = options || {};
        let reqOptions = { headers, binary: binary === true };
        if (timeout && typeof timeout === 'number' && timeout > 0) {
          reqOptions.timeout = Math.min(timeout, 60000);
        }

        let request = sendNativeRequest(url, { method, body, form, formData, ...reqOptions }, (err, resp) => {
          if (err) {
            callback && callback(err, resp || { statusCode: 0, headers: {}, body: null }, null);
          } else {
            callback &&
              callback(
                null,
                {
                  statusCode: resp.statusCode,
                  statusMessage: resp.statusMessage,
                  headers: resp.headers,
                  body: resp.body,
                },
                resp.body
              );
          }
        });

        return () => {
          if (!request.aborted) request.abort();
          request = null;
        };
      },
      send(eventName, d) {
        return new Promise((resolve, reject) => {
          if (!eventNames.includes(eventName)) return reject(new Error('The event is not supported: ' + eventName));
          switch (eventName) {
            case EVENT_NAMES.inited:
              if (isInitedApi) return reject(new Error('Script is inited'));
              isInitedApi = true;
              handleInit(d);
              resolve();
              break;
            case EVENT_NAMES.updateAlert:
              if (isShowedUpdateAlert) return reject(new Error('The update alert can only be called once.'));
              isShowedUpdateAlert = true;
              handleShowUpdateAlert(d, resolve, reject);
              break;
            default:
              reject(new Error('Unknown event name: ' + eventName));
          }
        });
      },
      on(eventName, handler) {
        if (!eventNames.includes(eventName)) return Promise.reject(new Error('The event is not supported: ' + eventName));
        switch (eventName) {
          case EVENT_NAMES.request:
            events.request = handler;
            break;
          default:
            return Promise.reject(new Error('The event is not supported: ' + eventName));
        }
        return Promise.resolve();
      },
      utils,
      currentScriptInfo: {
        name,
        description,
        version,
        author,
        homepage,
        rawScript: rawScript || '',
      },
      version: '2.0.0',
      env: 'mobile',
    };

    globalThis.setTimeout = _setTimeout;
    globalThis.clearTimeout = _clearTimeout;

    console.log('[Melos UserApi] Preload initialized successfully for: ' + name);
  }

  // 支持通过 globalThis.__MELOS_KEY__ 与 __MELOS_META__ 自动初始化
  if (globalThis.__MELOS_KEY__) {
    const meta = globalThis.__MELOS_META__ || {};
    initLxEnvironment(
      globalThis.__MELOS_KEY__,
      meta.id || 'custom_script',
      meta.name || '自定义音源',
      meta.description || '',
      meta.version || '1.0.0',
      meta.author || '',
      meta.homepage || '',
      ''
    );
  }

  // 同时也保留 lx_setup 供显式调用
  globalThis.lx_setup = (k, id, n, desc, ver, auth, home, raw) => {
    initLxEnvironment(k, id, n, desc, ver, auth, home, raw);
  };
})();
