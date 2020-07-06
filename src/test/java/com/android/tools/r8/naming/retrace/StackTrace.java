// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.base.Equivalence;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class StackTrace {

  public static String AT_PREFIX = "at ";
  public static String TAB_AT_PREFIX = "\t" + AT_PREFIX;

  public static class Builder {

    private List<StackTraceLine> stackTraceLines = new ArrayList<>();

    private Builder() {}

    public Builder add(StackTraceLine line) {
      stackTraceLines.add(line);
      return this;
    }

    public Builder addWithoutFileNameAndLineNumber(Class<?> clazz, String methodName) {
      return addWithoutFileNameAndLineNumber(clazz.getTypeName(), methodName);
    }

    public Builder addWithoutFileNameAndLineNumber(String className, String methodName) {
      stackTraceLines.add(
          StackTraceLine.builder().setClassName(className).setMethodName(methodName).build());
      return this;
    }

    public StackTrace build() {
      return new StackTrace(
          stackTraceLines,
          StringUtils.join(
              stackTraceLines.stream().map(StackTraceLine::toString).collect(Collectors.toList()),
              "\n"));
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class StackTraceLine {

    public static class Builder {
      private String className;
      private String methodName;
      private String fileName;
      private int lineNumber = -1;

      private Builder() {}

      public Builder setClassName(String className) {
        this.className = className;
        return this;
      }

      public Builder setMethodName(String methodName) {
        this.methodName = methodName;
        return this;
      }

      public Builder setFileName(String fileName) {
        this.fileName = fileName;
        return this;
      }

      public Builder setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
        return this;
      }

      public StackTraceLine build() {
        String lineNumberPart = lineNumber >= 0 ? ":" + lineNumber : "";
        String originalLine = className + '.' + methodName + '(' + fileName + lineNumberPart + ')';
        return new StackTraceLine(originalLine, className, methodName, fileName, lineNumber);
      }
    }

    public final String originalLine;
    public final String className;
    public final String methodName;
    public final String fileName;
    public final int lineNumber;

    public StackTraceLine(
        String originalLine, String className, String methodName, String fileName, int lineNumber) {
      this.originalLine = originalLine;
      this.className = className;
      this.methodName = methodName;
      this.fileName = fileName;
      this.lineNumber = lineNumber;
    }

    public static Builder builder() {
      return new Builder();
    }

    public boolean hasLineNumber() {
      return lineNumber >= 0;
    }

    public static StackTraceLine parse(String line) {
      String originalLine = line;

      line = line.trim();
      if (line.startsWith(AT_PREFIX)) {
        line = line.substring(AT_PREFIX.length());
      }

      // Expect only one '(', and only one ')' with an optional ':' in between.
      int parenBeginIndex = line.indexOf('(');
      assertTrue(parenBeginIndex > 0);
      assertEquals(parenBeginIndex, line.lastIndexOf('('));
      int parenEndIndex = line.indexOf(')');
      assertTrue(parenBeginIndex < parenEndIndex);
      assertEquals(parenEndIndex, line.lastIndexOf(')'));
      int colonIndex = line.indexOf(':');
      assertTrue(colonIndex == -1 || (parenBeginIndex < colonIndex && colonIndex < parenEndIndex));
      assertEquals(parenEndIndex, line.lastIndexOf(')'));
      String classAndMethod = line.substring(0, parenBeginIndex);
      int lastDotIndex = classAndMethod.lastIndexOf('.');
      assertTrue(lastDotIndex > 0);
      String className = classAndMethod.substring(0, lastDotIndex);
      String methodName = classAndMethod.substring(lastDotIndex + 1);
      int fileNameEnd = colonIndex > 0 ? colonIndex : parenEndIndex;
      String fileName = line.substring(parenBeginIndex + 1, fileNameEnd);
      int lineNumber =
          colonIndex > 0 ? Integer.parseInt(line.substring(colonIndex + 1, parenEndIndex)) : -1;
      StackTraceLine result =
          new StackTraceLine(originalLine, className, methodName, fileName, lineNumber);
      assertEquals(line, result.toString());
      return result;
    }

    @Override
    public int hashCode() {
      return className.hashCode() * 31
          + methodName.hashCode() * 13
          + fileName.hashCode() * 7
          + lineNumber;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (other instanceof StackTraceLine) {
        StackTraceLine o = (StackTraceLine) other;
        return className.equals(o.className)
            && methodName.equals(o.methodName)
            && fileName.equals(o.fileName)
            && lineNumber == o.lineNumber;
      }
      return false;
    }

    @Override
    public String toString() {
      String lineNumberPart = lineNumber >= 0 ? ":" + lineNumber : "";
      return className + '.' + methodName + '(' + fileName + lineNumberPart + ')';
    }
  }

  private final List<StackTraceLine> stackTraceLines;
  private final String originalStderr;

  private StackTrace(List<StackTraceLine> stackTraceLines, String originalStderr) {
    assert stackTraceLines.size() > 0;
    this.stackTraceLines = stackTraceLines;
    this.originalStderr = originalStderr;
  }

  public int size() {
    return stackTraceLines.size();
  }

  public StackTraceLine get(int index) {
    return stackTraceLines.get(index);
  }

  public String getOriginalStderr() {
    return originalStderr;
  }

  public List<StackTraceLine> getStackTraceLines() {
    return stackTraceLines;
  }

  public static StackTrace extractFromArt(String stderr, DexVm vm) {
    List<StackTraceLine> stackTraceLines = new ArrayList<>();
    List<String> stderrLines = StringUtils.splitLines(stderr);

    // A Dalvik stacktrace looks like this (apparently the bottom frame
    // "dalvik.system.NativeStart.main" is not always present)
    // W(209693) threadid=1: thread exiting with uncaught exception (group=0xf616cb20)  (dalvikvm)
    // java.lang.NullPointerException
    // \tat com.android.tools.r8.naming.retrace.Main.a(:133)
    // \tat com.android.tools.r8.naming.retrace.Main.a(:139)
    // \tat com.android.tools.r8.naming.retrace.Main.main(:145)
    // \tat dalvik.system.NativeStart.main(Native Method)
    //
    // An Art 5.1.1 and 6.0.1 stacktrace looks like this:
    // java.lang.NullPointerException: throw with null exception
    // \tat com.android.tools.r8.naming.retrace.Main.a(:154)
    // \tat com.android.tools.r8.naming.retrace.Main.a(:160)
    // \tat com.android.tools.r8.naming.retrace.Main.main(:166)
    //
    // An Art 7.0.0 and latest stacktrace looks like this:
    // Exception in thread "main" java.lang.NullPointerException: throw with null exception
    // \tat com.android.tools.r8.naming.retrace.Main.a(:150)
    // \tat com.android.tools.r8.naming.retrace.Main.a(:156)
    // \tat com.android.tools.r8.naming.retrace.Main.main(:162)
    // TODO(122940268): Remove test code when fixed.
    System.out.println("TOTAL STDERR LINES: " + stderrLines.size());
    for (int i = 0; i < stderrLines.size(); i++) {
      System.out.print("LINE " + i + ": " + stderrLines.get(i));
      if (stderrLines.get(i).length() > 3) {
        System.out.print(" (" + ((int) stderrLines.get(i).charAt(0)));
        System.out.print(", " + ((int) stderrLines.get(i).charAt(1)));
        System.out.print(", " + ((int) stderrLines.get(i).charAt(2) + ")"));
      } else {
        System.out.print(" (less than three chars)");
      }
      if (stderrLines.get(i).startsWith(TAB_AT_PREFIX)) {
        System.out.println(" IS STACKTRACE LINE");
      } else {
        System.out.println(" IS NOT STACKTRACE LINE");
      }
    }
    for (int i = 0; i < stderrLines.size(); i++) {
      String line = stderrLines.get(i);
      // Find all lines starting with "\tat" except "dalvik.system.NativeStart.main" frame
      // if present.
      if (line.startsWith(TAB_AT_PREFIX)
          && !(vm.isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)
              && line.contains("dalvik.system.NativeStart.main"))) {
        stackTraceLines.add(StackTraceLine.parse(stderrLines.get(i)));
      }
    }
    return new StackTrace(stackTraceLines, stderr);
  }

  private static List<StackTraceLine> internalExtractFromJvm(String stderr) {
    return StringUtils.splitLines(stderr).stream()
        .filter(s -> s.startsWith(TAB_AT_PREFIX))
        .map(StackTraceLine::parse)
        .collect(Collectors.toList());
  }

  public static StackTrace extractFromJvm(String stderr) {
    return new StackTrace(internalExtractFromJvm(stderr), stderr);
  }

  public static StackTrace extractFromJvm(TestRunResult result) {
    assertNotEquals(0, result.getExitCode());
    return extractFromJvm(result.getStdErr());
  }

  public StackTrace retrace(String map) {
    class Box {
      List<String> result;
    }
    Box box = new Box();
    Retrace.run(
        RetraceCommand.builder()
            .setProguardMapProducer(() -> map)
            .setStackTrace(
                stackTraceLines.stream()
                    .map(line -> line.originalLine)
                    .collect(Collectors.toList()))
            .setRetracedStackTraceConsumer(retraced -> box.result = retraced)
            .build());
    // Keep the original stderr in the retraced stacktrace.
    return new StackTrace(internalExtractFromJvm(StringUtils.lines(box.result)), originalStderr);
  }

  public StackTrace filter(Predicate<StackTraceLine> filter) {
    return new StackTrace(
        stackTraceLines.stream().filter(filter).collect(Collectors.toList()), originalStderr);
  }

  @Override
  public int hashCode() {
    return stackTraceLines.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof StackTrace) {
      return stackTraceLines.equals(((StackTrace) other).stackTraceLines);
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return toStringWithPrefix("");
  }

  public String toStringWithPrefix(String prefix) {
    StringBuilder builder = new StringBuilder();
    for (StackTraceLine stackTraceLine : stackTraceLines) {
      builder.append(prefix).append(stackTraceLine).append(System.lineSeparator());
    }
    return builder.toString();
  }

  public abstract static class StackTraceEquivalence extends Equivalence<StackTrace> {
    public abstract Equivalence<StackTrace.StackTraceLine> getLineEquivalence();

    @Override
    protected boolean doEquivalent(StackTrace a, StackTrace b) {
      if (a.stackTraceLines.size() != b.stackTraceLines.size()) {
        return false;
      }
      for (int i = 0; i < a.stackTraceLines.size(); i++) {
        if (!getLineEquivalence().equivalent(a.stackTraceLines.get(i), b.stackTraceLines.get(i))) {
          return false;
        }
      }
      return true;
    }

    @Override
    protected int doHash(StackTrace stackTrace) {
      int hashCode = stackTrace.size() * 13;
      for (StackTrace.StackTraceLine stackTraceLine : stackTrace.stackTraceLines) {
        hashCode += (hashCode << 4) + getLineEquivalence().hash(stackTraceLine);
      }
      return hashCode;
    }
  }

  // Equivalence forwarding to default stack trace comparison.
  public static class EquivalenceFull extends StackTraceEquivalence {

    private static class LineEquivalence extends Equivalence<StackTrace.StackTraceLine> {

      private static final LineEquivalence INSTANCE = new LineEquivalence();

      public static LineEquivalence get() {
        return INSTANCE;
      }

      @Override
      protected boolean doEquivalent(StackTrace.StackTraceLine a, StackTrace.StackTraceLine b) {
        return a.equals(b);
      }

      @Override
      protected int doHash(StackTrace.StackTraceLine stackTraceLine) {
        return stackTraceLine.hashCode();
      }
    }

    private static final EquivalenceFull INSTANCE = new EquivalenceFull();

    public static EquivalenceFull get() {
      return INSTANCE;
    }

    @Override
    public Equivalence<StackTrace.StackTraceLine> getLineEquivalence() {
      return LineEquivalence.get();
    }

    @Override
    protected boolean doEquivalent(StackTrace a, StackTrace b) {
      return a.equals(b);
    }

    @Override
    protected int doHash(StackTrace stackTrace) {
      return stackTrace.hashCode();
    }
  }

  // Equivalence comparing stack traces without taking the file name into account.
  public static class EquivalenceWithoutFileName extends StackTraceEquivalence {

    private static class LineEquivalence extends Equivalence<StackTrace.StackTraceLine> {

      private static final LineEquivalence INSTANCE = new LineEquivalence();

      public static LineEquivalence get() {
        return INSTANCE;
      }

      @Override
      protected boolean doEquivalent(StackTrace.StackTraceLine a, StackTrace.StackTraceLine b) {
        return a.className.equals(b.className)
            && a.methodName.equals(b.methodName)
            && a.lineNumber == b.lineNumber;
      }

      @Override
      protected int doHash(StackTrace.StackTraceLine stackTraceLine) {
        return stackTraceLine.className.hashCode() * 13
            + stackTraceLine.methodName.hashCode() * 7
            + stackTraceLine.lineNumber;
      }
    }

    private static final EquivalenceWithoutFileName INSTANCE = new EquivalenceWithoutFileName();

    public static EquivalenceWithoutFileName get() {
      return INSTANCE;
    }

    @Override
    public Equivalence<StackTrace.StackTraceLine> getLineEquivalence() {
      return LineEquivalence.get();
    }
  }

  // Equivalence comparing stack traces without taking the file name and line number into account.
  public static class EquivalenceWithoutFileNameAndLineNumber extends StackTraceEquivalence {

    private static final EquivalenceWithoutFileNameAndLineNumber INSTANCE =
        new EquivalenceWithoutFileNameAndLineNumber();

    public static EquivalenceWithoutFileNameAndLineNumber get() {
      return INSTANCE;
    }

    public static class LineEquivalence extends Equivalence<StackTrace.StackTraceLine> {

      private static final LineEquivalence INSTANCE = new LineEquivalence();

      public static LineEquivalence get() {
        return INSTANCE;
      }

      @Override
      protected boolean doEquivalent(StackTrace.StackTraceLine a, StackTrace.StackTraceLine b) {
        return a.className.equals(b.className) && a.methodName.equals(b.methodName);
      }

      @Override
      protected int doHash(StackTrace.StackTraceLine stackTraceLine) {
        return stackTraceLine.className.hashCode() * 13 + stackTraceLine.methodName.hashCode() * 7;
      }
    }

    @Override
    public Equivalence<StackTrace.StackTraceLine> getLineEquivalence() {
      return LineEquivalence.get();
    }
  }

  public static class StackTraceMatcherBase extends TypeSafeMatcher<StackTrace> {
    private final StackTrace expected;
    private final StackTraceEquivalence equivalence;
    private final String comparisonDescription;

    private StackTraceMatcherBase(
        StackTrace expected, StackTraceEquivalence equivalence, String comparisonDescription) {
      this.expected = expected;
      this.equivalence = equivalence;
      this.comparisonDescription = comparisonDescription;
    }

    @Override
    public boolean matchesSafely(StackTrace stackTrace) {
      return equivalence.equivalent(expected, stackTrace);
    }

    @Override
    public void describeTo(Description description) {
      description
          .appendText("stacktrace " + comparisonDescription)
          .appendText(System.lineSeparator())
          .appendText(expected.toString());
    }

    @Override
    public void describeMismatchSafely(final StackTrace stackTrace, Description description) {
      description.appendText("stacktrace was");
      description.appendText(System.lineSeparator());
      description.appendText(stackTrace.toString());
      description.appendText(System.lineSeparator());
      if (expected.size() != stackTrace.size()) {
        description.appendText("They have different sizes.");
      } else {
        for (int i = 0; i < expected.size(); i++) {
          if (!equivalence.getLineEquivalence().equivalent(expected.get(i), stackTrace.get(i))) {
            description
                .appendText("First different entry is index " + i + ":")
                .appendText(System.lineSeparator())
                .appendText("Expected: " + expected.get(i))
                .appendText(System.lineSeparator())
                .appendText("     Was: " + stackTrace.get(i));
            return;
          }
        }
      }
    }
  }

  public static class StackTraceMatcher extends StackTraceMatcherBase {
    private StackTraceMatcher(StackTrace expected) {
      super(expected, EquivalenceFull.get(), "");
    }
  }

  public static Matcher<StackTrace> isSame(StackTrace stackTrace) {
    return new StackTraceMatcher(stackTrace);
  }

  public static class StackTraceIgnoreFileNameMatcher extends StackTraceMatcherBase {
    private StackTraceIgnoreFileNameMatcher(StackTrace expected) {
      super(expected, EquivalenceWithoutFileName.get(), "(ignoring file name)");
    }
  }

  public static Matcher<StackTrace> isSameExceptForFileName(StackTrace stackTrace) {
    return new StackTraceIgnoreFileNameMatcher(stackTrace);
  }

  public static class StackTraceIgnoreFileNameAndLineNumberMatcher extends StackTraceMatcherBase {
    private StackTraceIgnoreFileNameAndLineNumberMatcher(StackTrace expected) {
      super(
          expected,
          EquivalenceWithoutFileNameAndLineNumber.get(),
          "(ignoring file name and line number)");
    }
  }

  public static Matcher<StackTrace> isSameExceptForFileNameAndLineNumber(StackTrace stackTrace) {
    return new StackTraceIgnoreFileNameAndLineNumberMatcher(stackTrace);
  }
}
