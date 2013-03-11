/* Look for port passed for Kevoree params */
var port = 8022
for(var i=0;i<process.argv.length;i++)
{
    if (process.argv[i] != ''){
        if(process.argv[i].indexOf("=") != -1){
            port = process.argv[i].substring(process.argv[i].indexOf("=")+1,process.argv[i].length);
        }
    }
}

var WebSocketServer = require('ws').Server
, wss = new WebSocketServer({port: port});
wss.on('connection', function(ws) {
  ws.on('message', function(message) {
      console.log('received: %s', message);
  });
  ws.send('something');
});