package org.kevoree.library.sky.api.nodeType;

import org.kevoree.annotation.DictionaryAttribute;
import org.kevoree.annotation.DictionaryType;
import org.kevoree.annotation.NodeType;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 25/06/13
 * Time: 10:46
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@DictionaryType({
        @DictionaryAttribute(name = "login", optional = true),
        @DictionaryAttribute(name = "credentials", optional = true),
        @DictionaryAttribute(name = "endpoints", optional = true)
})
@NodeType
public interface ProxyNode {

}
