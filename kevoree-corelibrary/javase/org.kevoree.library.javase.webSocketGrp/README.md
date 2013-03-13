# WebSocketGroup documentation

This Kevoree Group aim to provide a WebSocket implementation of fragment communications

## What could I use ?
In this module you have 4 different kinds of group. 
Each one has its own purpose and works with WebSocket API such as [Webbit] [1] & [Java_WebSocket] [2]

## The 4 different groups
* **WebSocketGroup**: starts a server & a client on each fragment meaning that if a node is behind a router you won't probably be able to push/pull anything from it
* **WebSocketGroupMasterServer**: requires that you specify **one** (and only one) master server within your group nodes making this group a fully centralized network. Each other nodes will connect themselves to this master server.
								  This group disallow push/pull requests on other nodes than the **master server one** throwing exceptions back at you if you dare to try.
* **WebSocketGroupEchoer**: same as **WebSocketGroupMasterServer** but this one allows you (or your puppy who knows ?) to push/pull on every nodes
* **WebSocketGroupQueuer**: same as **WebSocketGroupEchoer** but this one is able to recognize that some nodes from its group are not yet connected to him so it will keep a **waitingQueue** up-to-date and therefore echo back models when those waiting nodes will initiate a connection

## What about the first one : *WebSocketGroup*
### Node start
When a node starts, this group creates a Webbit socket server that is capable of handling 4 different kinds of requests :

*   host:port/ **push**
*   host:port/ **pull**
*   host:port/ **push/zip**
*   host:port/ **pull/zip**

### Push process
When a push is requested on a node. This group compresses the given model and try to send it to the targeted node on :

*   ws://host:port/ **push/zip**

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

* ws://host:port/ **pull/zip**

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
TODO

### Push process
TODO

### Pull process
TODO

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
