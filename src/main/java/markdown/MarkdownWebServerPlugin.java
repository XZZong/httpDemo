package markdown;

import fi.iki.elonen.NanoHTTPD;
import webserver.WebServerPlugin;
import org.pegdown.PegDownProcessor;

import java.io.*;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MarkdownWebServerPlugin implements WebServerPlugin {
    private final PegDownProcessor processor;
    private static final Logger LOG = Logger.getLogger(MarkdownWebServerPlugin.class.getName());

    public MarkdownWebServerPlugin() {
        this.processor = new PegDownProcessor();
    }

    @Override
    public boolean canServeUri(String uri, File rootDir) {
        File file = new File(rootDir, uri);
        return file.exists();
    }

    @Override
    public void initialize(Map<String, String> commandLineOptions) {
    }

    @Override
    public NanoHTTPD.Response serveFile(String uri, Map<String, String> headers, NanoHTTPD.IHTTPSession session, File file, String mimeType) {
        String markdownSource = readSource(file);
        byte[] bytes;
        try {
            bytes = this.processor.markdownToHtml(markdownSource).getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            MarkdownWebServerPlugin.LOG.log(Level.SEVERE, "encoding problem, responding nothing", e);
            bytes = new byte[0];
        }
        return markdownSource == null ? null : NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                NanoHTTPD.MIME_HTML, new ByteArrayInputStream(bytes), bytes.length);
    }

    private String readSource(File file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            MarkdownWebServerPlugin.LOG.log(Level.SEVERE, "could not read source", e);
            return null;
        }
    }
}
