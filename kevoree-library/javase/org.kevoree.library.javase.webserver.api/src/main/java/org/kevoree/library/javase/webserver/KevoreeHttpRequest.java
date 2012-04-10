package org.kevoree.library.javase.webserver;

import java.io.Serializable;
import java.util.HashMap;
import java.util.UUID;

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 14/10/11
 * Time: 08:41
 */
public class KevoreeHttpRequest implements Serializable {

	private String url = "";

	private String rawParams = "";

	private HashMap<String, String> resolvedParams = new HashMap<String, String>();

	public HashMap<String, String> getHeaders () {
		return headers;
	}

	public void setHeaders (HashMap<String, String> headers) {
		this.headers = headers;
	}

	private HashMap<String, String> headers = new HashMap<String, String>();

	private UUID tokenID = UUID.randomUUID();

	public UUID getTokenID () {
		return tokenID;
	}

	public String getUrl () {
		return url;
	}

	public void setUrl (String url) {
		this.url = url;
	}

	public HashMap<String, String> getResolvedParams () {
		return resolvedParams;
	}

	public void setResolvedParams (HashMap<String, String> resolvedParams) {
		this.resolvedParams = resolvedParams;
	}

	private byte[] rawBody = new byte[0];


	public byte[] getRawBody () {
		return rawBody;
	}

	public void setRawBody (byte[] rawBody) {
		this.rawBody = rawBody;
	}

	public String getRawParams () {
		return rawParams;
	}

	public void setRawParams(String rawParams) {
		this.rawParams = rawParams;
	}
}

