/**
 * 邮件发送客户端：对接 Cloudflare 部署的自建邮件服务
 * 接口：POST /admin/send_mail
 * 鉴权：x-admin-auth
 */

/**
 * 发送邮箱验证码邮件
 * @param {object} options
 * @param {string} options.toMail 收件邮箱
 * @param {string} options.code 6 位数字验证码
 * @param {string} [options.scene='login'] 场景描述
 * @param {object} options.config 后端配置
 */
export async function sendVerifyCodeEmail({ toMail, code, scene = 'login', config }) {
  const serviceUrl = (config.mailServiceUrl || 'https://mail.dovislab.com').replace(/\/+$/, '');
  const adminAuth = config.mailAdminAuth || 'REDACTED';
  const fromMail = config.mailFromAddress || 'melos@dovislab.com';
  const fromName = config.mailFromName || 'Melos 音乐';

  let sceneText = '登录';
  if (scene === 'register') sceneText = '注册';
  else if (scene === 'bind') sceneText = '绑定/更换邮箱';
  else if (scene === 'unbind') sceneText = '解绑邮箱';
  const subject = `【Melos 音乐】${sceneText}验证码：${code}`;

  const textContent = `【Melos 音乐】您正在进行${sceneText}操作。
您的验证码为：${code}
验证码有效时间为 5 分钟。请勿将验证码泄露给他人。
如非本人操作，请忽略此邮件。`;

  const htmlContent = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background-color: #f6f5f2; margin: 0; padding: 30px; }
    .card { max-width: 480px; margin: 0 auto; background: #ffffff; border-radius: 16px; padding: 32px; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
    .brand { font-size: 20px; font-weight: 700; color: #1c1917; margin-bottom: 20px; display: flex; align-items: center; }
    .brand span { color: #5352ed; margin-right: 8px; }
    .title { font-size: 15px; color: #44403c; line-height: 1.6; margin-bottom: 24px; }
    .code-box { background: #f5f3ff; border: 1px dashed #7b61ff; border-radius: 12px; padding: 18px; text-align: center; margin: 24px 0; }
    .code { font-size: 32px; font-weight: 800; letter-spacing: 6px; color: #5352ed; font-family: Menlo, Monaco, monospace; }
    .tip { font-size: 13px; color: #78716c; line-height: 1.5; }
    .footer { margin-top: 32px; padding-top: 16px; border-top: 1px solid #f5f5f4; font-size: 11px; color: #a8a29e; text-align: center; }
  </style>
</head>
<body>
  <div class="card">
    <div class="brand"><span>Melos</span> 音乐</div>
    <div class="title">您好！您正在申请<strong>${sceneText}</strong> Melos 音乐账号，请在客户端输入下方验证码完成验证：</div>
    <div class="code-box">
      <div class="code">${code}</div>
    </div>
    <div class="tip">
      • 验证码有效期为 <strong>5 分钟</strong>，过期后请重新获取。<br>
      • 如非本人操作，请忽略此邮件，您的账号状态不会受到影响。
    </div>
    <div class="footer">
      本邮件由 Melos 音乐安全系统自动发出，请勿直接回复。
    </div>
  </div>
</body>
</html>
`;

  const payload = {
    from_name: fromName,
    from_mail: fromMail,
    to_mail: toMail,
    to_name: 'Melos 用户',
    subject,
    content: htmlContent,
    is_html: true,
  };

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 8000);

  try {
    const res = await fetch(`${serviceUrl}/admin/send_mail`, {
      method: 'POST',
      headers: {
        'x-admin-auth': adminAuth,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
      signal: controller.signal,
    });

    if (!res.ok) {
      const errText = await res.text().catch(() => '');
      throw new Error(`邮件发送失败 (${res.status}): ${errText || res.statusText}`);
    }

    const data = await res.json().catch(() => ({}));
    return { ok: true, data };
  } catch (err) {
    if (err.name === 'AbortError') {
      throw new Error('邮件服务响应超时，请稍后重试');
    }
    throw err;
  } finally {
    clearTimeout(timeoutId);
  }
}
