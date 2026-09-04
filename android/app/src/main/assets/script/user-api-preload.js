'use strict'

globalThis.lx_setup = (key, id, name, description, version, author, homepage, rawScript) => {
  delete globalThis.lx_setup
  const _nativeCall = globalThis.__lx_native_call__
  delete globalThis.__lx_native_call__
  const checkLength = (str, length = 1048576) => {
    if (typeof str == 'string' && str.length > length) throw new Error('Input too long')
    return str
  }
  const nativeFuncNames = [
    '__lx_native_call__set_timeout',
    '__lx_native_call__utils_str2b64',
    '__lx_native_call__utils_b642buf',
    '__lx_native_call__utils_str2md5',
    '__lx_native_call__utils_aes_encrypt',
    '__lx_native_call__utils_rsa_encrypt',
  ]
  const nativeFuncs = {}
  for (const name of nativeFuncNames) {
    const nativeFunc = globalThis[name]
    delete globalThis[name]
    nativeFuncs[name.replace('__lx_native_call__', '')] = (...args) => {
      for (const arg of args) checkLength(arg)
      return nativeFunc(...args)
    }
  }

  const KEY_PREFIX = {
    publicKeyStart: '-----BEGIN PUBLIC KEY-----',
    publicKeyEnd: '-----END PUBLIC KEY-----',
    privateKeyStart: '-----BEGIN PRIVATE KEY-----',
    privateKeyEnd: '-----END PRIVATE KEY-----',
  }
  const RSA_PADDING = {
    OAEPWithSHA1AndMGF1Padding: 'RSA/ECB/OAEPWithSHA1AndMGF1Padding',
    NoPadding: 'RSA/ECB/NoPadding',
  }
  const AES_MODE = {
    CBC_128_PKCS7Padding: 'AES/CBC/PKCS7Padding',
    ECB_128_NoPadding: 'AES',
  }
  const nativeCall = (action, data) => {
    data = JSON.stringify(data)
    checkLength(data, 4194304)
    _nativeCall(key, action, data)
  }

  const callbacks = new Map()
  let timeoutId = 0
  const _setTimeout = (callback, timeout = 0, ...params) => {
    if (typeof callback !== 'function') throw new Error('callback required a function')
    if (typeof timeout !== 'number' || timeout < 0) throw new Error('timeout required a number')
    if (timeoutId > 90000000000) throw new Error('max timeout')
    const id = timeoutId++
    callbacks.set(id, {
      callback(...args) {
        callback(...args)
      },
      params,
    })
    nativeFuncs.set_timeout(id, parseInt(timeout))
    return id
  }
  const _clearTimeout = (id) => {
    const target = callbacks.get(id)
    if (!target) return
    callbacks.delete(id)
  }
  const handleSetTimeout = (id) => {
    const target = callbacks.get(id)
    if (!target) return
    callbacks.delete(id)
    target.callback(...target.params)
  }

  // 将字节数组解码为字符串（UTF-8）
  function bytesToString(bytes) {
    let result = ''
    let i = 0
    while (i < bytes.length) {
      const byte = bytes[i]
      if (byte < 128) {
        result += String.fromCharCode(byte)
        i++
      } else if (byte >= 192 && byte < 224) {
        result += String.fromCharCode(((byte & 31) << 6) | (bytes[i + 1] & 63))
        i += 2
      } else {
        result += String.fromCharCode(((byte & 15) << 12) | ((bytes[i + 1] & 63) << 6) | (bytes[i + 2] & 63))
        i += 3
      }
    }
    return result
  }

  // 将字符串编码为字节数组（UTF-8）
  function stringToBytes(inputString) {
    const bytes = []
    for (let i = 0; i < inputString.length; i++) {
      const charCode = inputString.charCodeAt(i)
      if (charCode < 128) {
        bytes.push(charCode)
      } else if (charCode < 2048) {
        bytes.push((charCode >> 6) | 192)
        bytes.push((charCode & 63) | 128)
      } else {
        bytes.push((charCode >> 12) | 224)
        bytes.push(((charCode >> 6) & 63) | 128)
        bytes.push((charCode & 63) | 128)
      }
    }
    return bytes
  }

  const NATIVE_EVENTS_NAMES = {
    init: 'init',
    showUpdateAlert: 'showUpdateAlert',
    request: 'request',
    cancelRequest: 'cancelRequest',
    response: 'response',
  }
  const EVENT_NAMES = {
    request: 'request',
    inited: 'inited',
    updateAlert: 'updateAlert',
  }
  const eventNames = Object.values(EVENT_NAMES)
  const events = {
    request: null,
  }
  const allSources = ['kw', 'kg', 'tx', 'wy', 'mg', 'local']
  const supportQualitys = {
    kw: ['128k', '320k', 'flac', 'flac24bit'],
    kg: ['128k', '320k', 'flac', 'flac24bit'],
    tx: ['128k', '320k', 'flac', 'flac24bit'],
    wy: ['128k', '320k', 'flac', 'flac24bit'],
    mg: ['128k', '320k', 'flac', 'flac24bit'],
    local: [],
  }
  const supportActions = {
    kw: ['musicUrl'],
    kg: ['musicUrl'],
    tx: ['musicUrl'],
    wy: ['musicUrl'],
    mg: ['musicUrl'],
    local: ['musicUrl', 'lyric', 'pic'],
  }

  const requestQueue = new Map()
  let isInitedApi = false
  let isShowedUpdateAlert = false

  const sendNativeRequest = (url, options, callback) => {
    const requestKey = Math.random().toString()
    const requestInfo = {
      aborted: false,
      abort: () => {
        nativeCall(NATIVE_EVENTS_NAMES.cancelRequest, requestKey)
      },
    }
    requestQueue.set(requestKey, {
      callback,
      requestInfo,
    })

    nativeCall(NATIVE_EVENTS_NAMES.request, { requestKey, url, options })
    return requestInfo
  }

  const handleNativeResponse = ({ requestKey, error, response }) => {
    const targetRequest = requestQueue.get(requestKey)
    if (!targetRequest) return
    requestQueue.delete(requestKey)
    targetRequest.requestInfo.aborted = true
    if (error == null) {
      targetRequest.callback(null, response, response ? response.body : null)
    } else {
      // 容错：即使出错也传空 response 对象，防脚本未判空直接读取导致 NPE
      targetRequest.callback(
        new Error(error),
        response || { statusCode: 0, headers: {}, body: null, rawText: '' },
        null
      )
    }
  }

  const handleRequest = ({ requestKey, data }) => {
    if (!events.request) {
      return nativeCall(NATIVE_EVENTS_NAMES.response, {
        requestKey,
        status: false,
        errorMessage: 'Request event is not defined in script'
      })
    }
    try {
      Promise.resolve(events.request(data)).then(result => {
        nativeCall(NATIVE_EVENTS_NAMES.response, { requestKey, status: true, result })
      }).catch(err => {
        nativeCall(NATIVE_EVENTS_NAMES.response, {
          requestKey,
          status: false,
          errorMessage: err ? (err.message || String(err)) : 'Unknown error'
        })
      })
    } catch (err) {
      nativeCall(NATIVE_EVENTS_NAMES.response, {
        requestKey,
        status: false,
        errorMessage: err ? (err.message || String(err)) : 'Unknown sync error'
      })
    }
  }

  const jsCall = (action, data) => {
    switch (action) {
      case '__run_error__':
        if (!isInitedApi) isInitedApi = true
        return
      case '__set_timeout__':
        handleSetTimeout(data)
        return
      case 'request':
        handleRequest(data)
        return
      case 'response':
        handleNativeResponse(data)
        return
    }
    return 'Unknown action: ' + action
  }

  Object.defineProperty(globalThis, '__lx_native__', {
    enumerable: false,
    configurable: false,
    writable: false,
    value: (_key, action, data) => {
      if (key != _key) return 'Invalid key'
      return data == null ? jsCall(action) : jsCall(action, JSON.parse(data))
    },
  })

  const handleInit = (info) => {
    if (!info) {
      nativeCall(NATIVE_EVENTS_NAMES.init, {
        info: null,
        status: false,
        errorMessage: 'Missing required parameter init info'
      })
      return
    }
    const sourceInfo = {
      sources: {},
    }
    try {
      for (const source of allSources) {
        const userSource = info.sources ? info.sources[source] : null
        if (!userSource || userSource.type !== 'music') continue
        const qualitys = supportQualitys[source] || []
        const actions = supportActions[source] || []
        sourceInfo.sources[source] = {
          type: 'music',
          actions: actions.filter(a => userSource.actions && userSource.actions.includes(a)),
          qualitys: qualitys.filter(q => userSource.qualitys && userSource.qualitys.includes(q)),
        }
      }
    } catch (error) {
      nativeCall(NATIVE_EVENTS_NAMES.init, { info: null, status: false, errorMessage: error.message })
      return
    }
    nativeCall(NATIVE_EVENTS_NAMES.init, { info: sourceInfo, status: true })
  }

  const handleShowUpdateAlert = (data, resolve, reject) => {
    if (!data || typeof data != 'object') return reject(new Error('parameter format error.'))
    nativeCall(NATIVE_EVENTS_NAMES.showUpdateAlert, { log: data.log, updateUrl: data.updateUrl, name })
    resolve()
  }

  const dataToB64 = (data) => {
    if (typeof data === 'string') return nativeFuncs.utils_str2b64(data)
    else if (Array.isArray(data) || ArrayBuffer.isView(data)) return utils.buffer.bufToString(data, 'base64')
    throw new Error('data type error: ' + typeof data)
  }

  const utils = {
    crypto: {
      aesEncrypt(buffer, mode, key, iv) {
        switch (mode) {
          case 'aes-128-cbc':
            return utils.buffer.from(
              nativeFuncs.utils_aes_encrypt(dataToB64(buffer), dataToB64(key), dataToB64(iv), AES_MODE.CBC_128_PKCS7Padding),
              'base64'
            )
          case 'aes-128-ecb':
            return utils.buffer.from(
              nativeFuncs.utils_aes_encrypt(dataToB64(buffer), dataToB64(key), '', AES_MODE.ECB_128_NoPadding),
              'base64'
            )
          default:
            throw new Error('Unsupported AES mode: ' + mode)
        }
      },
      rsaEncrypt(buffer, key) {
        if (typeof key !== 'string') throw new Error('Invalid RSA key')
        const cleanKey = key.replace(KEY_PREFIX.publicKeyStart, '')
          .replace(KEY_PREFIX.publicKeyEnd, '')
          .replace(/\s+/g, '')
        return utils.buffer.from(
          nativeFuncs.utils_rsa_encrypt(dataToB64(buffer), cleanKey, RSA_PADDING.NoPadding),
          'base64'
        )
      },
      randomBytes(size) {
        const byteArray = new Uint8Array(size)
        for (let i = 0; i < size; i++) {
          byteArray[i] = Math.floor(Math.random() * 256)
        }
        return byteArray
      },
      md5(str) {
        if (typeof str !== 'string') throw new Error('param required a string')
        return nativeFuncs.utils_str2md5(encodeURIComponent(str))
      },
    },
    buffer: {
      from(input, encoding = 'utf8') {
        if (typeof input === 'string') {
          switch (encoding) {
            case 'binary':
              return stringToBytes(input)
            case 'base64':
              return JSON.parse(nativeFuncs.utils_b642buf(input))
            case 'hex':
              const matches = input.match(/.{1,2}/g)
              return matches ? matches.map(byte => parseInt(byte, 16)) : []
            case 'utf8':
            case 'utf-8':
            default:
              return stringToBytes(input)
          }
        } else if (Array.isArray(input)) {
          return new Uint8Array(input)
        } else if (ArrayBuffer.isView(input)) {
          return input
        } else {
          throw new Error('Unsupported input type for buffer: ' + typeof input)
        }
      },
      bufToString(buf, format = 'utf8') {
        if (Array.isArray(buf) || ArrayBuffer.isView(buf)) {
          switch (format) {
            case 'binary':
              return buf
            case 'hex':
              return Array.from(buf).reduce((str, byte) => str + byte.toString(16).padStart(2, '0'), '')
            case 'base64':
              return nativeFuncs.utils_str2b64(bytesToString(Array.from(buf)))
            case 'utf8':
            case 'utf-8':
            default:
              return bytesToString(Array.from(buf))
          }
        } else {
          throw new Error('Input is not a valid buffer: ' + buf)
        }
      },
    },
  }

  globalThis.lx = {
    EVENT_NAMES,
    request(url, { method = 'get', timeout, headers, body, form, formData, binary }, callback) {
      let options = { headers, binary: binary === true }
      if (timeout && typeof timeout == 'number' && timeout > 0) {
        options.timeout = Math.min(timeout, 60000)
      }

      let request = sendNativeRequest(url, { method, body, form, formData, ...options }, (err, resp) => {
        if (err) {
          callback(err, resp || { statusCode: 0, headers: {}, body: null }, null)
        } else {
          callback(null, {
            statusCode: resp.statusCode,
            statusMessage: resp.statusMessage,
            headers: resp.headers,
            body: resp.body,
          }, resp.body)
        }
      })

      return () => {
        if (!request.aborted) request.abort()
        request = null
      }
    },
    send(eventName, data) {
      return new Promise((resolve, reject) => {
        if (!eventNames.includes(eventName)) return reject(new Error('The event is not supported: ' + eventName))
        switch (eventName) {
          case EVENT_NAMES.inited:
            if (isInitedApi) return reject(new Error('Script is inited'))
            isInitedApi = true
            handleInit(data)
            resolve()
            break
          case EVENT_NAMES.updateAlert:
            if (isShowedUpdateAlert) return reject(new Error('The update alert can only be called once.'))
            isShowedUpdateAlert = true
            handleShowUpdateAlert(data, resolve, reject)
            break
          default:
            reject(new Error('Unknown event name: ' + eventName))
        }
      })
    },
    on(eventName, handler) {
      if (!eventNames.includes(eventName)) return Promise.reject(new Error('The event is not supported: ' + eventName))
      switch (eventName) {
        case EVENT_NAMES.request:
          events.request = handler
          break
        default:
          return Promise.reject(new Error('The event is not supported: ' + eventName))
      }
      return Promise.resolve()
    },
    utils,
    currentScriptInfo: {
      name: name || '自定义音源',
      description: description || '',
      version: version || '1.0.0',
      author: author || '',
      homepage: homepage || '',
      rawScript,
    },
    version: '2.0.0',
    env: 'mobile',
  }

  globalThis.setTimeout = _setTimeout
  globalThis.clearTimeout = _clearTimeout

  console.log('[Melos UserApi] Preload initialized successfully for: ' + name)
}
