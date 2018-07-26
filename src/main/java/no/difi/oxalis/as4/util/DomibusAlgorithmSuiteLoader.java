//package no.difi.oxalis.as4.util;
//
//import org.apache.cxf.Bus;
//import org.apache.cxf.ws.policy.AssertionBuilderRegistry;
//import org.apache.cxf.ws.policy.builder.primitive.PrimitiveAssertion;
//import org.apache.cxf.ws.policy.builder.primitive.PrimitiveAssertionBuilder;
//import org.apache.cxf.ws.security.policy.custom.AlgorithmSuiteLoader;
//import org.apache.neethi.Assertion;
//import org.apache.neethi.AssertionBuilderFactory;
//import org.apache.neethi.Policy;
//import org.apache.neethi.builders.xml.XMLPrimitiveAssertionBuilder;
//import org.apache.wss4j.common.WSS4JConstants;
//import org.apache.wss4j.dom.WSConstants;
//import org.apache.wss4j.policy.SPConstants;
//import org.apache.wss4j.policy.model.AbstractSecurityAssertion;
//import org.apache.wss4j.policy.model.AlgorithmSuite;
//import org.w3c.dom.Element;
//
//import javax.xml.namespace.QName;
//import java.util.HashMap;
//import java.util.Map;
//
//
///**
// * This class implements a custom {@link org.apache.cxf.ws.security.policy.custom.AlgorithmSuiteLoader} in order to enable the domibus gateway to support:
// * <ol>
// * <li>the gcm variant of the aes algorithm</li>
// * <li>a key transport algorithm wihtout SHA1 dependencies</li> *
// * </ol>
// * NOTE: GCM is supported by Apache CXF via {@link org.apache.cxf.ws.security.policy.custom.DefaultAlgorithmSuiteLoader} but at time of writing (15.10.2014)
// * the corresponding AlgorithmSuites do not support digest Algorithms other than SHA1.
// *
// * @author Christian Koch, Stefan Mueller
// */
//public class DomibusAlgorithmSuiteLoader implements AlgorithmSuiteLoader {
//
//    public static final String E_DELIVERY_ALGORITHM_NAMESPACE = "http://e-delivery.eu/custom/security-policy";
//
//    public static final String MGF1_KEY_TRANSPORT_ALGORITHM = "http://www.w3.org/2009/xmlenc11#rsa-oaep";
//    public static final String AES128_GCM_ALGORITHM = "http://www.w3.org/2009/xmlenc11#aes128-gcm";
//    public static final String BASIC_128_GCM_SHA_256 = "Basic128GCMSha256";
//    public static final String BASIC_128_GCM_SHA_256_MGF_SHA_256 = "Basic128GCMSha256MgfSha256";
//
//
//    public DomibusAlgorithmSuiteLoader(final Bus bus) {
//        bus.setExtension(this, AlgorithmSuiteLoader.class);
//    }
//
//
//    public AlgorithmSuite getAlgorithmSuite(final Bus bus, final SPConstants.SPVersion version, final Policy nestedPolicy) {
//        final AssertionBuilderRegistry reg = bus.getExtension(AssertionBuilderRegistry.class);
//        if (reg != null) {
//            final Map<QName, Assertion> assertions = new HashMap<QName, Assertion>();
//            QName qName = new QName(E_DELIVERY_ALGORITHM_NAMESPACE, BASIC_128_GCM_SHA_256);
//            assertions.put(qName, new PrimitiveAssertion(qName));
//            qName = new QName(E_DELIVERY_ALGORITHM_NAMESPACE, BASIC_128_GCM_SHA_256_MGF_SHA_256);
//            assertions.put(qName, new PrimitiveAssertion(qName));
//
//            reg.registerBuilder(new PrimitiveAssertionBuilder(assertions.keySet()) {
//                public Assertion build(final Element element, final AssertionBuilderFactory fact) {
//                    if (XMLPrimitiveAssertionBuilder.isOptional(element)
//                            || XMLPrimitiveAssertionBuilder.isIgnorable(element)) {
//                        return super.build(element, fact);
//                    }
//                    final QName q = new QName(element.getNamespaceURI(), element.getLocalName());
//                    return assertions.get(q);
//                }
//            });
//        }
//        return new DomibusAlgorithmSuiteLoader.DomibusAlgorithmSuite(version, nestedPolicy);
//    }
//
//    public static class DomibusAlgorithmSuite extends AlgorithmSuite {
//
//        static {
//            ALGORITHM_SUITE_TYPES.put(
//                    BASIC_128_GCM_SHA_256,
//                    new AlgorithmSuiteType(
//                            BASIC_128_GCM_SHA_256,
//                            SPConstants.SHA256,
//                            DomibusAlgorithmSuiteLoader.AES128_GCM_ALGORITHM,
//                            SPConstants.KW_AES128,
//                            SPConstants.KW_RSA_OAEP,
//                            SPConstants.P_SHA1_L128,
//                            SPConstants.P_SHA1_L128,
//                            128, 128, 128, 256, 1024, 4096
//                    )
//            );
//
//            ALGORITHM_SUITE_TYPES.put(
//                    BASIC_128_GCM_SHA_256_MGF_SHA_256,
//                    new AlgorithmSuiteType(
//                            BASIC_128_GCM_SHA_256_MGF_SHA_256,
//                            SPConstants.SHA256,
//                            DomibusAlgorithmSuiteLoader.AES128_GCM_ALGORITHM,
//                            SPConstants.KW_AES128,
//                            WSS4JConstants.KEYTRANSPORT_RSAOAEP_XENC11,
//                            SPConstants.P_SHA1_L128,
//                            SPConstants.P_SHA1_L128,
//                            128, 128, 128, 256, 1024, 4096
//                    )
//            );
//            ALGORITHM_SUITE_TYPES.get(BASIC_128_GCM_SHA_256_MGF_SHA_256).setMGFAlgo(WSConstants.MGF_SHA256);
//            ALGORITHM_SUITE_TYPES.get(BASIC_128_GCM_SHA_256_MGF_SHA_256).setEncryptionDigest(SPConstants.SHA256);
//        }
//
//        DomibusAlgorithmSuite(final SPConstants.SPVersion version, final Policy nestedPolicy) {
//            super(version, nestedPolicy);
//        }
//
//        @Override
//        protected AbstractSecurityAssertion cloneAssertion(final Policy nestedPolicy) {
//            return new DomibusAlgorithmSuiteLoader.DomibusAlgorithmSuite(this.getVersion(), nestedPolicy);
//        }
//
//        @Override
//        protected void parseCustomAssertion(final Assertion assertion) {
//            final String assertionName = assertion.getName().getLocalPart();
//            final String assertionNamespace = assertion.getName().getNamespaceURI();
//            if (!DomibusAlgorithmSuiteLoader.E_DELIVERY_ALGORITHM_NAMESPACE.equals(assertionNamespace)) {
//                return;
//            }
//
//            if (BASIC_128_GCM_SHA_256.equals(assertionName)) {
//                setAlgorithmSuiteType(ALGORITHM_SUITE_TYPES.get(BASIC_128_GCM_SHA_256));
//                getAlgorithmSuiteType().setNamespace(assertionNamespace);
//            } else if (BASIC_128_GCM_SHA_256_MGF_SHA_256.equals(assertionName)) {
//                setAlgorithmSuiteType(ALGORITHM_SUITE_TYPES.get(BASIC_128_GCM_SHA_256_MGF_SHA_256));
//                getAlgorithmSuiteType().setNamespace(assertionNamespace);
//            }
//        }
//    }
//}