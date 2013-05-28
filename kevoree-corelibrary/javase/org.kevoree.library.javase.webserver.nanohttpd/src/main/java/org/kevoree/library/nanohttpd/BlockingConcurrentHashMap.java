package org.kevoree.library.nanohttpd;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 28/05/13
 * Time: 17:45
 * To change this template use File | Settings | File Templates.
 */
public class BlockingConcurrentHashMap<K,V> implements Map<K,V> {

    private final HashMap<K,V> backingMap = new HashMap<K,V>();

    private final Object lock = new Object();
    public V getAndWait(Object key) throws InterruptedException {
        synchronized(lock){
            V value = null;
            do{
                value = backingMap.get(key);

                if(value == null) lock.wait();

            }while(value == null);
            return value;
        }
    }

    @Override
    public int size() {
        return backingMap.size();  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isEmpty() {
        return backingMap.isEmpty();  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean containsKey(Object o) {
        return backingMap.containsKey(o);  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean containsValue(Object o) {
        return backingMap.containsValue(o);  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public V get(Object o) {
        return backingMap.get(o);  //To change body of implemented methods use File | Settings | File Templates.
    }

    public V put(K key, V val){
        synchronized(lock){
            V value = backingMap.put(key,val);
            lock.notifyAll();
            return value;
        }
    }

    @Override
    public V remove(Object o) {
        return backingMap.remove(o);  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void clear() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Set<K> keySet() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Collection<V> values() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }


}