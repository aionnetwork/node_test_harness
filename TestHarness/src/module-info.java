module aion.node.harness {
    requires org.apache.commons.io;
    requires org.apache.commons.codec;
    requires offline.signer;
    requires gson;
    requires ed25519;
    requires java.net.http;

    exports org.aion.harness.statistics;
    exports org.aion.harness.result;
    exports org.aion.harness.kernel;
    exports org.aion.harness.main;
    exports org.aion.harness.main.impl;
    exports org.aion.harness.main.event;
    exports org.aion.harness.main.types;
    exports org.aion.harness.main.util;
}
