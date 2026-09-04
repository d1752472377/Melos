import crypto from 'node:crypto';
import { verifyToken } from '../lib/auth.js';
import {
  listScripts,
  getActiveScript,
  getScript,
  insertScript,
  updateScript,
  setActiveScript,
  updateScriptStatus,
  deleteScript,
} from '../lib/db.js';
import { testScriptExecution } from '../lib/scriptExecutor.js';

export function registerScriptsRoutes(app, { db, config }) {
  // 鉴权检查：写操作需要 admin 权限或 syncToken
  function checkAuth(req, reply) {
    const authHeader = req.headers['authorization'] || '';
    const syncTokenHeader = req.headers['x-sync-token'] || '';

    let token = '';
    if (authHeader.startsWith('Bearer ')) {
      token = authHeader.slice(7).trim();
    } else if (syncTokenHeader) {
      token = syncTokenHeader.trim();
    }

    // 允许通过 syncToken 或 adminPassword 鉴权
    if (token && (token === config.syncToken || token === config.adminPassword)) {
      return true;
    }

    // 允许通过管理后台 HMAC Token 鉴权
    if (token && verifyToken(token, config.encryptionKey)) {
      return true;
    }

    reply.status(401).send({ code: 401, msg: '未授权：请提供有效的同步密钥或管理口令' });
    return false;
  }

  // 1. 获取当前生效的脚本（供客户端直接拉取，公开或凭 Token 访问）
  app.get('/api/scripts/active', async (req, reply) => {
    const active = await getActiveScript(db);
    if (!active) {
      return { code: 404, msg: '暂无已启用的音源脚本', data: null };
    }
    return {
      code: 200,
      data: {
        id: active.id,
        name: active.name,
        version: active.version,
        description: active.description,
        content: active.content,
        status: active.status,
        statusMessage: active.status_message,
        lastCheckedAt: active.last_checked_at,
        updatedAt: active.updated_at,
      },
    };
  });

  // 2. 脚本列表（管理端查看，排除极长 content 减少网络负担）
  app.get('/api/scripts', async (req, reply) => {
    const scripts = await listScripts(db);
    return {
      code: 200,
      data: scripts.map((s) => ({
        id: s.id,
        name: s.name,
        version: s.version,
        description: s.description,
        isActive: s.is_active === 1,
        status: s.status,
        statusMessage: s.status_message,
        lastCheckedAt: s.last_checked_at,
        createdAt: s.created_at,
        updatedAt: s.updated_at,
        contentLength: s.content ? s.content.length : 0,
      })),
    };
  });

  // 3. 获取单条脚本详情（含完整 content）
  app.get('/api/scripts/:id', async (req, reply) => {
    const script = await getScript(db, req.params.id);
    if (!script) {
      return reply.status(404).send({ code: 404, msg: '脚本不存在' });
    }
    return {
      code: 200,
      data: {
        ...script,
        isActive: script.is_active === 1,
      },
    };
  });

  // 4. 新增脚本（支持直接传 content，或传 url 自动下载）
  app.post('/api/scripts', async (req, reply) => {
    if (!checkAuth(req, reply)) return;

    const { name, version, description, content, url, isActive = false, autoCheck = true } = req.body || {};
    let scriptText = content || '';

    if (!scriptText && url) {
      try {
        const res = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        scriptText = await res.text();
      } catch (e) {
        return reply.status(400).send({ code: 400, msg: `从 URL 抓取脚本失败: ${e.message}` });
      }
    }

    if (!scriptText || !scriptText.trim()) {
      return reply.status(400).send({ code: 400, msg: '脚本内容不能为空' });
    }

    const id = 'script_' + crypto.randomUUID().replace(/-/g, '').slice(0, 12);
    const created = await insertScript(db, {
      id,
      name: name || '未命名音源',
      version: version || '1.0.0',
      description: description || '',
      content: scriptText,
      isActive: isActive ? 1 : 0,
    });

    let checkResult = null;
    if (autoCheck) {
      checkResult = await testScriptExecution(scriptText);
      await updateScriptStatus(db, id, {
        status: checkResult.status,
        statusMessage: checkResult.message,
      });
    }

    return {
      code: 200,
      msg: '添加脚本成功',
      data: {
        id,
        checkResult,
      },
    };
  });

  // 5. 更新脚本基本信息
  app.put('/api/scripts/:id', async (req, reply) => {
    if (!checkAuth(req, reply)) return;
    const { id } = req.params;
    const updated = await updateScript(db, id, req.body || {});
    if (!updated) {
      return reply.status(404).send({ code: 404, msg: '脚本不存在' });
    }
    return { code: 200, msg: '更新脚本成功', data: updated };
  });

  // 6. 激活某个脚本
  app.put('/api/scripts/:id/activate', async (req, reply) => {
    if (!checkAuth(req, reply)) return;
    const { id } = req.params;
    const cur = await getScript(db, id);
    if (!cur) return reply.status(404).send({ code: 404, msg: '脚本不存在' });

    await setActiveScript(db, id);
    return { code: 200, msg: `已切换激活音源为 [${cur.name}]` };
  });

  // 7. 测活执行器接口
  app.post('/api/scripts/:id/check', async (req, reply) => {
    const { id } = req.params;
    const cur = await getScript(db, id);
    if (!cur) return reply.status(404).send({ code: 404, msg: '脚本不存在' });

    const checkResult = await testScriptExecution(cur.content);
    await updateScriptStatus(db, id, {
      status: checkResult.status,
      statusMessage: checkResult.message,
    });

    return {
      code: 200,
      msg: '测活完成',
      data: checkResult,
    };
  });

  // 8. 删除脚本
  app.delete('/api/scripts/:id', async (req, reply) => {
    if (!checkAuth(req, reply)) return;
    const { id } = req.params;
    await deleteScript(db, id);
    return { code: 200, msg: '删除成功' };
  });
}
