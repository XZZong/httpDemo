import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class Test {
    public static void listDirectory() {
        File file = new File(".").getAbsoluteFile();
        File[] files = file.listFiles();
        for (File f : files) {
            if (f.isFile()) {
                System.out.println(f.getName());
            }
        }
    }

    public static void encode(String s) {
        StringBuilder sb = new StringBuilder();
        String[] strings = s.split("/");
        // 使用split后， / 的添加存在问题
        System.out.println(strings.length);
        for (String ss : strings) {
            if (" ".equals(ss)) {
                sb.append("%20");
            }
            else {
                try {
                    sb.append(URLEncoder.encode(ss, "utf-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            sb.append("/");
        }
        System.out.println(sb.toString());
    }

    public static void main(String[] args) {
        encode("/src/");
        encode("/abc.txt");
    }
}
