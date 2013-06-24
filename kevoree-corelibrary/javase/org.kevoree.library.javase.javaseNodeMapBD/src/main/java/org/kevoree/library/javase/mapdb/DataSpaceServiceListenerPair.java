package org.kevoree.library.javase.mapdb;

import org.kevoree.api.dataspace.DataSpaceListener;

/**
 * Created by duke on 24/06/13.
 */
public class DataSpaceServiceListenerPair {

    private DataSpaceListener listener = null;

    private String query = null;

    public DataSpaceListener getListener() {
        return listener;
    }

    public DataSpaceServiceListenerPair setListener(DataSpaceListener listener) {
        this.listener = listener;
        return this;
    }

    public String getQuery() {
        return query;
    }

    public DataSpaceServiceListenerPair setQuery(String query) {
        this.query = query;
        return this;
    }
}
