package org.kevoree.library.sky.minicloud.nodeType;

import org.kevoree.annotation.*;
import org.kevoree.library.defaultNodeTypes.JavaSENode;
import org.kevoree.library.sky.api.CommandMapper;
import org.kevoree.library.sky.api.KevoreeNodeManager;
import org.kevoree.library.sky.api.KevoreeNodeRunner;
import org.kevoree.library.sky.api.PlanningManager;
import org.kevoree.library.sky.api.nodeType.CloudNode;
import org.kevoree.library.sky.api.nodeType.KevoreeNodeRunnerFactory;
import org.kevoree.library.sky.api.nodeType.PaaSNode;
import org.kevoree.library.sky.minicloud.MiniCloudKevoreeNodeRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 15/09/11
 * Time: 16:26
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@Library(name = "SKY")
@DictionaryType({
        @DictionaryAttribute(name = "VMARGS", optional = true)
})
@NodeType
@PrimitiveCommands(value = {
        @PrimitiveCommand(name = CloudNode.ADD_NODE, maxTime = 120000)
}, values = {CloudNode.REMOVE_NODE})
public class MiniCloudNode extends JavaSENode implements CloudNode, PaaSNode {
    private static final Logger logger = LoggerFactory.getLogger(MiniCloudNode.class);

    private KevoreeNodeManager nodeManager;

    @Start
    @Override
    public void startNode() {
        super.startNode();
        nodeManager = new KevoreeNodeManager(new MiniCloudNodeRunnerFactory());
        kompareBean = new PlanningManager(this);
        mapper = new CommandMapper(nodeManager);
        mapper.setNodeType(this);
    }

    @Stop
    @Override
    public void stopNode() {
        logger.debug("stopping node type of {}", this.getNodeName());
        nodeManager.stop();
        super.stopNode();
    }

    public class MiniCloudNodeRunnerFactory implements KevoreeNodeRunnerFactory {
        @Override
        public KevoreeNodeRunner createKevoreeNodeRunner(String nodeName) {
            return new MiniCloudKevoreeNodeRunner(nodeName, MiniCloudNode.this);
        }
    }
}
