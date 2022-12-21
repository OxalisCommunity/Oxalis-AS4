package network.oxalis.as4.util;

import static network.oxalis.as4.util.Constants.BASIC_128_GCM_SHA_256;
import static network.oxalis.as4.util.Constants.BASIC_128_GCM_SHA_256_MGF_SHA_256;
import static network.oxalis.as4.util.Constants.OXALIS_ALGORITHM_NAMESPACE;
import static org.apache.wss4j.common.WSS4JConstants.MGF_SHA256;

import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.Bus;
import org.apache.cxf.ws.policy.AssertionBuilderRegistry;
import org.apache.cxf.ws.security.policy.custom.AlgorithmSuiteLoader;
import org.apache.neethi.Assertion;
import org.apache.neethi.Policy;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.policy.SPConstants;
import org.apache.wss4j.policy.model.AbstractSecurityAssertion;
import org.apache.wss4j.policy.model.AlgorithmSuite;


// Based on from CEF e-delivery Domibus
// https://ec.europa.eu/cefdigital/code/projects/EDELIVERY/repos/domibus/browse/Domibus-MSH/src/main/java/eu/domibus/ebms3/security/custom/DomibusAlgorithmSuiteLoader.java
@Slf4j
public class OxalisAlgorithmSuiteLoader implements AlgorithmSuiteLoader {

    public static final String AES128_GCM_ALGORITHM = "http://www.w3.org/2009/xmlenc11#aes128-gcm";

    public OxalisAlgorithmSuiteLoader(final Bus bus) {
        bus.setExtension(this, AlgorithmSuiteLoader.class);
        register(bus);
    }

    public AlgorithmSuite getAlgorithmSuite(final Bus bus, final SPConstants.SPVersion version, final Policy nestedPolicy) {
        return new OxalisAlgorithmSuite(version, nestedPolicy);
    }

    private void register(final Bus bus) {
        final AssertionBuilderRegistry reg = bus.getExtension(AssertionBuilderRegistry.class);
        if (reg != null) {
            reg.registerBuilder(new OxalisAssertionBuilder());
        }
    }

    public static class OxalisAlgorithmSuite extends AlgorithmSuite {

        static {
            ALGORITHM_SUITE_TYPES.put(
                    BASIC_128_GCM_SHA_256,
                    new AlgorithmSuiteType(
                            BASIC_128_GCM_SHA_256,
                            SPConstants.SHA256,
                            OxalisAlgorithmSuiteLoader.AES128_GCM_ALGORITHM,
                            SPConstants.KW_AES128,
                            SPConstants.KW_RSA_OAEP,
                            SPConstants.P_SHA1_L128,
                            SPConstants.P_SHA1_L128,
                            128, 128, 128, 256, 1024, 4096
                    )
            );

            ALGORITHM_SUITE_TYPES.put(
                    BASIC_128_GCM_SHA_256_MGF_SHA_256,
                    new AlgorithmSuiteType(
                            BASIC_128_GCM_SHA_256_MGF_SHA_256,
                            SPConstants.SHA256,
                            OxalisAlgorithmSuiteLoader.AES128_GCM_ALGORITHM,
                            SPConstants.KW_AES128,
                            WSS4JConstants.KEYTRANSPORT_RSAOAEP_XENC11,
                            SPConstants.P_SHA1_L128,
                            SPConstants.P_SHA1_L128,
                            128, 128, 128, 256, 1024, 4096
                    )
            );
            ALGORITHM_SUITE_TYPES.get(BASIC_128_GCM_SHA_256)
                .setAsymmetricSignature("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
            AlgorithmSuiteType algorithmSuiteType = ALGORITHM_SUITE_TYPES.get(BASIC_128_GCM_SHA_256_MGF_SHA_256);
            algorithmSuiteType.setMGFAlgo(MGF_SHA256);
            algorithmSuiteType.setEncryptionDigest(SPConstants.SHA256);
            algorithmSuiteType.setAsymmetricSignature("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
        }

        OxalisAlgorithmSuite(final SPConstants.SPVersion version, final Policy nestedPolicy) {
            super(version, nestedPolicy);
        }

        @Override
        protected AbstractSecurityAssertion cloneAssertion(final Policy nestedPolicy) {
            return new OxalisAlgorithmSuite(this.getVersion(), nestedPolicy);
        }

        @Override
        protected void parseCustomAssertion(final Assertion assertion) {
            final String assertionName = assertion.getName().getLocalPart();
            final String assertionNamespace = assertion.getName().getNamespaceURI();
            if (!OXALIS_ALGORITHM_NAMESPACE.equals(assertionNamespace)) {
                return;
            }

            if (BASIC_128_GCM_SHA_256.equals(assertionName)) {
                setAlgorithmSuiteType(ALGORITHM_SUITE_TYPES.get(BASIC_128_GCM_SHA_256));
                getAlgorithmSuiteType().setNamespace(assertionNamespace);
            } else if (BASIC_128_GCM_SHA_256_MGF_SHA_256.equals(assertionName)) {
                setAlgorithmSuiteType(ALGORITHM_SUITE_TYPES.get(BASIC_128_GCM_SHA_256_MGF_SHA_256));
                getAlgorithmSuiteType().setNamespace(assertionNamespace);
            }
        }
    }
}