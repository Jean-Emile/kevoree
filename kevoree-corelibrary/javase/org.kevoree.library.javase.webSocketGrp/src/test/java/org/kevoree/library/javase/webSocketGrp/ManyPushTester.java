package org.kevoree.library.javase.webSocketGrp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.kevoree.ContainerRoot;
import org.kevoree.framework.KevoreeXmiHelper;

public class ManyPushTester {
	private static final byte PUSH = 1;

	public static void main(String[] args) throws FileNotFoundException {
		String path = "resources/model0.kevm";
		// TODO use your own model file & path
		File fModel = new File(path);
		
		FileInputStream fis = new FileInputStream(fModel);
		
		// serialize model into an OutputStream
		ContainerRoot model = KevoreeXmiHelper.instance$.loadStream(fis);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		KevoreeXmiHelper.instance$.saveCompressedStream(baos, model);
		
		final byte[] data = new byte[baos.size() + 1];
		byte[] serializedModel = baos.toByteArray();
		data[0] = PUSH;
		for (int i = 1; i < data.length; i++) {
			data[i] = serializedModel[i - 1];
		}
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				System.out.println("Gonna start pushing models for 5 seconds");
				long stopTime = System.currentTimeMillis()+5000;
				int count = 0;
				while (System.currentTimeMillis() < stopTime) {
					// push model
					try {
						WebSocketClient client = new WebSocketClient(URI.create("ws://localhost:8010/")) {
							@Override
							public void onOpen(ServerHandshake arg0) {}
							@Override
							public void onMessage(String arg0) {}
							@Override
							public void onError(Exception arg0) {}
							@Override
							public void onClose(int arg0, String arg1, boolean arg2) {}
						};
						
						client.connectBlocking();
						client.send(data);
						client.close();
						
						count++;
						
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
				}
				System.out.println("Ok I'm done pushing models, I did it "+count+" times!");
			}
		}).start();
	}
}
