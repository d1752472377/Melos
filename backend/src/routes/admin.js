import { login, requireAuth } from '../lib/auth.js';
import { getSource, insertSource, updateSource, deleteSource, listSources } from '../lib/db.js';
import { encryptConfig, decryptConfig, maskConfig } from '../lib/crypto.js';
import { createSource, SUPPORTED_TYPES } from '../sources/index.js';

/** 解密 + 脱敏后返回音源记录 */
function toSafeRow(row, encryptionKey) {
  const cfg = decryptConfig(row.config, encryptionKey);
  return {
    id: row.id,
    name: row.name,
    type: row.type,
    enabled: !!row.enabled,
    weight: row.weight,
    config: maskConfig(cfg),
    hasSecret: !!(cfg.apiKey || cfg.secretKey || cfg.cookie),
    created_at: row.created_at,
    updated_at: row.updated_at,
  };
}

/**
 * 管理 API（全部需要鉴权，除 /admin/login）：
 *   POST /admin/login
 *   GET  /admin/sources
 *   POST /admin/sources           新增音源
 *   PUT  /admin/sources/:id       更新（含启用/禁用、权重、密钥）
 *   DELETE /admin/sources/:id
 *   POST /admin/sources/:id/test  连通性测试
 *   POST /admin/reload            重载注册表
 */
export function registerAdminRoutes(app, { config, db, registry }) {
  const auth = requireAuth(config);

  app.post('/admin/login', async (req, reply) => {
    const { username, password } = req.body || {};
    const r = login(username || '', password || '', config);
    if (r.error) return reply.code(401).send({ code: 401, msg: r.error });
    return { code: 200, data: { token: r.token, expiresIn: 24 * 60 * 60 } };
  });

  app.get('/admin/sources', { preHandler: auth }, async () => {
    const rows = await listSources(db);
    return { code: 200, data: rows.map((row) => toSafeRow(row, config.encryptionKey)) };
  });

  app.post('/admin/sources', { preHandler: auth }, async (req, reply) => {
    const { id, name, type, weight, config: cfg } = req.body || {};
    if (!id || !name || !type) {
      return reply.code(400).send({ code: 400, msg: '需要 id / name / type' });
    }
    if (!SUPPORTED_TYPES.includes(type)) {
      return reply.code(400).send({ code: 400, msg: `不支持的音源类型: ${type}（支持 ${SUPPORTED_TYPES.join(', ')}）` });
    }
    if (await getSource(db, id)) {
      return reply.code(409).send({ code: 409, msg: `音源 ${id} 已存在` });
    }
    const enc = encryptConfig(cfg || {}, config.encryptionKey);
    const row = await insertSource(db, { id, name, type, weight: weight ?? 100, config: enc });
    await registry.reload();
    return { code: 200, data: toSafeRow(row, config.encryptionKey) };
  });

  app.put('/admin/sources/:id', { preHandler: auth }, async (req, reply) => {
    const cur = await getSource(db, req.params.id);
    if (!cur) return reply.code(404).send({ code: 404, msg: '音源不存在' });

    const body = req.body || {};
    // config 合并：提交的非空字段覆盖现有；敏感字段留空 = 不修改（保留原值）
    const curCfg = decryptConfig(cur.config, config.encryptionKey);
    const mergedCfg = { ...curCfg };
    if (body.config && typeof body.config === 'object') {
      for (const [k, v] of Object.entries(body.config)) {
        if (v === '' || v === null || v === undefined) continue; // 空值不覆盖
        mergedCfg[k] = v;
      }
    }
    const fields = {
      name: body.name ?? cur.name,
      enabled: body.enabled ?? cur.enabled,
      weight: body.weight ?? cur.weight,
      config: encryptConfig(mergedCfg, config.encryptionKey),
    };
    const row = await updateSource(db, req.params.id, fields);
    await registry.reload();
    return { code: 200, data: toSafeRow(row, config.encryptionKey) };
  });

  app.delete('/admin/sources/:id', { preHandler: auth }, async (req, reply) => {
    const r = await deleteSource(db, req.params.id);
    if (!r.changes) return reply.code(404).send({ code: 404, msg: '音源不存在' });
    await registry.reload();
    return { code: 200, msg: '已删除' };
  });

  app.get('/admin/sources/:id/test', { preHandler: auth }, async (req, reply) => {
    const row = await getSource(db, req.params.id);
    if (!row) return reply.code(404).send({ code: 404, msg: '音源不存在' });
    try {
      const cfg = decryptConfig(row.config, config.encryptionKey);
      const source = createSource(row, cfg);
      const r = await source.healthCheck();
      return { code: 200, data: { id: row.id, ...r } };
    } catch (e) {
      return { code: 200, data: { id: row.id, ok: false, detail: e.message } };
    }
  });

  app.get('/admin/reload', { preHandler: auth }, async () => {
    await registry.reload();
    return { code: 200, data: registry.list() };
  });
}
