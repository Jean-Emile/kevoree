package org.kevoree.library.javase.webserver;

import org.kevoree.log.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 06/05/13
 * Time: 13:53
 *
 * @author Erwan Daubert
 * @version 1.0
 */
public class URLHandler {
    String localURLPattern = "/";
    private List<String> paramNames;

    public String getLocalURLPattern() {
        return localURLPattern;
    }

    public void initRegex(String pattern) {
        paramNames = new ArrayList<String>();
        Matcher m = Pattern.compile("\\{(\\w+)\\}").matcher(pattern);
        StringBuffer sb = new StringBuffer();
        StringBuilder rsb = new StringBuilder();
        while (m.find()) {
            paramNames.add(m.group(1));
            rsb.replace(0, rsb.length(), m.group(1));
            m.appendReplacement(sb, "(\\\\w+)");
        }
        m.appendTail(sb);
        localURLPattern = sb.toString().replaceAll("\\*{2,}", ".*").replaceAll("[^.]\\*+", "/?[^/]*");
    }

    public boolean precheck(String url) {
        Matcher m = Pattern.compile(localURLPattern).matcher(url);
        return m.matches();
    }

    public KevoreeHttpRequest check(KevoreeHttpRequest url) {

        if (Log.DEBUG) {
            Log.debug("Try to check => " + localURLPattern + " - " + url);
        }
        Matcher m = Pattern.compile(localURLPattern).matcher(url.getUrl());
        if (m.matches()) {
            Map<String, String> params = new HashMap<String, String>();
            params.putAll(url.getResolvedParams());

            for (int i = 0; i < m.groupCount();i++) {
                String param = m.group(i+1);
                params.put(paramNames.get(i), param);
            }
            url.setResolvedParams(params);
            return url;
        } else {
            return null;
        }
    }

    public String getLastParam(String url, String urlPattern) {
        String purlPattern = urlPattern;
        Matcher m = Pattern.compile("\\{(\\w+)\\}").matcher(purlPattern);
        while (m.find()) {
            purlPattern = purlPattern.replace("{" + m.group(1) + "}", ".*");
        }

        String regexText = purlPattern.replaceAll("\\*{2,}", "(.*)").replaceAll("[^.]\\*+", "(/?[^/]*)");
        m = Pattern.compile(regexText).matcher(url);
        String param = null;
        if (m.matches()) {
            param = url.substring(m.start(m.groupCount()), m.end(m.groupCount()));
        }
        return param;
    }
}
