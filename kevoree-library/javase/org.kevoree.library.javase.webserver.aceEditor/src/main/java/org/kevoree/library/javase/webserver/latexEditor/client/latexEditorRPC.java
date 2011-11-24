package org.kevoree.library.javase.webserver.latexEditor.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.*;
import com.google.gwt.user.client.Window;

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 24/11/11
 * Time: 19:44
 * To change this template use File | Settings | File Templates.
 */
public class latexEditorRPC {

    public static void callForCompile(latexEditorFileExplorer explorer) {
        String url = GWT.getModuleBaseURL() + "compile?file=" + explorer.getSelectedFilePath();
        RequestBuilder builder = new RequestBuilder(RequestBuilder.GET, URL.encode(url));
        try {
            builder.sendRequest(null, new RequestCallback() {
                public void onError(Request request, Throwable exception) {
                    Window.alert("Error while connecting to server");
                }
                public void onResponseReceived(Request request, Response response) {}
            });

        } catch (Exception e) {
            Window.alert("Error while connecting to server");
        }
    }

}
