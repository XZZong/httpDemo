package webserver;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.util.ServerRunner;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;

public class SimpleWebServer extends NanoHTTPD {
    private final boolean quiet;
    protected List<File> rootDirs;

    private final String cors;   // 跨域资源共享（Cross-origin resource sharing）
    private final static String ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS, HEAD";
    private final static int MAX_AGE = 42 * 60 * 60;
    // explicitly relax visibility to package for tests purposes
    public final static String DEFAULT_ALLOWED_HEADERS = "origin,accept,content-type";
    public final static String ACCESS_CONTROL_ALLOW_HEADER_PROPERTY_NAME = "AccessControlAllowHeader";

    @SuppressWarnings("serial")
    public static final List<String> INDEX_FILE_NAMES = new ArrayList<>();

    private static final String LICENCE;
    static {
        // A MIME type is a label used to identify a type of data
        // It is used so software can know how to handle the data.
        // You'll most commonly find them in the headers of HTTP messages and in email headers
        mimeTypes();
        INDEX_FILE_NAMES.add("index.html");
        INDEX_FILE_NAMES.add("index.htm");
        String text;
        try {
            InputStream stream = SimpleWebServer.class.getResourceAsStream("/LICENCE.txt");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                bytes.write(buffer, 0, count);
            }
            text = bytes.toString("utf-8");
        }
        catch (Exception e) {
            text = "Unknown";
        }
        LICENCE = text;
    }

    private static final Map<String, WebServerPlugin> mimeTypeHandlers = new HashMap<>();

    public SimpleWebServer(String host, int port, List<File> wwwroots, boolean quiet, String cors) {
        super(host, port);
        this.quiet = quiet;
        this.cors = cors;
        this.rootDirs = new ArrayList<>(wwwroots);
    }

    protected Response getInternalErrorResponse(String s) {
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                NanoHTTPD.MIME_PLAINTEXT, "INTERNAL ERROR: " + s);
    }

    protected Response getForbiddenResponse(String s) {
        return newFixedLengthResponse(Response.Status.FORBIDDEN,
                NanoHTTPD.MIME_PLAINTEXT, "FORBIDDEN: " + s);
    }

    protected Response getNotFoundResponse() {
        return newFixedLengthResponse(Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT, "Error 404, file not found.");
    }

    private String findIndexFileInDirectory(File directory) {
        for (String filename : SimpleWebServer.INDEX_FILE_NAMES) {
            // Creates a new File instance from a parent abstract pathname and a child pathname string
            File file = new File(directory, filename);
            if (file.isFile()) {
                return filename;
            }
        }
        return null;
    }

    // URL-encodes everything between "/"-characters. Encodes spaces as '%20'
    // TODO split tokenizer
    private String encodeUri(String uri) {
        StringBuilder newUri = new StringBuilder();
        // If the returnDelims flag is true, then the delimiter characters are also returned as tokens
        StringTokenizer st = new StringTokenizer(uri, "/", true);
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if ("/".equals(token)) {
                newUri.append("/");
            }
            else if (" ".equals(token)){
                newUri.append("%20");
            }
            else {
                try {
                    newUri.append(URLEncoder.encode(token, "utf-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
        return newUri.toString();
    }

    private String calculateAllowHeaders() {
        return System.getProperty(ACCESS_CONTROL_ALLOW_HEADER_PROPERTY_NAME, DEFAULT_ALLOWED_HEADERS);
    }

    protected String listDirectory(String uri, File file) {
        String heading = "Directory " + uri;
        StringBuilder msg = new StringBuilder("<html><head><title>").append(heading).append("</title><style><!--\n")
                .append("span.dirname { font-weight: bold; }\n").append("span.filesize { font-size: 75%; }\n")
                .append("// -->\n" + "</style>" + "</head><body><h1>")
                .append(heading).append("</h1>");

        String up = null;
        if (uri.length() > 1) {
            String u = uri.substring(0, uri.length() - 1); // 所有的uri都是以'/'结尾的
            int slash = u.lastIndexOf('/');
            if (slash >= 0) {
                up = uri.substring(slash - 1);            // 上一级目录
            }
        }

        List<String> files = new ArrayList<>();
        List<String> directories = new ArrayList<>();
        for (File f : file.listFiles()) { // 将文件和目录分开
            if (f.isFile()) {
                files.add(f.getName());
            }
            else {
                directories.add(f.getName());
            }
        }
        Collections.sort(files);
        Collections.sort(directories);

        if (up != null || directories.size() + files.size() > 0) {
            msg.append("<u1>");
            if (up != null || directories.size() > 0) {
                msg.append("<section class=\"directories\">");
                if (up != null) {
                    msg.append("<li><a rel=\"directory\" href=\"").append(up).
                            append("\"><span class=\"dirname\">..</span></a></li>");
                }
                for (String directory : directories) {
                    String dir = directory + "/";
                    msg.append("<li><a rel=\"directory\" href=\"").append(encodeUri(uri + dir)).
                            append("\"><span class=\"dirname\">").append(dir).append("</span></a></li>");
                }
                msg.append("</section>");
            }
            if (files.size() > 0) {
                msg.append("<section class=\"files\">");
                for (String f : files) {
                    msg.append("<li><a href=\"").append(encodeUri(uri + f)).append("\">")
                            .append("<span class=\"filename\">").append(f).append("</span></a>");
                    File curFile = new File(file, f);
                    long len = curFile.length();
                    msg.append("&nbsp;<span class=\"filesize\">(");
                    if (len < 1024) {
                        msg.append(len).append(" bytes");
                    } else if (len < 1024 * 1024) {
                        msg.append(len / 1024).append(".").append(len % 1024 / 10 % 100).append(" KB");
                    } else {
                        msg.append(len / (1024 * 1024)).append(".").
                                append(len % (1024 * 1024) / 10000 % 100).append(" MB");
                    }
                    msg.append(")</span></li>");
                }
                msg.append("</section>");
            }
            msg.append("</ul>");
        }
        msg.append("</body></html>");
        return msg.toString();
    }

    Response serveFile(Map<String, String> header, File file, String mime) {
        Response res;
        // toHexString - Returns a string representation of the integer argument as an unsigned integer in base 16
        // The ETag HTTP response header is an identifier for a specific version of a resource.
        // It lets caches be more efficient and save bandwidth, as a web server does not need to resend
        // a full response if the content has not changed
        String etag = Integer.toHexString((file.getAbsolutePath() + file.lastModified() + "" + file.length()).hashCode());

        long startFrom = 0;
        long endAt = -1;
        String range = header.get("range");
        if (range != null && range.startsWith("bytes=")) {
            range = range.substring("bytes=".length());
            int minus = range.indexOf('-');
            if (minus > 0) {
                startFrom = Long.parseLong(range.substring(0, minus));
                endAt = Long.parseLong(range.substring(minus + 1));
            }
        }

        // get if-range header. If present, it must match etag, or we should ignore the range request
        String ifRange = header.get("if-range");
        boolean headerIfRangeMissingOrMatching = (ifRange == null || etag.equals(ifRange));

        String ifNoneMatch = header.get("if-none-match");
        boolean headerIfNoneMatchPresentAndMatching = ifNoneMatch != null
                && ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag));

        // Change return code and add Content-Range header when skipping is requested
        long fileLen = file.length();
        boolean flag = true;
        if (headerIfRangeMissingOrMatching && range != null && startFrom >= 0 && startFrom < fileLen) {
            // range request that matches current etag and the startFrom of the range is satisfiable

            if (headerIfNoneMatchPresentAndMatching) {
                // match current etag, respond with not-modified
                res = newFixedLengthResponse(Response.Status.NOT_MODIFIED, mime, "");
            }
            else {
                if (endAt < 0) {
                    endAt = fileLen - 1;
                }
                long newLen = endAt - startFrom + 1;
                if (newLen < 0) newLen = 0;

                FileInputStream fis;
                try {
                    fis = new FileInputStream(file);
                    fis.skip(startFrom);
                    res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, fis, newLen);
                    res.addHeader("Accept-Ranges", "bytes");
                    res.addHeader("Content-Length", "" + newLen);
                    res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
                } catch (IOException e) {
                    res = getForbiddenResponse("Reading file failed.");
                    flag = false;
                }
            }
        }
        else {
            if (headerIfRangeMissingOrMatching && range != null && startFrom >= fileLen) {
                res = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
                res.addHeader("Content-Range", "bytes */" + fileLen);
            }
            else if (range == null && headerIfNoneMatchPresentAndMatching) {
                // full-file-fetch request would return entire file respond with not-modified
                res = newFixedLengthResponse(Response.Status.NOT_MODIFIED, mime, "");
            } else if (!headerIfRangeMissingOrMatching && headerIfNoneMatchPresentAndMatching) {
                // range request doesn't match current etag
                // return entire (different) file respond with not-modified
                res = newFixedLengthResponse(Response.Status.NOT_MODIFIED, mime, "");
            } else {
                // supply the file
                try {
                    res = newFixedLengthResponse(Response.Status.OK, mime,
                            new FileInputStream(file), (int)file.length());
                    res.addHeader("Accept-Ranges", "bytes");
                    res.addHeader("Content-Length", "" + fileLen);
                } catch (IOException e) {
                    res = getForbiddenResponse("Reading file failed.");
                    flag = false;
                }
            }
        }
        if (flag)  res.addHeader("ETag", etag);
        return res;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Map<String, String> header = session.getHeaders();
        Map<String, String> params = session.getParms();
        String uri = session.getUri();

        System.out.println(session.getMethod() + " '" + uri + "' ");
        if (!this.quiet) {
            for (Map.Entry<String, String> entry : header.entrySet()) {
                System.out.println("  HDR: '" + entry.getKey() + "' = '" + entry.getValue() + "'");
            }
            for (Map.Entry<String, String> entry : params.entrySet()) {
                System.out.println("  PRM: '" + entry.getKey() + "' = '" + entry.getValue() + "'");
            }
        }
        for (File homeDir : this.rootDirs) {
            if (!homeDir.isDirectory()) {
                return getInternalErrorResponse("given path is not a directory (" + homeDir + ").");
            }
        }
        // Returns an unmodifiable view of the specified map.
        // This method allows modules to provide users with "read-only" access to internal maps
        return response(Collections.unmodifiableMap(header), session, uri);
    }

    private Response response(Map<String, String> header, IHTTPSession session, String uri) {
        if (cors != null && Method.OPTIONS.equals(session.getMethod())) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, null, 0);
        }

        Response res = defaultRespond(header, session, uri);
        if (cors != null){
            res = addCORSHeaders(res, cors);
        }
        return res;
    }

    private Response defaultRespond(Map<String, String> header, IHTTPSession session, String uri) {
        uri = uri.trim().replace(File.separatorChar, '/'); // trim 去掉头尾的空格
        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf("?"));
        }
        if (uri.contains("../")) {
            getForbiddenResponse("Won't serve ../ for security reasons.");
        }

        boolean canServeUri = false;
        File homeDir = null;
        for (int i = 0; !canServeUri && i < this.rootDirs.size(); i++) {
            homeDir = this.rootDirs.get(i);
            canServeUri = canServeUri(uri, homeDir); // homeDir下是否有uri这个子路径
        }
        if (!canServeUri) {
            return getNotFoundResponse();
        }

        File f = new File(homeDir, uri);
        if (f.isDirectory() && !uri.endsWith("/")) {
            uri += "/";
            Response res = newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML,
                    "<html><body>Redirected: <a href=\"" + uri + "\">" + uri + "</a></body></html>");
            res.addHeader("Location", uri);
            return res;
        }

        if (f.isDirectory()) {
            String indexFile = findIndexFileInDirectory(f);
            if (indexFile == null) {
                if (f.canRead()) {
                    // No index file, list the directory if it is readable
                    return newFixedLengthResponse(Response.Status.OK,
                            NanoHTTPD.MIME_HTML, listDirectory(uri, f));
                }
                else {
                    return getForbiddenResponse("No directory listing.");
                }
            }
            else {
                return response(header, session, uri + indexFile);
            }
        }
        String mimeTypeForFile = getMimeTypeForFile(uri);
        WebServerPlugin plugin = SimpleWebServer.mimeTypeHandlers.get(mimeTypeForFile);
        Response response;
        if (plugin != null && plugin.canServeUri(uri, homeDir)) {
            response = plugin.serveFile(uri, header, session, f, mimeTypeForFile);
            // internalRewrite 的作用？
            if (response instanceof InternalRewrite) {
                InternalRewrite rewrite = (InternalRewrite) response;
                return response(rewrite.getHeader(), session, rewrite.getUri());
            }
        }
        else {
            response = serveFile(header, f, mimeTypeForFile);
        }
        return response == null? getNotFoundResponse() : response;
    }

    protected Response addCORSHeaders(Response res, String cors) {
        res.addHeader("Access-Control-Allow-Origin", cors);
        res.addHeader("Access-Control-Allow-Headers", calculateAllowHeaders());
        res.addHeader("Access-Control-Allow-Credentials", "true");
        res.addHeader("Access-Control-Allow-Methods", ALLOWED_METHODS);
        res.addHeader("Access-Control-Max-Age", "" + MAX_AGE);

        return res;
    }

    private boolean canServeUri(String uri, File homeDir) {
        boolean canServeUri;
        File file = new File(homeDir, uri);
        canServeUri = file.exists();
        if (!canServeUri) {
            WebServerPlugin plugin = SimpleWebServer.mimeTypeHandlers.get(getMimeTypeForFile(uri));
            if (plugin != null) {
                canServeUri = plugin.canServeUri(uri, homeDir);
            }
        }
        return canServeUri;
    }

    protected static void registerPluginForMimeType(String[] indexFiles, String mimeType, WebServerPlugin plugin,
                                                    Map<String, String> commandLineOptions) {
        if (mimeType == null || plugin == null) {
            return;
        }

        if (indexFiles != null) {
            for (String filename : indexFiles) {
                int dot = filename.indexOf(".");
                if (dot >= 0) {
                    String extension = filename.substring(dot + 1).toLowerCase();
                    mimeTypes().put(extension, mimeType);
                }
            }
            SimpleWebServer.INDEX_FILE_NAMES.addAll(Arrays.asList(indexFiles));
        }
        SimpleWebServer.mimeTypeHandlers.put(mimeType, plugin);
        plugin.initialize(commandLineOptions);
    }

    public static void main(String[] args) {
        // default
        int port = 8080;
        String host = null;  // bind to all interfaces by default
        List<File> rootDirs = new ArrayList<>();
        boolean quiet = false;
        String cors = null;
        Map<String, String> options = new HashMap<>();

        // Parse command-line, with short and long versions of the options.
        for (int i = 0; i < args.length; i++) {
            if ("-h".equalsIgnoreCase(args[i]) || "--host".equalsIgnoreCase(args[i])) {
                host = args[i + 1];
            } else if ("-p".equalsIgnoreCase(args[i]) || "--port".equalsIgnoreCase(args[i])) {
                port = Integer.parseInt(args[i + 1]);
            } else if ("-q".equalsIgnoreCase(args[i]) || "--quiet".equalsIgnoreCase(args[i])) {
                quiet = true;
            } else if ("-d".equalsIgnoreCase(args[i]) || "--dir".equalsIgnoreCase(args[i])) {
                rootDirs.add(new File(args[i + 1]).getAbsoluteFile());
            } else if (args[i].startsWith("--cors")) {
                cors = "*";
                int equalIdx = args[i].indexOf('=');
                if (equalIdx > 0) {
                    cors = args[i].substring(equalIdx + 1);
                }
            } else if ("--licence".equalsIgnoreCase(args[i])) {
                System.out.println(SimpleWebServer.LICENCE + "\n");
            } else if (args[i].startsWith("-X:")) {
                int dot = args[i].indexOf('=');
                if (dot > 0) {
                    String name = args[i].substring(0, dot);
                    String value = args[i].substring(dot + 1);
                    options.put(name, value);
                }
            }
        }
        if (rootDirs.isEmpty()) {
            // . -- 代表目前所在的目录，相对路径
            // Returns the absolute form of this abstract pathname
            rootDirs.add(new File(".").getAbsoluteFile());
        }

        options.put("host", host);
        options.put("port", String.valueOf(port));
        options.put("quiet", String.valueOf(quiet));
        StringBuilder sb = new StringBuilder();
        for (File dir : rootDirs) {
            if (sb.length() > 0) {
                sb.append(":");
            }
            try {
                // Returns the canonical pathname string of this abstract pathname
                // A canonical pathname is both absolute and unique.
                sb.append(dir.getCanonicalPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        options.put("home", sb.toString());

        ServiceLoader<WebServerPluginInfo> serviceLoader = ServiceLoader.load(WebServerPluginInfo.class);
//        serviceLoader
        for (WebServerPluginInfo info : serviceLoader) {
            String[] mimeTypes = info.getMimeTypes();
            for (String mime : mimeTypes) {
                String[] indexFiles = info.getIndexFilesForMimeType(mime);
                if (!quiet) {
                    System.out.print("# Found plugin for Mime type: \"" + mime + "\"");
                    if (indexFiles != null) {
                        System.out.print(" (serving index files: ");
                        for (String indexFile : indexFiles) {
                            System.out.print(indexFile + " ");
                        }
                        System.out.println(").");
                    }
                }
                registerPluginForMimeType(indexFiles, mime, info.getWebServerPlugin(mime), options);
            }
        }
        ServerRunner.executeInstance(new SimpleWebServer(host, port, rootDirs, quiet, cors));
    }
}
