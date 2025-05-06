import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

@SuppressWarnings("NullableProblems")
public class ConvertTestResultsToMarkdown {
  private static final String anchorSuffix = "-" + Long.toHexString(new Random().nextLong());
  private static final String topAnchor = "top" + anchorSuffix;

  private static class Counters {
    public Counters(String module) {
      this.module = module;
    }

    final String module;
    long timeMillis;
    int tests;
    int errors;
    int skipped;
    int failures;

    private void add(Element testsuite) {
      double timeSeconds = getDoubleAttr(testsuite, "time");
      timeMillis += (long) (timeSeconds * 1000);
      tests += getIntAttr(testsuite, "tests");
      errors += getIntAttr(testsuite, "errors");
      skipped += getIntAttr(testsuite, "skipped");
      failures += getIntAttr(testsuite, "failures");
    }

    private void add(Counters other) {
      timeMillis += other.timeMillis;
      tests += other.tests;
      errors += other.errors;
      skipped += other.skipped;
      failures += other.failures;
    }

    private static String urlEncode(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String toBadgeMarkdown() {
      String altText = "Test results: " + passed() + " passed, " + errorsAndFailures() + " failed, " + skipped + " skipped, time: " + formatTimeMillis(timeMillis);
      String imgUrl = "https://img.shields.io/badge/" +
        urlEncode("tests-" + passed() + " ✅ | " + errorsAndFailures() + " ❌ | " + skipped + " ⏭ | " + formatTimeMillis(timeMillis) + " ⏱️-white");

      return "![" + altText + "](" + imgUrl + ")";
    }

    static String tableHeaderMarkdown() {
      return "|Package|Passed|Failed|Skipped|Time (min:sec)|\n" +
        "|:---|---:|---:|---:|---:|";
    }

    private static String markdownTableRow(String... cells) {
      return "|" + String.join("|", cells) + "|";
    }

    private int passed() {
      return tests - errorsAndFailures() - skipped;
    }

    private int errorsAndFailures() {
      return errors + failures;
    }

    String toMarkdown() {
      int passed = passed();
      int errorsAndFailures = errorsAndFailures();

      boolean linkToModule = passed() != tests;

      return markdownTableRow(
        !linkToModule ? module : ("[" + module + "](#" + module + anchorSuffix + ")"),
        passed > 0 ? (passed + " ✅") : "",
        errorsAndFailures > 0 ? (errorsAndFailures + " ❌") : "",
        // reran + " 🔄",
        skipped > 0 ? (skipped + " ⏭️") : "",
        formatTimeMillis(timeMillis) + " ⏱️"
      );
    }

    public String toString() {
      return toMarkdown();
    }
  }

  static class CountersAndDetails implements Comparable<CountersAndDetails> {
    final Counters counters;
    final List<Element> testcases = new ArrayList<>();

    CountersAndDetails(String packageName) {
      this.counters = new Counters(packageName);
    }

    private static final Comparator<CountersAndDetails> errorsAndSkipsFirst =
      Comparator.comparing((CountersAndDetails it) -> it.counters.errorsAndFailures() > 0)
        .thenComparing(it -> it.counters.skipped > 0)
        .reversed();

    @Override
    public int compareTo(ConvertTestResultsToMarkdown.CountersAndDetails o) {
      return errorsAndSkipsFirst.compare(this, o);
    }
  }


  public static void main(String[] args) throws Exception {
    String rootDir;
    if (args.length != 1) {
//      rootDir = ".";
//      rootDir = "/Users/david.nault/repos/forks/couchbase-jvm-clients";
      System.err.println("Usage: ConvertTestResultsToMarkdown <rootDir>");
      System.exit(1);
      throw new RuntimeException("unreachable");
    } else {
      rootDir = args[0];
    }

    List<Path> files = new ArrayList<>();
    Files.walkFileTree(Paths.get(rootDir), new FileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String fileName = file.getFileName().toString();
        if (fileName.startsWith("TEST-") && fileName.endsWith(".xml")) {
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


    Set<String> interestingTags = Set.of(
      "skipped",
      "failure",
      "error"
//      "rerunFailure",
//      "flakyFailure",
//      "rerunError",
//      "flakyError"
    );

    Map<String, CountersAndDetails> packageToCountersAndDetails = new TreeMap<>();

    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

    for (Path reportFile : files) {
      Document reportDoc = builder.parse(reportFile.toFile());
      Element testsuite = reportDoc.getDocumentElement();
      if (!testsuite.getNodeName().equals("testsuite")) {
        System.err.println("Skipping " + reportFile.getFileName() + " because it is not an XML document with a root named 'testsuite'.");
        continue;
      }

      String packageName = getPackage(testsuite.getAttribute("name"));
      if (packageName.isEmpty()) packageName = "default";

      CountersAndDetails countersAndDetails = packageToCountersAndDetails.computeIfAbsent(packageName, CountersAndDetails::new);
      countersAndDetails.counters.add(testsuite);

      childElements(testsuite, "testcase").forEach(testcase -> {

        boolean hasInterestingChild = childElements(testcase)
          .map(Element::getTagName)
          .anyMatch(interestingTags::contains);

        if (hasInterestingChild) {
          countersAndDetails.testcases.add(testcase);
        }
      });
    }

    Counters aggregated = new Counters("aggregated");
    packageToCountersAndDetails.values().forEach(it -> aggregated.add(it.counters));

    System.out.println("<a id=\"" + topAnchor + "\"></a>");

    System.out.println(aggregated.toBadgeMarkdown());
    System.out.println();

    System.out.println("<details>");
    System.out.println("<summary>Click here for details</summary>");
    System.out.println();

    System.out.println(Counters.tableHeaderMarkdown());


    packageToCountersAndDetails.values().stream().sorted().forEach(counterAndDetails -> {
      System.out.println(counterAndDetails.counters.toMarkdown());
    });

    packageToCountersAndDetails.values().forEach(counterAndDetails -> {
      if (counterAndDetails.testcases.isEmpty()) return;

      System.out.println("<a id=\"" + counterAndDetails.counters.module + anchorSuffix + "\"></a>");
      System.out.println("## " + counterAndDetails.counters.module);

      counterAndDetails.testcases.stream()
        .map(ConvertTestResultsToMarkdown::toMarkdown)
        // within each package, show failures before skips
        .sorted(Comparator.comparing((String it) -> it.contains("❌")).reversed())
        .forEach(System.out::println);
    });

    System.out.println("</details>");


    if (aggregated.errorsAndFailures() != 0) {
      int exitCode = 2;
      System.err.println("Found at least one test failure. Exit code: " + exitCode);
      System.exit(exitCode);
    }

    if (aggregated.tests == 0) {
      int exitCode = 3;
      System.err.println("No tests found. Exit code: " + exitCode);
      System.exit(exitCode);
    }

    System.err.println("All tests passed.");
  }

  private static String getPackage(String name) {
    int index = name.lastIndexOf('.');
    return index == -1 ? "" : name.substring(0, index);
  }

  private static String removePackage(String name) {
    String removeMe = getPackage(name);
    return removeMe.isEmpty() ? name : name.substring(removeMe.length() + 1);
  }


  static String toMarkdown(Element testcase) {
    String className = testcase.getAttribute("classname");
    String testName = testcase.getAttribute("name");
    double timeSeconds = getDoubleAttr(testcase, "time");

    StringBuilder sb = new StringBuilder();


    String headerEmoji = childElements(testcase, "skipped").findAny().isPresent() ? "⏭️" : "❌";

    String classSimpleName = removePackage(className);
    sb.append("#### ").append(headerEmoji).append("&nbsp;").append(classSimpleName).append(".").append(testName).append("\n");

    childElements(testcase, "skipped").forEach(failure -> appendFailureOrError(sb, failure, timeSeconds));

    childElements(testcase, "failure").forEach(failure -> appendFailureOrError(sb, failure, timeSeconds));
    childElements(testcase, "error").forEach(failure -> appendFailureOrError(sb, failure, timeSeconds));

    childElements(testcase, "system-out").forEach(stdout -> {
      sb.append("<details>\n")
        .append("<summary>stdout</summary>\n")
        .append("\n")
        .append("```\n")
        .append(truncate(childText(stdout))).append("\n")
        .append("```\n")
        .append("\n")
        .append("</details>\n");
    });

    childElements(testcase, "system-err").forEach(stdout -> {
      sb.append("<details>\n")
        .append("<summary>stderr</summary>\n")
        .append("\n")
        .append("```\n")
        .append(truncate(childText(stdout))).append("\n")
        .append("```\n")
        .append("\n")
        .append("</details>\n");
    });

    sb.append("\n[\uD83D\uDD1D](#").append(topAnchor).append(")\n");
    sb.append("\n----\n");

    return sb.toString();
  }

  private static String truncate(String s) {
    int max = 8 * 1024;
    if (s.length() <= max) return s;

    return "[too long; skipping middle]\n" +
      s.substring(0, max / 2) + "\n[...skipping...]\n" + s.substring(s.length() - (max / 2));
  }

  private static int getIntAttr(Element element, String attrName) {
    return getAttr(element, attrName, Integer::valueOf, 0);
  }

  private static double getDoubleAttr(Element element, String attrName) {
    return getAttr(element, attrName, Double::valueOf, 0d);
  }

  private static <T> T getAttr(Element element, String attrName, Function<String, T> parser, T defaultValue) {
    String attrValue = element.getAttribute(attrName);
    return attrValue.isEmpty() ? defaultValue : parser.apply(attrValue);
  }

  static String firstLine(String s) {
    try {
      return new BufferedReader(new StringReader(s)).readLine();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void appendFailureOrError(StringBuilder sb, Element failure, double timeSeconds) {
    String childText = childText(failure);
    String message = failure.getAttribute("message");

    // Dor a "skipped" due to failed assumption, the message is blank.
    // Use the first line of the stack trace.
    if (message.isBlank()) message = firstLine(childText);

    sb.append("```\n")
      .append(message).append("\n")
      .append("```\n");

    long timeMillis = (long) (timeSeconds * 1000);
    if (timeMillis > 0) {
      sb.append(formatTimeMillis(timeMillis)).append(" ⏱️\n");
    }

    if (!childText.isBlank()) {
      sb.append("<details>\n")
        .append("<summary>Stack trace</summary>\n")
        .append("\n")
        .append("```\n")
        .append(childText.trim()).append("\n")
        .append("```\n")
        .append("\n")
        .append("</details>\n");
    }
  }

  ;

  private static String childText(Element element) {
    StringBuilder sb = new StringBuilder();
    childNodes(element)
      .filter(it -> it.getNodeType() == Node.TEXT_NODE || it.getNodeType() == Node.CDATA_SECTION_NODE)
      .map(Node::getNodeValue)
      .forEach(sb::append);
    return sb.toString();
  }

  private static Stream<Node> childNodes(Node node) {
    return toStream(node.getChildNodes());
  }

  private static Stream<Node> toStream(NodeList nodes) {
    List<Node> result = new ArrayList<>();
    for (int i = 0; i < nodes.getLength(); i++) {
      result.add(nodes.item(i));
    }
    return result.stream();
  }

  private static Stream<Element> childElements(Node node) {
    return childNodes(node)
      .filter(it -> it.getNodeType() == Node.ELEMENT_NODE)
      .map(it -> (Element) it);
  }

  private static Stream<Element> childElements(Node node, String tagName) {
    return childElements(node)
      .filter(it -> it.getTagName().equals(tagName));
  }

  static String formatTimeMillis(long millis) {
    long minutes = millis / 60000;
    millis -= minutes * 60000;

    long seconds = millis / 1000;
    millis -= seconds * 1000;

    return minutes + ":" + String.format("%02d", seconds) + "." + String.format("%03d", millis);
  }
}
