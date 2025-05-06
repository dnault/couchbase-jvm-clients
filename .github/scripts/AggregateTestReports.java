import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AggregateTestReports {

  public static void main(String[] args) throws Exception {
    System.out.println("Aggregate test report");
    List<Path> files = new ArrayList<>();
    Files.walkFileTree(Paths.get("."), new FileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String fileName = file.getFileName().toString();
        if (fileName.startsWith("TEST-") && fileName.endsWith(".xml")) {
          System.out.println(file);
          files.add(file);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        return FileVisitResult.CONTINUE;
      }
    });

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document aggregatedDoc = builder.newDocument();
    aggregatedDoc.setXmlStandalone(true);

    Element root = aggregatedDoc.createElement("testsuite");
    root.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
    root.setAttribute("xsi:noNamespaceSchemaLocation", "https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report.xsd");
    root.setAttribute("version", "3.0.1");

    root.setAttribute("name", "core-io");

    aggregatedDoc.appendChild(root);

    List<String> interestingChildren = Arrays.asList(
      "skipped",
      "failure",
      "rerunFailure",
      "flakyFailure",
      "error",
      "rerunError",
      "flakyError"
    );

    // version="3.0.1" name="com.couchbase.client.core.TimerTest" time="1.024" tests="3" errors="0" skipped="0" failures="1"

    double timeSeconds = 0;
    int tests = 0;
    int errors = 0;
    int skipped = 0;
    int failures = 0;

    for (Path reportFile : files) {
      Document reportDoc = builder.parse(reportFile.toFile());
      Element reportDocRoot = reportDoc.getDocumentElement();

      if (!reportDocRoot.getNodeName().equals("testsuite")) {
        System.out.println("Skipping " + reportFile.getFileName()  + " because it is not a test report");
        continue;
      }

      timeSeconds += getDoubleAttr(reportDocRoot, "time");
      tests += getIntAttr(reportDocRoot, "tests");
      errors += getIntAttr(reportDocRoot, "errors");
      skipped += getIntAttr(reportDocRoot, "skipped");
      failures += getIntAttr(reportDocRoot, "failures");


      NodeList testcases = reportDocRoot.getElementsByTagName("testcase");

      for (int i = 0; i < testcases.getLength(); i++) {
        Element element = (Element) testcases.item(i);

        Set<String> childNames = childElementNames(element);
        childNames.retainAll(interestingChildren);
        if (!childNames.isEmpty()) {
          Element importedTestSuite = (Element) aggregatedDoc.importNode(element, true);
          root.appendChild(importedTestSuite);
        }
      }
    }


    root.setAttribute("time", String.format("%.3f", timeSeconds));
    root.setAttribute("tests", String.valueOf(tests));
    root.setAttribute("errors", String.valueOf(errors));
    root.setAttribute("skipped", String.valueOf(skipped));
    root.setAttribute("failures", String.valueOf(failures));

    writeAggregatedReport(aggregatedDoc);
//    System.out.println(aggregatedDoc);

  }

  private static int getIntAttr(Element element, String attrName) {
    String attrValue = element.getAttribute(attrName);
    return attrValue.isEmpty() ? 0 : Integer.parseInt(attrValue);
  }

  private static double getDoubleAttr(Element element, String attrName) {
    String attrValue = element.getAttribute(attrName);
    return attrValue.isEmpty() ? 0 : Double.parseDouble(attrValue);
  }

  private static Set<String> childElementNames(Element element) {
    Set<String> result = new LinkedHashSet<>();
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        result.add(child.getNodeName());
      }
    }
    return result;
  }

  private static String writeAggregatedReport(Document doc) throws TransformerException {
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    DOMSource source = new DOMSource(doc);

    String outputPath = "aggregated-test-report.xml";
//    StreamResult result = new StreamResult(System.out);
    StreamResult result = new StreamResult(new File(outputPath));
    transformer.transform(source, result);

    return outputPath;
  }
}
