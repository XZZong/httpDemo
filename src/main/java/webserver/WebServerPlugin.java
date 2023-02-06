package webserver;

import fi.iki.elonen.NanoHTTPD;

import java.io.File;
import java.util.Map;

public interface WebServerPlugin {
    boolean canServeUri(String uri, File rootDir);

    void initialize(Map<String, String> commandLineOptions);

    NanoHTTPD.Response serveFile(String uri, Map<String, String> headers, NanoHTTPD.IHTTPSession session, File file, String mimeType);
}
