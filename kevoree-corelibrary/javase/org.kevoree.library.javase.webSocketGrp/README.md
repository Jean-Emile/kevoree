# WebSocketGroup documentation

This Kevoree Group goal is to provide a WebSocket implementation between push/pull model updates

## Node start
When a node starts, this group creates a Webbit socket server that is capable of handling 4 different kinds of requests :

*   host:port/ **push**

*   host:port/ **pull**

*   host:port/ **push/zip**

*   host:port/ **pull/zip**

## Push process
When a push is requested on a node. This group compresses the given model and try to send it to the targeted node on :

*   ws://host:port/ **push/zip**

The targeted node will then process the model in the **pushCompressedHandler**  
```java
ByteArrayInputStream bais = new ByteArrayInputStream(msg);  
ContainerRoot model = KevoreeXmiHelper.$instance.loadCompressedStream(bais);  
updateLocalModel(model);
```

## Pull process
When a pull is requested on a node. This group asks the targeted node via :

* ws://host:port/ **pull/zip**

The targeted node will then process the model in the **pullCompressedHandler**  
```java
ByteArrayOutputStream output = new ByteArrayOutputStream();  
KevoreeXmiHelper.$instance.saveCompressedStream(output, getModelService().getLastModel());  
connection.send(output.toByteArray());
```
