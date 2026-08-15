import { KuwoSource } from './kuwo.js';
import { NeteaseSource } from './netease.js';
import { ChkszSource } from './chksz.js';
import { ChkszQQSource, ChkszKugouSource } from './chksz_ext.js';

/**
 * 根据音源记录 + 解密后的 config 构造对应的源实例。
 * 新增音源类型时，在这里加一个分支即可。
 */
export function createSource(record, config) {
  const { id, name, type } = record;
  switch (type) {
    case 'kuwo':
      return new KuwoSource({
        id,
        name,
        apiKey: config.apiKey,
        secretKey: config.secretKey,
        baseUrl: config.baseUrl,
        defaultQuality: config.defaultQuality,
      });
    case 'netease':
      return new NeteaseSource({ id, name, cookie: config.cookie });
    case 'chksz':
      return new ChkszSource({
        id,
        name,
        apiKey: config.apiKey,
        baseUrl: config.baseUrl,
      });
    case 'qq':
      return new ChkszQQSource({ id, name, apiKey: config.apiKey, baseUrl: config.baseUrl });
    case 'kugou':
      return new ChkszKugouSource({ id, name, apiKey: config.apiKey, baseUrl: config.baseUrl });
    default:
      throw new Error(`未知音源类型: ${type}`);
  }
}

export const SUPPORTED_TYPES = ['kuwo', 'netease', 'chksz', 'qq', 'kugou'];
