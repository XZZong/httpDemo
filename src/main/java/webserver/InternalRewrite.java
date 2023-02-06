package webserver;

import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayInputStream;
import java.util.Map;

public class InternalRewrite extends NanoHTTPD.Response {
    private final String uri;
    private final Map<String, String> header;

    protected InternalRewrite(Map<String, String> header, String uri) {
        super(Status.OK, NanoHTTPD.MIME_HTML, new ByteArrayInputStream(new byte[0]), 0);
        this.header = header;
        this.uri = uri;
    }

    public Map<String, String> getHeader() {
        return header;
    }

    public String getUri() {
        return uri;
    }
}
