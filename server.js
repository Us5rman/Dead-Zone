const express = require('express');
const { ExpressPeerServer } = require('peer');

const app = express();
const server = app.listen(3000, () => {
  console.log('Dead Zone running on http://localhost:3000');
});

const peerServer = ExpressPeerServer(server, {
  path: '/deadzone'
});

app.use('/peerjs', peerServer);
app.use(express.static(__dirname));
