# WebSocketGroup documentation

This Kevoree Group aim to provide a WebSocket implementation of fragment communications

## What could I use ?
In this module you have 4 different kinds of group.  
Each one has its own purpose and works with WebSocket API such as [Webbit] [1] & [Java_WebSocket] [2]

## The 4 different groups
* **WebSocketGroup**: starts a server & a client on each fragment meaning that if a node is behind a router you won't probably be able to push/pull anything from it

* **WebSocketGroupMasterServer**: requires that you specify **one** (and only one) master server within your group nodes making this group a fully centralized network. Each other nodes will connect themselves to this master server.  
								  This group disallows push/pull requests on other nodes than the **master server one** throwing exceptions back at you if you dare to try.
								  
* **WebSocketGroupEchoer**: same as **WebSocketGroupMasterServer** but this one allows you (or your puppy who knows ?) to push/pull on every nodes

* **WebSocketGroupQueuer**: same as **WebSocketGroupEchoer** but this one is able to recognize that some nodes from its group are not yet connected to him so it will keep a **waitingQueue** up-to-date and then echo back models when those waiting nodes will initiate a connection

## What about the first one : *WebSocketGroup*
### Node start
When a node starts, this group creates a Webbit socket server that is capable of handling 4 different kinds of requests :

*   host:port/push
*   host:port/pull
*   host:port/push/zip
*   host:port/pull/zip

### Push process
When a push is requested on a node. This group compresses the given model and try to send it to the targeted node on :

*   ws://host:port/push/zip

The targeted node will then process the model in the **pushCompressedHandler**  
```java
private BaseWebSocketHandler pushCompressedHandler = new BaseWebSocketHandler() {
    public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
		logger.debug("Compressed model received from "+connection.httpRequest().header("Host")+": loading...");
		ByteArrayInputStream bais = new ByteArrayInputStream(msg);
		ContainerRoot model = KevoreeXmiHelper.$instance.loadCompressedStream(bais);
		updateLocalModel(model);
		logger.debug("Model loaded from XMI String");
    }
};
```

### Pull process
When a pull is requested on a node. This group asks the targeted node via :

* ws://host:port/pull/zip

The targeted node will then process the model in the **pullCompressedHandler**  
```java
private BaseWebSocketHandler pullCompressedHandler = new BaseWebSocketHandler() {
    public void onMessage(WebSocketConnection connection, byte[] msg) throws Throwable {
		logger.debug("Pull request received from "+connection.httpRequest().header("Host")+": loading...");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
			KevoreeXmiHelper.$instance.saveCompressedStream(output, getModelService().getLastModel());
		connection.send(output.toByteArray());
		logger.debug("Compressed model pulled back to "+connection.httpRequest().header("Host"));
    }
};
```

## What about the second one : *WebSocketGroupMasterServer*
### Node start
With this group, when the group fragment starts on a node it will check if a **port** as been given to it.  
If a port as been given, then it means that the related node will be the **master server** and every other node will initiate a connection to it when they will start.

![WebSocketGroupMasterServer KevoreeEditor Config](http://i48.tinypic.com/2c0mte.jpg)

In this example, **node0** will be the master server because it has its port property set in the group (port 8000).

### Push process
WebSocketGroupMasterServer **only allows master server node** to process push requests. So if you try to do a push on an other node **nothing will happen**.  
By the way, if you initiate the push procedure on the master server node, it will send a request to the given node *host:port* via **WebSocket** following this schema:  

<table>
  <tr>
  	<td>URI</td>
    <td>ws://host:port/</td>
  </tr>
  <tr>
  	<td>data[0]</td>
    <td>control byte (to let the server know it is a PUSH)</td>
  </tr>
  <tr>
  	<td>data[1..n]</td>
    <td>compressed model bytes</td>
  </tr>
</table>

Once the master server node receives the push request, it will deserialize the model and apply it locally.  
Then it will forward the push request to each sub-nodes of the group.

```java
/**
 * In this context you are a master server and you should do the work
 * associated with the PUSH event requested from another client.
 * 
 * @param connection
 *            a client
 * @param msg
 */
protected void onMasterServerPushEvent(WebSocketConnection connection, byte[] msg) {
	logger.debug("PUSH: " + connection.httpRequest().remoteAddress() + " asked for a PUSH");
	ByteArrayInputStream bais = new ByteArrayInputStream(msg, 1, msg.length - 1);
	ContainerRoot model = KevoreeXmiHelper.$instance.loadCompressedStream(bais);
	updateLocalModel(model);

	logger.debug("server knows: " + clients.toString());
	// broadcasting model to each client
	for (WebSocketConnection conn : clients.keySet()) {
		logger.debug("Trying to push model to client " + conn.httpRequest().remoteAddress());
		conn.send(msg, 1, msg.length - 1); // offset is for the control byte
	}
}
```

### Pull process
WebSocketGroupMasterServer **only allows master server node** to process pull requests. So if you try to do a pull on an other node a **NotAMasterServerException** will be thrown.
To process pull requests, this group will create a new WebSocketClient and send a message with the PULL control byte.  
Once the server receives the message it will serialize the model from the targetted node and send it back to the client.  

> This process uses *java.util.concurrent.Exchanger* in order to pass the model from the client thread to the pull method thread when it is done.  
> The Exchanger is created with a timeout set to 5 seconds, meaning that if the model is not sent back within this time you will get a **null** in return.


## What about the third one : *WebSocketGroupEchoer*
### Node start
TODO

### Push process
TODO

### Pull process
TODO

## What about the fourth one : *WebSocketGroupQueuer*
### Node start
TODO

### Push process
TODO

### Pull process
TODO

[1]: https://github.com/webbit/webbit
[2]: http://java-websocket.org
