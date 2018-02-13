package no.difi.oxalis.as4.inbound;

import com.google.inject.Singleton;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.soap.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.StringTokenizer;

@Singleton
public class As4Servlet extends HttpServlet {

    private MessageFactory messageFactory = null;

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        try {
            messageFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
        }
        catch (SOAPException ex) {
            throw new ServletException("Unable to create message factory" + ex.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            SOAPMessage soapMessage = messageFactory.createMessage(getHeaders(req), req.getInputStream());

            Iterator<AttachmentPart> attachments = soapMessage.getAttachments();
            while (attachments.hasNext()) {
                AttachmentPart attachment = attachments.next();
                String s = IOUtils.toString(attachment.getRawContent(), StandardCharsets.UTF_8);
                System.out.println("Attachment content: \n"+s);
            }
        } catch (SOAPException e) {
            throw new RuntimeException("Cannot create message factory", e);
        }

        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private MimeHeaders getHeaders(HttpServletRequest httpServletRequest) {
        Enumeration<?> enumeration = httpServletRequest.getHeaderNames();
        MimeHeaders headers = new MimeHeaders();
        while (enumeration.hasMoreElements()) {
            String headerName = (String) enumeration.nextElement();
            String headerValue = httpServletRequest.getHeader(headerName);
            StringTokenizer values = new StringTokenizer(headerValue, ",");
            while (values.hasMoreTokens()) {
                headers.addHeader(headerName, values.nextToken().trim());
            }
        }
        return headers;
    }
}
