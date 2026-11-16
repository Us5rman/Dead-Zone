const http = require('http');
const { WebSocketServer } = require('ws');

const MAX_JOINERS = 3;
const rooms = new Map();
let nextId = 1;

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('dead zone relay ok');
});

const wss = new WebSocketServer({ server, maxPayload: 256 * 1024 });

function put(ws, obj) {
  if (ws && ws.readyState === 1) ws.send(JSON.stringify(obj));
}

wss.on('connection', ws => {
  ws.alive = true;
  ws.room = null;
  ws.on('pong', () => { ws.alive = true; });

  ws.on('message', raw => {
    ws.alive = true;
    let m;
    try { m = JSON.parse(raw); } catch (e) { return; }
    if (!m) return;
    if (m.t === 'ping') return put(ws, { t: 'pong', ts: m.ts });
    if (m.t === 'list') {
      const out = [];
      rooms.forEach((r, code) => {
        if (r.pub) out.push({ code, name: r.name, host: r.hostName, n: 1 + r.joiners.size, max: MAX_JOINERS + 1 });
      });
      return put(ws, { t: 'list', rooms: out.slice(0, 50) });
    }

    if (m.t === 'host') {
      const code = String(m.code || '').toUpperCase().slice(0, 4);
      if (ws.room) return;
      if (code.length !== 4 || rooms.has(code)) return put(ws, { t: 'err', e: 'taken' });
      rooms.set(code, { host: ws, joiners: new Map(), pub: !!m.pub, name: String(m.name || '').slice(0, 24), hostName: String(m.host || '').slice(0, 16) });
      ws.room = { code, host: true };
      put(ws, { t: 'hosted' });
      return;
    }

    if (m.t === 'join') {
      const code = String(m.code || '').toUpperCase().slice(0, 4);
      const room = rooms.get(code);
      if (ws.room) return;
      if (!room) return put(ws, { t: 'err', e: 'nohost' });
      if (room.joiners.size >= MAX_JOINERS) return put(ws, { t: 'full' });
      const id = 'p' + (nextId++);
      room.joiners.set(id, ws);
      ws.room = { code, id };
      put(ws, { t: 'joined', id });
      put(room.host, { t: 'peer_join', id });
      return;
    }

    if (m.t === 'g' && ws.room) {
      const room = rooms.get(ws.room.code);
      if (!room) return;
      if (ws.room.host) {
        if (m.to) put(room.joiners.get(m.to), { t: 'g', m: m.m });
        else room.joiners.forEach(j => put(j, { t: 'g', m: m.m }));
      } else {
        put(room.host, { t: 'from', id: ws.room.id, m: m.m });
      }
    }
  });

  ws.on('close', () => {
    if (!ws.room) return;
    const room = rooms.get(ws.room.code);
    if (!room) return;
    if (ws.room.host) {
      rooms.delete(ws.room.code);
      room.joiners.forEach(j => {
        put(j, { t: 'host_gone' });
        j.close();
      });
    } else {
      room.joiners.delete(ws.room.id);
      put(room.host, { t: 'peer_leave', id: ws.room.id });
    }
  });

  ws.on('error', () => {});
});

setInterval(() => {
  wss.clients.forEach(ws => {
    if (!ws.alive) return ws.terminate();
    ws.alive = false;
    ws.ping();
  });
}, 30000);

const port = process.env.PORT || 8080;
server.listen(port, () => console.log('relay listening on ' + port));
