#!/usr/bin/env node
/*
 * Minimal Chrome DevTools Protocol client (no npm deps) for the on-device
 * WebView: adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>
 * Usage: node tools/cdp_eval.js "<js expression>" [pageUrlSubstring]
 * Prints Runtime.evaluate result.value (or exceptionDetails).
 */
const http = require('http');
const net = require('net');
const crypto = require('crypto');

const PORT = Number(process.env.CDP_PORT || 9222);
const expr = process.argv[2];
const match = process.argv[3] || 'index.html';
if (!expr) { console.error('usage: cdp_eval.js <expr> [urlMatch]'); process.exit(2); }

function listPages() {
  return new Promise((resolve, reject) => {
    http.get({ host: '127.0.0.1', port: PORT, path: '/json' }, (res) => {
      let d = '';
      res.on('data', (c) => (d += c));
      res.on('end', () => { try { resolve(JSON.parse(d)); } catch (e) { reject(e); } });
    }).on('error', reject);
  });
}

class WS {
  constructor(url) {
    const m = new URL(url);
    this.key = crypto.randomBytes(16).toString('base64');
    this.sock = net.connect(Number(m.port), m.hostname);
    this.buf = Buffer.alloc(0);
    this.messages = [];
    this.onmessage = null;
    this.ready = new Promise((resolve) => {
      this.sock.on('connect', () => {
        this.sock.write(
          `GET ${m.pathname} HTTP/1.1\r\nHost: ${m.host}\r\nUpgrade: websocket\r\n` +
          `Connection: Upgrade\r\nSec-WebSocket-Key: ${this.key}\r\nSec-WebSocket-Version: 13\r\n\r\n`,
        );
      });
      this.sock.on('data', (chunk) => {
        this.buf = Buffer.concat([this.buf, chunk]);
        if (!this.upgraded) {
          const i = this.buf.indexOf('\r\n\r\n');
          if (i < 0) return;
          this.buf = this.buf.subarray(i + 4);
          this.upgraded = true;
          resolve();
        }
        this.drain();
      });
    });
  }
  drain() {
    for (;;) {
      if (this.buf.length < 2) return;
      const fin = (this.buf[0] & 0x80) !== 0;
      const op = this.buf[0] & 0x0f;
      let len = this.buf[1] & 0x7f;
      let off = 2;
      if (len === 126) { if (this.buf.length < 4) return; len = this.buf.readUInt16BE(2); off = 4; }
      else if (len === 127) { if (this.buf.length < 10) return; len = Number(this.buf.readBigUInt64BE(2)); off = 10; }
      if (this.buf.length < off + len) return;
      const payload = this.buf.subarray(off, off + len);
      this.buf = this.buf.subarray(off + len);
      if (op === 8) { this.sock.end(); return; }
      if (op === 9) { this.frame(10, payload); continue; }
      if (op === 1 && fin && this.onmessage) this.onmessage(payload.toString('utf8'));
    }
  }
  frame(op, payload) {
    const len = payload.length;
    let head;
    if (len < 126) { head = Buffer.alloc(2); head[1] = len; }
    else if (len < 65536) { head = Buffer.alloc(4); head.writeUInt16BE(len, 2); head[1] |= 126; }
    else { head = Buffer.alloc(10); head.writeBigUInt64BE(BigInt(len), 2); head[1] |= 127; }
    head[0] = 0x80 | op;
    const mask = crypto.randomBytes(4);
    const parts = [];
    const h = Buffer.from(head);
    h[1] |= 0x80; // masked
    parts.push(h, mask);
    const masked = Buffer.alloc(len);
    for (let i = 0; i < len; i++) masked[i] = payload[i] ^ mask[i & 3];
    parts.push(masked);
    this.sock.write(Buffer.concat(parts));
  }
  send(obj) { this.frame(1, Buffer.from(JSON.stringify(obj))); }
}

(async () => {
  const pages = await listPages();
  const page = pages.find((p) => p.url.includes(match)) || pages[0];
  if (!page) { console.error('no page'); process.exit(1); }
  const ws = new WS(page.webSocketDebuggerUrl);
  await ws.ready;
  let id = 0;
  const pending = new Map();
  ws.onmessage = (t) => { const m = JSON.parse(t); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); } };
  const call = (method, params) => new Promise((r) => { const i = ++id; pending.set(i, r); ws.send({ id: i, method, params: params || {} }); });
  const res = await call('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
  const r = (res.result && res.result.result) || {};
  const out = r.exceptionDetails !== undefined
    ? r.exceptionDetails
    : (r.value !== undefined ? r.value : r);
  ws.sock.end();
  process.stdout.write(JSON.stringify(out) + '\n', () => process.exit(0));
})().catch((e) => { console.error(String(e && e.stack || e)); process.exit(1); });
