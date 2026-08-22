// libSQL 重构后的端到端烟雾测试（走 HTTP，覆盖用户体系全部核心路径）
const BASE = 'http://127.0.0.1:8787';
let pass = 0, fail = 0;
function check(name, cond, extra = '') {
  if (cond) { pass++; console.log(`  ✓ ${name}`); }
  else { fail++; console.log(`  ✗ ${name} ${extra}`); }
}

const uname = 'dist_test_' + Date.now().toString(36);

// 1) 注册
let r = await fetch(`${BASE}/api/user/register`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: uname, password: 'test123456', nickname: '分布式测试' }),
});
let j = await r.json();
check('注册', j.code === 200 && j.data?.token, JSON.stringify(j).slice(0, 120));
const token = j.data?.token;
const H = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };

// 2) me
j = await (await fetch(`${BASE}/api/user/me`, { headers: H })).json();
check('me 返回昵称', j.code === 200 && j.data?.nickname === '分布式测试');

// 3) 收藏整表同步
const songs = [{ platform: 'netease', id: '5257138', title: '屋顶', artist: '周杰伦/温岚' }];
j = await (await fetch(`${BASE}/api/user/favorites`, { method: 'PUT', headers: H, body: JSON.stringify({ songs }) })).json();
check('收藏写入', j.code === 200 && j.data?.length === 1 && j.data[0].title === '屋顶');

// 4) 历史
j = await (await fetch(`${BASE}/api/user/history`, { method: 'PUT', headers: H, body: JSON.stringify({ songs }) })).json();
check('历史写入', j.code === 200 && j.data?.length === 1);

// 5) 歌单创建 + 加歌 + 查询
j = await (await fetch(`${BASE}/api/user/playlists`, { method: 'POST', headers: H, body: JSON.stringify({ name: '测试歌单' }) })).json();
check('建歌单', j.code === 200 && !!j.data?.id);
const pid = j.data.id;
j = await (await fetch(`${BASE}/api/user/playlists/${pid}/songs`, { method: 'POST', headers: H, body: JSON.stringify({ songs }) })).json();
check('加歌进歌单', j.code === 200 && j.data?.songCount === 1);
j = await (await fetch(`${BASE}/api/user/playlists/${pid}/songs`, { headers: H })).json();
check('读歌单歌曲', j.code === 200 && j.data?.length === 1);

// 6) 统计上报 + 查询
j = await (await fetch(`${BASE}/api/user/stats/report`, { method: 'POST', headers: H, body: JSON.stringify({ events: [{ platform: 'netease', id: '5257138', title: '屋顶', artist: '周杰伦', listenedMs: 90000, durationMs: 240000 }] }) })).json();
check('统计上报', j.code === 200 && j.data?.accepted === 1);
j = await (await fetch(`${BASE}/api/user/stats`, { headers: H })).json();
check('统计聚合(数字类型正确)', j.code === 200 && typeof j.data?.totalPlayMs === 'number' && j.data.totalPlayMs === 90000,
  `totalPlayMs=${JSON.stringify(j.data?.totalPlayMs)}`);

// 7) 业务接口仍正常
j = await (await fetch(`${BASE}/api/sources`)).json();
check('音源列表', j.code === 200 && j.data.length >= 2);
j = await (await fetch(`${BASE}/api/search?keyword=Jay&limit=20`)).json();
check('搜索(经远程DB加载音源)', j.code === 200 && j.data.length > 0, `count=${j.data?.length}`);

console.log(`\n结果: ${pass} 通过 / ${fail} 失败`);
process.exit(fail ? 1 : 0);
