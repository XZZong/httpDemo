package samples;

import fi.iki.elonen.util.ServerRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TempFilesServer extends DebugServer{
    private static class ExampleManager implements TempFileManager {
        private final File tmpdir;
        private final List<TempFile> tmpFiles;

        private ExampleManager() {
            // System.getProperty() returns the string value of the system property,
            // or null if there is no property with that key.
            this.tmpdir = new File(System.getProperty("java.io.tmpdir"));
            this.tmpFiles = new ArrayList<TempFile>();
        }

        public void clear() {
            if (!this.tmpFiles.isEmpty()) {
                System.out.println("Cleaning up");
            }
            for (TempFile file : this.tmpFiles) {
                try {
                    System.out.println("  " + file.getName());
                    file.delete();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            this.tmpFiles.clear();
        }

        public TempFile createTempFile(String s) throws Exception {
            DefaultTempFile tempFile = new DefaultTempFile(this.tmpdir);
            this.tmpFiles.add(tempFile);
            System.out.println("Create Temp File:" + tempFile.getName());
            return tempFile;
        }
    }

    private static class ExampleManagerFactory implements TempFileManagerFactory {
        public TempFileManager create() {
            return new ExampleManager();
        }
    }

    public static void main(String[] args) {
        TempFilesServer server = new TempFilesServer();
        server.setTempFileManagerFactory(new ExampleManagerFactory());
        ServerRunner.executeInstance(server);
    }
}
