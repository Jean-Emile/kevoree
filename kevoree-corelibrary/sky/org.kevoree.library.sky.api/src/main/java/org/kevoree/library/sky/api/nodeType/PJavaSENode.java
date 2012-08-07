package org.kevoree.library.sky.api.nodeType;

import org.kevoree.annotation.Library;
import org.kevoree.annotation.NodeType;
import org.kevoree.library.defaultNodeTypes.JavaSENode;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 14/12/11
 * Time: 13:43
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@Library(name = "SKY")
@NodeType
public class PJavaSENode extends JavaSENode implements PaaSNode {
	@Override
	protected boolean isDaemon () {
		return true;
	}
}
