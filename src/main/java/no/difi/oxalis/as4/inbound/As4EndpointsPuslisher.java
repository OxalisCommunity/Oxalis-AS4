package no.difi.oxalis.as4.inbound;

import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;

public interface As4EndpointsPuslisher {

    EndpointImpl publish(Bus bus);
}
