package org.kevoree.library.javase.mapdb;

import org.kevoree.annotation.*;
import org.kevoree.api.dataspace.DataSpaceListener;
import org.kevoree.api.dataspace.DataSpaceService;
import org.kevoree.library.defaultNodeTypes.JavaSENode;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;

/**
 * Created by duke on 24/06/13.
 */
@Library(name = "JavaSE")
@NodeType
public class JavaSENodeMapDB extends JavaSENode implements DataSpaceService {

    ConcurrentNavigableMap treeMap = null;
    private DB db = null;
    private List<DataSpaceServiceListenerPair> listeners = new ArrayList<DataSpaceServiceListenerPair>();

    @Start
    public void startNode() {
        db = DBMaker.newFileDB(new File("kevoreeDataSpace")).closeOnJvmShutdown().make();
        treeMap = db.getTreeMap("kevoreeDataSpace");
        super.setDataSpaceService(this);
        super.startNode();
    }

    @Stop
    public void stopNode() {
        super.stopNode();
        super.setDataSpaceService(null);
        db.close();
    }

    @Override
    public void putData(String path, Object data) {



        treeMap.put(path, data);
    }

    @Override
    public Object getData(String path) {
        return treeMap.get(path);
    }

    @Override
    public void registerListener(String pathQuery, DataSpaceListener listener) {
        listeners.add(new DataSpaceServiceListenerPair().setListener(listener).setQuery(pathQuery));
    }

    @Override
    public void removeListener(DataSpaceService listener) {
        List<DataSpaceServiceListenerPair> temp = new ArrayList<DataSpaceServiceListenerPair>();
        for (DataSpaceServiceListenerPair p : listeners) {
            if (p.equals(listener)) {
                temp.add(p);
            }
        }
        listeners.removeAll(temp);
    }
}
