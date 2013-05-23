package org.kevoree.library.webserver.internal;

import java.util.Stack;

/**
 * Created with IntelliJ IDEA.
 * User: duke
 * Date: 23/05/13
 * Time: 15:59
 */
public class IDGenerator {

    Stack<Integer> freeIDS = new Stack<Integer>();

    public IDGenerator(Integer size) {
        for (int i = 0; i < size; i++) {
            freeIDS.push(i);
        }
    }

    public Integer getID() {
        return freeIDS.pop();
    }

    public void restack(Integer id) {
        freeIDS.push(id);
    }


}
