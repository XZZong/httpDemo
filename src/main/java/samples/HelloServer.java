package samples;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.util.ServerRunner;

import java.util.Map;
import java.util.logging.Logger;

public class HelloServer extends NanoHTTPD {
    private static final Logger LOG = Logger.getLogger(HelloServer.class.getName());
    public HelloServer() {
        super(8080);
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();
        HelloServer.LOG.info(method + " '" + uri + "' ");

        StringBuilder msg = new StringBuilder("<html><body><h1>Hello server</h1>\n");
        Map<String, String> params = session.getParms();
        if (params.get("username") == null) {
            msg.append("<form action='?' method='get'>\n")
                    .append("  <p>Your name: <input type='text' name='username'></p>\n" + "</form>\n");
        }
        else {
            msg.append("<p>Hello, ").append(params.get("username")).append("!</p>");
        }
        msg.append("</body></html>\n");

        return newFixedLengthResponse(msg.toString());
    }

    public static void main(String[] args) {
        ServerRunner.run(HelloServer.class);
    }
}
