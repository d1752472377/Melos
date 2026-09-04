// ===== 状态 =====
const TOKEN_KEY = 'musicAdminToken';
let currentTab = 'scripts'; // 'scripts' | 'sources'
let editingLegacyId = null;
let editingScriptId = null;

// ===== 工具 =====
const $ = (sel) => document.querySelector(sel);

function getToken() { return localStorage.getItem(TOKEN_KEY); }
function setToken(t) { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY); }

async function api(path, { method = 'GET', body } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
  let json = {};
  try { json = await res.json(); } catch {}
  if (res.status === 401) {
    setToken(null);
    showLogin();
    throw new Error(json.msg || '登录已过期，请重新登录');
  }
  if (!res.ok) throw new Error(json.msg || `HTTP ${res.status}`);
  return json;
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function esc(s) {
  return String(s ?? '').replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

// ===== 视图切换 =====
function showLogin() {
  $('#loginView').hidden = false;
  $('#mainView').hidden = true;
}

function showMain() {
  $('#loginView').hidden = true;
  $('#mainView').hidden = false;

  const addr = $('#serverAddressDisplay');
  if (addr) {
    addr.textContent = window.location.origin;
  }

  switchTab('scripts');
}

function switchTab(tab) {
  currentTab = tab;
  if (tab === 'scripts') {
    $('#tabScriptsBtn').classList.add('active');
    $('#tabSourcesBtn').classList.remove('active');
    $('#scriptsView').hidden = false;
    $('#sourcesView').hidden = true;
    loadScripts();
  } else {
    $('#tabSourcesBtn').classList.add('active');
    $('#tabScriptsBtn').classList.remove('active');
    $('#sourcesView').hidden = false;
    $('#scriptsView').hidden = true;
    loadSources();
  }
}

$('#tabScriptsBtn').addEventListener('click', () => switchTab('scripts'));
$('#tabSourcesBtn').addEventListener('click', () => switchTab('sources'));

// ===== 登录 =====
$('#loginForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('#loginError').textContent = '';
  const username = $('#loginUser').value.trim();
  const password = $('#loginPass').value;
  try {
    const r = await api('/admin/login', { method: 'POST', body: { username, password } });
    setToken(r.data.token);
    showMain();
  } catch (err) {
    $('#loginError').textContent = err.message;
  }
});

$('#logoutBtn').addEventListener('click', () => {
  setToken(null);
  showLogin();
});

// ===================================================
// ===== 1. 音源脚本管理 =====
// ===================================================

async function loadScripts() {
  const wrap = $('#scriptsList');
  try {
    const r = await api('/api/scripts');
    renderScripts(r.data || []);
  } catch (err) {
    wrap.innerHTML = `<div class="empty">加载脚本失败：${escapeHtml(err.message)}</div>`;
  }
}

function renderScripts(list) {
  const wrap = $('#scriptsList');
  const activeCount = list.filter((s) => s.isActive).length;
  const healthyCount = list.filter((s) => s.status === 'success').length;

  $('#healthBadge').textContent = `已激活 ${activeCount} / 正常 ${healthyCount}`;
  $('#healthBadge').className = 'badge ' + (healthyCount > 0 ? 'live' : 'dead');

  if (!list.length) {
    wrap.innerHTML = '<div class="empty">暂无音源脚本，点击上方「导入 / 添加音源脚本」创建</div>';
    return;
  }

  wrap.innerHTML = list.map((s) => {
    let statusBadge = '<span class="badge">❓ 未测活</span>';
    if (s.status === 'success') {
      statusBadge = '<span class="badge live">✅ 测活正常</span>';
    } else if (s.status === 'failed') {
      statusBadge = '<span class="badge dead">❌ 解析异常</span>';
    }

    const activeBadge = s.isActive
      ? '<span class="badge secret">🟢 客户端优先同步</span>'
      : '<span class="badge">⚪ 备用/未激活</span>';

    const lastChecked = s.lastCheckedAt
      ? new Date(s.lastCheckedAt).toLocaleString()
      : '从未测试';

    return `
      <div class="source-card ${s.isActive ? '' : 'disabled'}">
        <div class="source-main">
          <div class="source-title">
            <span class="name">${escapeHtml(s.name)}</span>
            <span class="badge type">v${escapeHtml(s.version || '1.0.0')}</span>
            ${activeBadge}
            ${statusBadge}
          </div>
          <div class="source-meta" style="margin-top: 6px;">
            <span>ID: <code>${escapeHtml(s.id)}</code></span>
            <span>大小: <b>${Math.round(s.contentLength / 1024)} KB</b></span>
            <span>测活状态: <em>${escapeHtml(s.statusMessage || '无')}</em></span>
            <span>最近测活: ${escapeHtml(lastChecked)}</span>
          </div>
        </div>
        <div class="source-ops">
          <button class="btn sm primary" onclick="checkScript('${esc(s.id)}')">⚡ 立即测活</button>
          ${!s.isActive ? `<button class="btn sm" onclick="activateScript('${esc(s.id)}')">设为激活</button>` : ''}
          <button class="btn sm" onclick="editScript('${esc(s.id)}')">查看/编辑</button>
          <button class="btn sm danger" onclick="deleteScript('${esc(s.id)}')">删除</button>
        </div>
      </div>
    `;
  }).join('');
}

window.activateScript = async (id) => {
  try {
    const r = await api(`/api/scripts/${id}/activate`, { method: 'PUT' });
    alert(r.msg || '激活成功');
    loadScripts();
  } catch (e) {
    alert('激活失败: ' + e.message);
  }
};

window.checkScript = async (id) => {
  try {
    const r = await api(`/api/scripts/${id}/check`, { method: 'POST' });
    const res = r.data || {};
    const icon = res.status === 'success' ? '✅' : '❌';
    alert(`测活结果：${icon} ${res.message}\n详情：${JSON.stringify(res.details || {}, null, 2)}`);
    loadScripts();
  } catch (e) {
    alert('测活执行失败: ' + e.message);
    loadScripts();
  }
};

window.deleteScript = async (id) => {
  if (!confirm(`确定要彻底删除该音源脚本吗？`)) return;
  try {
    await api(`/api/scripts/${id}`, { method: 'DELETE' });
    loadScripts();
  } catch (e) {
    alert('删除失败: ' + e.message);
  }
};

// 脚本模态框
function closeScriptModal() {
  $('#scriptModal').hidden = true;
  editingScriptId = null;
}
$('#scriptModalClose').addEventListener('click', closeScriptModal);
$('#scriptModalCancel').addEventListener('click', closeScriptModal);
$('#scriptModal').addEventListener('click', (e) => { if (e.target === $('#scriptModal')) closeScriptModal(); });

$('#addScriptBtn').addEventListener('click', () => {
  editingScriptId = null;
  $('#scriptModalTitle').textContent = '导入音源脚本';
  $('#sf_name').value = '';
  $('#sf_url').value = '';
  $('#sf_content').value = '';
  $('#sf_isActive').checked = true;
  $('#sf_autoCheck').checked = true;
  $('#scriptModalError').textContent = '';
  $('#scriptModal').hidden = false;
});

window.editScript = async (id) => {
  try {
    const r = await api(`/api/scripts/${id}`);
    const s = r.data;
    if (!s) return;
    editingScriptId = id;
    $('#scriptModalTitle').textContent = `编辑音源脚本: ${s.name}`;
    $('#sf_name').value = s.name;
    $('#sf_url').value = '';
    $('#sf_content').value = s.content || '';
    $('#sf_isActive').checked = !!s.isActive;
    $('#sf_autoCheck').checked = true;
    $('#scriptModalError').textContent = '';
    $('#scriptModal').hidden = false;
  } catch (e) {
    alert('获取脚本详情失败: ' + e.message);
  }
};

// URL 抓取代码
$('#sf_fetchBtn').addEventListener('click', async () => {
  const url = $('#sf_url').value.trim();
  if (!url) {
    alert('请输入有效的脚本下载链接');
    return;
  }
  const btn = $('#sf_fetchBtn');
  btn.textContent = '抓取中...';
  btn.disabled = true;
  try {
    const res = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const text = await res.text();
    $('#sf_content').value = text;

    // 自动提取 @name
    const nameMatch = text.match(/@name\s+([^\r\n*]+)/);
    if (nameMatch && nameMatch[1]) {
      $('#sf_name').value = nameMatch[1].trim();
    }
    alert('抓取成功！已自动填入代码内容');
  } catch (e) {
    alert('抓取失败，可能是跨域(CORS)限制，请直接复制并在下方粘贴代码。错误: ' + e.message);
  } finally {
    btn.textContent = '抓取代码';
    btn.disabled = false;
  }
});

$('#scriptModalSave').addEventListener('click', async () => {
  $('#scriptModalError').textContent = '';
  const name = $('#sf_name').value.trim();
  const content = $('#sf_content').value.trim();
  const url = $('#sf_url').value.trim();
  const isActive = $('#sf_isActive').checked;
  const autoCheck = $('#sf_autoCheck').checked;

  if (!content && !url) {
    $('#scriptModalError').textContent = '请输入或抓取脚本代码';
    return;
  }

  const saveBtn = $('#scriptModalSave');
  saveBtn.textContent = '保存并测活中...';
  saveBtn.disabled = true;

  try {
    if (editingScriptId) {
      await api(`/api/scripts/${editingScriptId}`, {
        method: 'PUT',
        body: { name, content },
      });
      if (isActive) {
        await api(`/api/scripts/${editingScriptId}/activate`, { method: 'PUT' });
      }
      if (autoCheck) {
        await api(`/api/scripts/${editingScriptId}/check`, { method: 'POST' });
      }
    } else {
      await api('/api/scripts', {
        method: 'POST',
        body: { name, content, url, isActive, autoCheck },
      });
    }
    closeScriptModal();
    loadScripts();
  } catch (e) {
    $('#scriptModalError').textContent = e.message;
  } finally {
    saveBtn.textContent = '保存并入库';
    saveBtn.disabled = false;
  }
});

// ===================================================
// ===== 2. 传统音源列表与配置 (向下兼容) =====
// ===================================================

async function loadSources() {
  try {
    const r = await api('/admin/sources');
    renderSources(r.data);
  } catch (err) {
    $('#sourcesList').innerHTML = `<div class="empty">加载失败：${escapeHtml(err.message)}</div>`;
  }
}

function renderSources(list) {
  const wrap = $('#sourcesList');
  if (!list || !list.length) {
    wrap.innerHTML = '<div class="empty">暂无传统音源，点击「添加音源」创建</div>';
    return;
  }
  const healthy = list.filter((s) => s.enabled).length;
  if (currentTab === 'sources') {
    $('#healthBadge').textContent = `传统源 健康 ${healthy}/${list.length}`;
    $('#healthBadge').className = 'badge ' + (healthy === list.length ? 'live' : 'dead');
  }
  wrap.innerHTML = list.map((s) => `
    <div class="source-card ${s.enabled ? '' : 'disabled'}">
      <label class="switch" title="启用/禁用">
        <input type="checkbox" ${s.enabled ? 'checked' : ''} onchange="toggleSource('${esc(s.id)}', this.checked)">
        <span class="slider"></span>
      </label>
      <div class="source-main">
        <div class="source-title">
          <span class="name">${escapeHtml(s.name)}</span>
          <span class="id">${escapeHtml(s.id)}</span>
          <span class="badge type">${escapeHtml(s.type)}</span>
          ${s.hasSecret ? '<span class="badge secret">🔒 已配置密钥</span>' : '<span class="badge">无密钥</span>'}
        </div>
        <div class="source-meta">
          <span>权重 <b>${s.weight}</b></span>
          <span>${s.enabled ? '启用中' : '已禁用'}</span>
          <span>健康：${s.healthy !== false ? '✅' : '⚠️'}</span>
        </div>
      </div>
      <div class="source-ops">
        <button class="btn sm" onclick="testSource('${esc(s.id)}')">测试</button>
        <button class="btn sm" onclick="editSource('${esc(s.id)}')">编辑</button>
        <button class="btn sm danger" onclick="deleteSource('${esc(s.id)}')">删除</button>
      </div>
    </div>
  `).join('');
}

window.toggleSource = async (id, enabled) => {
  try { await api(`/admin/sources/${id}`, { method: 'PUT', body: { enabled } }); loadSources(); }
  catch (e) { alert('切换失败：' + e.message); }
};

window.deleteSource = async (id) => {
  if (!confirm(`确定删除音源「${id}」？`)) return;
  try { await api(`/admin/sources/${id}`, { method: 'DELETE' }); loadSources(); }
  catch (e) { alert('删除失败：' + e.message); }
};

window.testSource = async (id) => {
  try {
    const r = await api(`/admin/sources/${id}/test`);
    const d = r.data || {};
    alert(`音源「${id}」连通性：${d.ok ? '✅ 正常' : '❌ 异常'}\n${d.detail || ''}`);
  } catch (e) { alert('测试失败：' + e.message); }
};

$('#reloadBtn').addEventListener('click', async () => {
  try { await api('/admin/reload'); loadSources(); }
  catch (e) { alert('重载失败：' + e.message); }
});

const CONFIG_SCHEMA = {
  kuwo: [
    { key: 'apiKey', label: 'API Key', type: 'password', secret: true },
    { key: 'secretKey', label: 'Secret Key', type: 'password', secret: true },
    { key: 'baseUrl', label: 'Base URL（可选）', placeholder: 'https://api.yaohud.cn/api/music/kuwo' },
    { key: 'defaultQuality', label: '默认音质', type: 'select', options: ['lossless', 'SQ', 'Standard'] },
  ],
  netease: [
    { key: 'cookie', label: 'Cookie（可选，登录态）', type: 'password', secret: true },
  ],
};

function renderConfigFields(type, existing = {}) {
  const wrap = $('#configFields');
  const schema = CONFIG_SCHEMA[type] || [];
  wrap.innerHTML = schema.map((f) => {
    const cur = existing[f.key];
    if (f.type === 'select') {
      const opts = (f.options || []).map((o) => `<option value="${o}" ${o === cur ? 'selected' : ''}>${o}</option>`).join('');
      return `<label>${f.label}</label><select id="cfg_${f.key}">${opts}</select>`;
    }
    const isSecret = f.secret;
    const ph = isSecret && cur ? `${cur}（留空则不修改）` : (f.placeholder || '');
    const val = isSecret ? '' : (cur || '');
    return `<label>${f.label}</label><input type="${f.type || 'text'}" id="cfg_${f.key}" placeholder="${escapeHtml(ph)}" value="${escapeHtml(val)}" autocomplete="off">`;
  }).join('');
}

function collectConfig() {
  const cfg = {};
  document.querySelectorAll('#configFields [id^="cfg_"]').forEach((el) => {
    const key = el.id.replace('cfg_', '');
    const val = el.value.trim();
    if (val !== '') cfg[key] = val;
  });
  return cfg;
}

$('#addBtn').addEventListener('click', () => {
  editingLegacyId = null;
  $('#modalTitle').textContent = '添加音源';
  $('#f_id').disabled = false;
  $('#f_id').value = '';
  $('#f_name').value = '';
  $('#f_type').value = 'kuwo';
  $('#f_weight').value = 100;
  renderConfigFields('kuwo', {});
  $('#modalError').textContent = '';
  $('#modal').hidden = false;
});

window.editSource = async (id) => {
  try {
    const r = await api('/admin/sources');
    const s = (r.data || []).find((x) => x.id === id);
    if (!s) throw new Error('音源不存在');
    editingLegacyId = id;
    $('#modalTitle').textContent = `编辑音源「${id}」`;
    $('#f_id').disabled = true;
    $('#f_id').value = s.id;
    $('#f_name').value = s.name;
    $('#f_type').value = s.type;
    $('#f_weight').value = s.weight;
    renderConfigFields(s.type, s.config || {});
    $('#modalError').textContent = '';
    $('#modal').hidden = false;
  } catch (e) { alert(e.message); }
};

$('#f_type').addEventListener('change', (e) => renderConfigFields(e.target.value, {}));

function closeModal() { $('#modal').hidden = true; }
$('#modalClose').addEventListener('click', closeModal);
$('#modalCancel').addEventListener('click', closeModal);
$('#modal').addEventListener('click', (e) => { if (e.target === $('#modal')) closeModal(); });

$('#modalSave').addEventListener('click', async () => {
  $('#modalError').textContent = '';
  const id = $('#f_id').value.trim();
  const name = $('#f_name').value.trim();
  const type = $('#f_type').value;
  const weight = parseInt($('#f_weight').value, 10) || 100;
  const config = collectConfig();

  if (!name) { $('#modalError').textContent = '请填写名称'; return; }
  if (!editingLegacyId && !id) { $('#modalError').textContent = '请填写音源 ID'; return; }

  try {
    if (editingLegacyId) {
      await api(`/admin/sources/${editingLegacyId}`, { method: 'PUT', body: { name, weight, config } });
    } else {
      await api('/admin/sources', { method: 'POST', body: { id, name, type, weight, config } });
    }
    closeModal();
    loadSources();
  } catch (e) {
    $('#modalError').textContent = e.message;
  }
});

// ===== 启动 =====
if (getToken()) showMain(); else showLogin();
