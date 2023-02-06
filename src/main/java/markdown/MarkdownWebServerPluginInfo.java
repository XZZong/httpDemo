package markdown;

import webserver.WebServerPlugin;
import webserver.WebServerPluginInfo;

public class MarkdownWebServerPluginInfo implements WebServerPluginInfo {
    @Override
    public String[] getIndexFilesForMimeType(String mime) {
        String[] indexFiles = new String[1];
        indexFiles[0] = "index.md";
        return indexFiles;
    }

    @Override
    public String[] getMimeTypes() {
        String[] mimeTypes = new String[1];
        mimeTypes[0] = "text/markdown";
        return mimeTypes;
    }

    @Override
    public WebServerPlugin getWebServerPlugin(String mimeType) {
        return new MarkdownWebServerPlugin();
    }
}
