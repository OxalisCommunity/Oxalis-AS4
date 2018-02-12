package no.difi.oxalis.as4.inbound;

import com.google.inject.Singleton;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Singleton
public class As4Servlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        FileOutputStream fos = new FileOutputStream(new File("output.mime"));
        String input = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
        IOUtils.write(input, fos, StandardCharsets.UTF_8);
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
