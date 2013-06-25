package org.kevoree.library.sky.api.nodeType;

import org.kevoree.annotation.*;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 14/12/11
 * Time: 10:12
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@DictionaryType({
		@DictionaryAttribute(name = "ARCH", optional = true),
		@DictionaryAttribute(name = "RAM", optional = true),
		// GB, MB, KB is allowed, N/A means undefined
		@DictionaryAttribute(name = "CPU_CORE", optional = true),
		// number of allowed cores, N/A means undefined
		@DictionaryAttribute(name = "CPU_FREQUENCY", optional = true),
		// in MHz, N/A means undefined
		@DictionaryAttribute(name = "OS", optional = true),
		// number of allowed cores, N/A means undefined
		@DictionaryAttribute(name = "DISK_SIZE", optional = true)
		// the disk size allowed/available for the node (GB, MB, KB is allowed), undefined value can be set using N/A
})
@PrimitiveCommands(value = {}, values = {CloudNode.REMOVE_NODE, CloudNode.ADD_NODE})
@NodeType
public interface CloudNode {
    public static final String REMOVE_NODE = "RemoveNode";
    public static final String ADD_NODE = "AddNode";
}
