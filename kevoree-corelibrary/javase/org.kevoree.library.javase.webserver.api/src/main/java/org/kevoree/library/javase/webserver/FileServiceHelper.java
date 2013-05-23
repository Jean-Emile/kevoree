package org.kevoree.library.javase.webserver;

import org.kevoree.log.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: duke
 * Date: 21/11/11
 * Time: 23:04
 */
public class FileServiceHelper {

    static Properties mime = initMime();
    static URLHandler handler = new URLHandler();

    protected static Properties initMime() {
        Properties p = new Properties();
        try {
            p.load(FileServiceHelper.class.getClassLoader().getResourceAsStream("mime.properties"));
        } catch (Exception ex) {
            Log.debug("MIME map can't be loaded:" + ex);
        }
        return p;
    }

    public static byte[] convertStream(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int l;
        do {
            l = (in.read(buffer));
            if (l > 0) {
                out.write(buffer, 0, l);
            }
        } while (l > 0);
        return out.toByteArray();
    }

    public static boolean checkStaticFileFromDir(String index, AbstractPage origin, KevoreeHttpRequest request, KevoreeHttpResponse response, String baseDir) {
        String urlPattern = origin.getDictionary().get("urlpattern").toString();
        String file = "";
        String fileOptPath = handler.getLastParam(request.getUrl(), urlPattern);
        if (fileOptPath != null) {
            file = fileOptPath;
        }
        if (file.equals("") || file.equals("/")) {
            file = index;
        }
        Log.debug("Request rec for file " + file);
        File in = new File(baseDir + File.separator + file);
        if (in.exists() && in.isFile()) {
            try {
                FileInputStream ins = new FileInputStream(in);

                if (isRaw(request.getUrl())) {
                    response.setRawContent(convertStream(ins));
                } else {
                    response.setContent(new String(convertStream(ins), "UTF-8"));
                }
                response.getHeaders().put("Content-Type", (getHttpHeaderFromURL(request.getUrl())));

                ins.close();

                return true;
            } catch (Exception e) {
                Log.error("", e);
            }

        } else {
            Log.debug("Ressource not exist " + file);
        }
        return false;
    }


    public static boolean checkStaticFile(String index, AbstractPage origin, KevoreeHttpRequest request, KevoreeHttpResponse response) {

        String urlPattern = origin.getDictionary().get("urlpattern").toString();
        String file = "";
        String fileOptPath = handler.getLastParam(request.getUrl(), urlPattern);
        if (fileOptPath != null) {
            file = fileOptPath;
        }

        //String file = request.getUrl().substring(request.getUrl().lastIndexOf("/"));
        if ("".equals(file) || "/".equals(file)) {
            file = index;
        }

        if (file.startsWith("/")) {
            file = file.substring(1);
        }

        Log.debug("Request rec for file " + file);
        InputStream in = origin.getClass().getClassLoader().getResourceAsStream(file);
        if (in != null) {
            try {
                if (isRaw(request.getUrl())) {
                    response.setRawContent(convertStream(in));
                } else {
                    response.setContent(new String(convertStream(in), "UTF-8"));
                }
                response.getHeaders().put("Content-Type", (getHttpHeaderFromURL(request.getUrl())));
                return true;
            } catch (Exception e) {
                Log.error("", e);
            }

        } else {
            Log.debug("Ressource not exist " + file);
        }
        return false;
    }


    private static boolean isRaw(String url) {
        Log.debug("look extension file to know if the file '{}' is a raw file", url);
        return !(url.endsWith(".js") || url.endsWith(".html") || url.endsWith(".css") || url.endsWith(".jnlp"));
    }

    private static String getHttpHeaderFromURL(String url) {
        int dp = url.lastIndexOf('.');
        if (dp > 0) {
            return mime.getProperty(url.substring(dp + 1).toUpperCase());
        }
        return "text/html";
    }

}
