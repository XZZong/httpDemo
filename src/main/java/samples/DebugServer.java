package samples;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.util.ServerRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebugServer extends NanoHTTPD {
    public DebugServer() {
        super(8080);
    }

    private void listItem(StringBuilder sb, Map.Entry<String, ?> entry) {
        sb.append("<li><code><b>")
                .append(entry.getKey()).append("</b> = ").append(entry.getValue())
                .append("</code></li>");
    }

    private String unsortedList(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ul>");
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            listItem(sb, entry);
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private String toString(Map<String, ?> map) {
        if (map.size() == 0) {
            return "";
        }
        return unsortedList(map);
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, List<String>> decodedQueryParameters = decodeParameters(session.getQueryParameterString());

        // 显示session中的所有内容
        StringBuilder sb = new StringBuilder();
        sb.append("<html>").append("<head><title>Debug Server</title></head>")
                .append("<body>").append("<h1>Debug Server</h1>");
        sb.append("<p><blockquote><b>URI</b> = ").append(session.getUri()).append("<br />");
        sb.append("<b>Method</b> = ").append(session.getMethod()).append("</blockquote></p>");
        sb.append("<h3>Headers</h3><p><blockquote>").append(toString(session.getHeaders())).append("</blockquote></p>");
        sb.append("<h3>Parms</h3><p><blockquote>").append(toString(session.getParms())).append("</blockquote></p>");
        sb.append("<h3>Parms (multi values?)</h3><p><blockquote>").append(toString(decodedQueryParameters)).append("</blockquote></p>");

        try {
            Map<String, String> files = new HashMap<String, String>();
            session.parseBody(files);
            sb.append("<h3>Files</h3><p><blockquote>").append(toString(files)).append("</blockquote></p>");
        } catch (Exception e) {
            e.printStackTrace();
        }
        sb.append("</body>");
        sb.append("</html>");

        return newFixedLengthResponse(sb.toString());
    }

    public static void main(String[] args) {
        ServerRunner.run(DebugServer.class);
    }
}
