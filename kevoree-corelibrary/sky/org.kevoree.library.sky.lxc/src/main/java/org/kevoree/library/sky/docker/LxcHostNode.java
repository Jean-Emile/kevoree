package org.kevoree.library.sky.docker;

import org.kevoree.annotation.*;
import org.kevoree.library.sky.api.KevoreeNodeRunner;
import org.kevoree.library.sky.api.nodeType.AbstractHostNode;
import org.kevoree.library.sky.api.nodeType.HostNode;

/**
 * Created with IntelliJ IDEA.
 * User: jed
 * Date: 03/06/13
 * Time: 13:48
 * To change this template use File | Settings | File Templates.
 *
 * User namespace: missing
 */

@Library(name = "SKY")
@DictionaryType({
        @DictionaryAttribute(name = "OS", defaultValue = "ubuntu", vals = {"busybox", "debian", "fedora","opensuse","ubuntu"}),
})
@NodeType
@PrimitiveCommands(value = {
        @PrimitiveCommand(name = HostNode.ADD_NODE, maxTime = 120000)
}, values = {HostNode.REMOVE_NODE})
public class LxcHostNode extends AbstractHostNode {

    @Override
    public KevoreeNodeRunner createKevoreeNodeRunner(String nodeName) {
        return new LxcNodeRunner(nodeName,this);
    }

}
