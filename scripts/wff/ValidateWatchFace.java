import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import org.xml.sax.*;
import java.io.File;
public class ValidateWatchFace {
  public static void main(String[] a) throws Exception {
    SchemaFactory f = SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1");
    Schema s = f.newSchema(new File(a[0]));
    Validator v = s.newValidator();
    final int[] n = {0};
    v.setErrorHandler(new ErrorHandler() {
      public void warning(SAXParseException e) { System.out.println("WARN " + e.getLineNumber() + ": " + e.getMessage()); }
      public void error(SAXParseException e) { n[0]++; System.out.println("ERR " + e.getLineNumber() + ": " + e.getMessage()); }
      public void fatalError(SAXParseException e) { n[0]++; System.out.println("FATAL " + e.getLineNumber() + ": " + e.getMessage()); }
    });
    v.validate(new StreamSource(new File(a[1])));
    System.out.println(n[0] == 0 ? "VALID" : n[0] + " errors");
    if (n[0] != 0) System.exit(1);
  }
}
