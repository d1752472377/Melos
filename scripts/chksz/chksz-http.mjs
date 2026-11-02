/**
 * 带连接层兜底的 HTTP 工具
 *
 * 为什么需要它：api.chksz.com 走 Cloudflare，域名解析出多个 IP，个别 IP 连不上。
 * 实测同一个域名会出现"这次 20 秒超时、下一次 300 毫秒成功"的情况，
 * 而 fetch 只挑一个地址去连，撞上坏地址就直接报 fetch failed。
 *
 * 策略：
 *  1. 先用 fetch（走系统 DNS + keep-alive，正常网络下最快）；
 *  2. 连接层错误（超时/重置/解析失败）时，把域名解析成所有 IPv4，
 *     逐个直连，并且**按真实域名校验证书**（servername + checkServerIdentity），
 *     不关闭 TLS 校验。
 */

import https from 'node:https';
import dns from 'node:dns/promises';
import tls from 'node:tls';

const RETRIABLE = /TIMEOUT|ENOTFOUND|ECONNRESET|ECONNREFUSED|EPIPE|EAI_AGAIN|ETIMEDOUT|ECONNABORTED/;

/**
 * @param {string} url
 * @param {{verbose?: boolean, timeoutMs?: number, headers?: Record<string,string>, method?: string, redirect?: string}} [options]
 * @returns {Promise<{status:number, headers:Record<string,string>, text:string, via:string}>}
 */
export async function resilientFetch(url, options = {}) {
  const { verbose = false, timeoutMs = 15_000, headers = {}, method = 'GET', redirect = 'follow' } = options;

  try {
    const res = await fetch(url, {
      method,
      redirect,
      signal: AbortSignal.timeout(timeoutMs),
      headers: {
        accept: 'application/json, text/plain, */*',
        'user-agent': 'chksz-cli/1.0',
        ...headers,
      },
    });
    const text = await res.text();
    return { status: res.status, headers: Object.fromEntries(res.headers), text, via: 'fetch' };
  } catch (err) {
    const code = err?.cause?.code || err?.code || '';
    if (!RETRIABLE.test(code)) throw err;
    if (verbose) console.error(`  ! 连接层失败（${code || err.message}），改用固定 IP 重试`);
    return await pinnedFetch(url, { verbose, timeoutMs, headers, method });
  }
}

/** 逐个解析出来的 IPv4 试一遍；证书仍按真实域名校验 */
async function pinnedFetch(url, { verbose, timeoutMs, headers, method }) {
  const target = new URL(url);
  let addresses = [];
  try {
    addresses = (await dns.lookup(target.hostname, { all: true })).filter((a) => a.family === 4);
  } catch (err) {
    throw new Error(`DNS 解析失败：${err.message}`);
  }

  let lastError = null;
  // 单个 IP 不必等满整个超时：两个 IP 各等 10 秒，用户还等得起
  const perIpTimeout = Math.min(timeoutMs, 10_000);
  for (const { address } of addresses) {
    try {
      const result = await new Promise((resolvePromise, rejectPromise) => {
        const req = https.request(
          {
            host: address,
            port: Number(target.port) || 443,
            servername: target.hostname,
            path: target.pathname + target.search,
            method,
            headers: {
              Host: target.hostname,
              accept: 'application/json, text/plain, */*',
              'user-agent': 'chksz-cli/1.0',
              ...headers,
            },
            timeout: perIpTimeout,
            checkServerIdentity: (_host, cert) => tls.checkServerIdentity(target.hostname, cert),
          },
          (res) => {
            let text = '';
            res.setEncoding('utf8');
            res.on('data', (chunk) => (text += chunk));
            res.on('end', () => resolvePromise({ status: res.statusCode, headers: res.headers, text, via: address }));
          }
        );
        req.on('timeout', () => req.destroy(new Error('timeout')));
        req.on('error', rejectPromise);
        req.end();
      });
      if (verbose) console.error(`  ! 已通过 IP ${address} 连接成功`);
      return result;
    } catch (err) {
      lastError = err;
      if (verbose) console.error(`  ! IP ${address} 失败：${err.message}`);
    }
  }
  throw lastError || new Error('所有 IP 均连接失败');
}
