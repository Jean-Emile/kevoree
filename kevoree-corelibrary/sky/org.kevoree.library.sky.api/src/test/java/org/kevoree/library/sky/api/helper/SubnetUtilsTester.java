package org.kevoree.library.sky.api.helper;

import org.junit.Test;
import org.kevoree.library.sky.api.nodeType.helper.SubnetUtils;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 07/03/13
 * Time: 13:41
 *
 * @author Erwan Daubert
 * @version 1.0
 */
public class SubnetUtilsTester {

    @Test
    public void testToCidrNotation() {
        SubnetUtils utils = new SubnetUtils("10.0.0.0", "255.255.255.0");
        System.out.println(utils.getInfo().getAddress());
        System.out.println(utils.getInfo().getNetmask());
        System.out.println(utils.getInfo().getCidrSignature());
    }

    @Test
    public void testFromCidrNotation() {
        SubnetUtils utils = new SubnetUtils("10.0.0.0/24");
        System.out.println(utils.getInfo().getAddress());
        System.out.println(utils.getInfo().getNetmask());
        System.out.println(utils.getInfo().getCidrSignature());
    }

}
