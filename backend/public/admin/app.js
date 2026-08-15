// ===== 状态 =====
const TOKEN_KEY = 'musicAdminToken';
let editingId = null; // null = 新增，否则为编辑中的音源 id

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

// ===== 视图切换 =====
function showLogin() {
  $('#loginView').hidden = false;
  $('#mainView').hidden = true;
}
function showMain() {
  $('#loginView').hidden = true;
  $('#mainView').hidden = false;
  loadSources();
}

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

// ===== 音源列表 =====
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
    wrap.innerHTML = '<div class="empty">暂无音源，点击「添加音源」创建</div>';
    updateHealth(0, 0);
    return;
  }
  const healthy = list.filter((s) => s.enabled).length;
  updateHealth(healthy, list.length);
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

function updateHealth(healthy, total) {
  const b = $('#healthBadge');
  b.textContent = `健康 ${healthy}/${total}`;
  b.className = 'badge ' + (healthy === total ? 'live' : 'dead');
}

// ===== 操作 =====
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

// ===== 模态框 =====
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
    // 密钥字段：编辑时展示脱敏占位，留空 = 不修改
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
  editingId = null;
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
    editingId = id;
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
  if (!editingId && !id) { $('#modalError').textContent = '请填写音源 ID'; return; }

  try {
    if (editingId) {
      await api(`/admin/sources/${editingId}`, { method: 'PUT', body: { name, weight, config } });
    } else {
      await api('/admin/sources', { method: 'POST', body: { id, name, type, weight, config } });
    }
    closeModal();
    loadSources();
  } catch (e) {
    $('#modalError').textContent = e.message;
  }
});

// ===== 工具 =====
function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function esc(s) {
  return String(s ?? '').replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

// ===== 启动 =====
if (getToken()) showMain(); else showLogin();
