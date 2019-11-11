package org.aion.harness.unit;

import static org.junit.Assert.assertTrue;

import java.io.File;
import org.aion.harness.integ.resources.TestHelper;
import org.aion.harness.kernel.Kernel;
import org.aion.harness.main.Network;
import org.aion.harness.main.impl.JavaNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class KernelTest {
    private static final File KEYSTORE_PATH = new File(new File(TestHelper.EXPECTED_KERNEL_ROOT, TestHelper.DEFAULT_NETWORK.string()), "keystore");
    private JavaNode node;

    @Before
    public void setup() throws Exception {
        this.node = (JavaNode) TestHelper.configureDefaultLocalNodeForNetwork(Network.CUSTOM);
        assertTrue(this.node.initialize().isSuccess());
    }

    @Test
    public void testCreateAccount() throws Exception {
        Kernel kernel = this.node.getKernel();
        Assert.assertNotNull(kernel.createNewAccountInKeystore("password"));
        kernel.clearKeystore();
    }

    @Test
    public void testClearKeystore() throws Exception {
        this.node.getKernel().clearKeystore();
        Assert.assertFalse(KEYSTORE_PATH.exists());
    }
}
