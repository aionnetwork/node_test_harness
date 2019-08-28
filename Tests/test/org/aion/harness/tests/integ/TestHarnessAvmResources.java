package org.aion.harness.tests.integ;

import org.aion.vm.AvmResources;
import org.aion.vm.AvmUtility;

public class TestHarnessAvmResources {
    private static final String AION_ROOT_DIR = "/home/nick/Desktop/node_test_harness/Tests/aion/";
    private static final String VERSION1_DIR = AION_ROOT_DIR + "version1/";
    private static final String VERSION2_DIR = AION_ROOT_DIR + "version2/";
    private static final String RESOURCE_FACTORY_PACKAGE_NAME_V1 = "org.aion.avm.version1.";
    private static final String RESOURCE_FACTORY_PACKAGE_NAME_V2 = "org.aion.avm.version2.";
    private static final String VERSION_MODULE_JAR_V1 = AION_ROOT_DIR + "modAvmVersion1.jar";
    private static final String VERSION_MODULE_JAR_V2 = AION_ROOT_DIR + "modAvmVersion2.jar";
    private static final String CORE_JAR_V1 = VERSION1_DIR + "org-aion-avm-core-v1.jar";
    private static final String CORE_JAR_V2 = VERSION2_DIR + "org-aion-avm-core-v2.jar";
    private static final String API_JAR_V1 = VERSION1_DIR + "org-aion-avm-api-v1.jar";
    private static final String API_JAR_V2 = VERSION2_DIR + "org-aion-avm-api-v2.jar";
    private static final String RT_JAR_V1 = VERSION1_DIR + "org-aion-avm-rt-v1.jar";
    private static final String RT_JAR_V2 = VERSION2_DIR + "org-aion-avm-rt-v2.jar";
    private static final String USERLIB_JAR_V1 = VERSION1_DIR + "org-aion-avm-userlib-v1.jar";
    private static final String USERLIB_JAR_V2 = VERSION2_DIR + "org-aion-avm-userlib-v2.jar";
    private static final String TOOLING_JAR_V1 = VERSION1_DIR + "org-aion-avm-tooling-v1.jar";
    private static final String TOOLING_JAR_V2 = VERSION2_DIR + "org-aion-avm-tooling-v2.jar";

    public static AvmResources resourcesForVersion1() {
        return new AvmResources(RESOURCE_FACTORY_PACKAGE_NAME_V1, VERSION_MODULE_JAR_V1, CORE_JAR_V1, API_JAR_V1, RT_JAR_V1, USERLIB_JAR_V1, TOOLING_JAR_V1);
    }

    public static AvmResources resourcesForVersion2() {
        return new AvmResources(RESOURCE_FACTORY_PACKAGE_NAME_V2, VERSION_MODULE_JAR_V2, CORE_JAR_V2, API_JAR_V2, RT_JAR_V2, USERLIB_JAR_V2, TOOLING_JAR_V2);
    }

    public static AvmUtility avmUtility() throws Exception {
        return new AvmUtility(resourcesForVersion1(), resourcesForVersion2());
    }
}
