// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatabilityTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

public class AdaptResourceFileContentsTest extends ProguardCompatabilityTestBase {

  private static class CustomDataResourceConsumer implements DataResourceConsumer {

    private final Map<String, ImmutableList<String>> resources = new HashMap<>();

    @Override
    public void accept(DataDirectoryResource directory, DiagnosticsHandler diagnosticsHandler) {
      throw new Unreachable();
    }

    @Override
    public void accept(DataEntryResource file, DiagnosticsHandler diagnosticsHandler) {
      try {
        byte[] bytes = ByteStreams.toByteArray(file.getByteStream());
        String contents = new String(bytes, Charset.defaultCharset());
        resources.put(file.getName(), ImmutableList.copyOf(contents.split(System.lineSeparator())));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {}

    public ImmutableList<String> get(String name) {
      return resources.get(name);
    }
  }

  private static final ImmutableList<String> originalAllChangedResource =
      ImmutableList.of(
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A<java.lang.String>",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A<"
              + "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A>",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          // Test property values are rewritten.
          "property=com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A",
          // Test XML content is rewritten.
          "<tag>com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A</tag>",
          "<tag attr=\"com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A\"></tag>",
          // Test single-quote literals are rewritten.
          "'com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A'");

  private static final ImmutableList<String> originalAllPresentResource =
      ImmutableList.of(
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B");

  private static final ImmutableList<String> originalAllUnchangedResource =
      ImmutableList.of(
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass",
          // Test there is no renaming for the method on A.
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A.method",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$A.method()",
          // Test there is no renaming for the methods on B.
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B.method",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B.method()",
          // Test various prefixes.
          "42com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "WithIdentifierPrefixcom.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "-com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "WithDashPrefix-com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "$com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "WithDollarPrefix$com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          ".com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          "WithDotPrefix.com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B",
          // Test various suffixes.
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B42",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$BWithIdentifierSuffix",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B-",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B-WithDashSuffix",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B$",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B$WithDollarSuffix",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B.",
          "com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B.WithDotSuffix");

  private static String getProguardConfig(
      boolean enableAdaptResourceFileContents, String adaptResourceFileContentsPathFilter) {
    String adaptResourceFileContentsRule;
    if (enableAdaptResourceFileContents) {
      adaptResourceFileContentsRule = "-adaptresourcefilecontents";
      if (adaptResourceFileContentsPathFilter != null) {
        adaptResourceFileContentsRule += " " + adaptResourceFileContentsPathFilter;
      }
    } else {
      adaptResourceFileContentsRule = "";
    }
    return String.join(
        System.lineSeparator(),
        adaptResourceFileContentsRule,
        "-keep class " + AdaptResourceFileContentsTestClass.class.getName() + " {",
        "  public static void main(...);",
        "}");
  }

  private static String getProguardConfigWithNeverInline(
      boolean enableAdaptResourceFileContents, String adaptResourceFileContentsPathFilter) {
    return String.join(
        System.lineSeparator(),
        getProguardConfig(enableAdaptResourceFileContents, adaptResourceFileContentsPathFilter),
        "-neverinline class com.android.tools.r8.naming.AdaptResourceFileContentsTestClass$B {",
        "  public void method();",
        "}");
  }

  @Test
  public void testEnabled() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    AndroidApp out =
        compileWithR8(getProguardConfigWithNeverInline(true, null), dataResourceConsumer);

    // Check that the data resources have changed as expected.
    checkAllAreChanged(
        dataResourceConsumer.get("resource-all-changed.md"), originalAllChangedResource);
    checkAllAreChanged(
        dataResourceConsumer.get("resource-all-changed.txt"), originalAllChangedResource);

    // Check that the new names are consistent with the actual application code.
    checkAllArePresent(
        dataResourceConsumer.get("resource-all-present.txt"), new CodeInspector(out));

    // Check that the data resources have not changed unexpectedly.
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.class"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.cLaSs"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-unchanged.txt"), originalAllUnchangedResource);
  }

  @Test
  public void testProguardBehavior() throws Exception {
    AndroidApp result =
        runProguard6Raw(
            ImmutableList.of(
                AdaptResourceFileContentsTestClass.class,
                AdaptResourceFileContentsTestClass.A.class,
                AdaptResourceFileContentsTestClass.B.class),
            getProguardConfig(true, null),
            null,
            getDataResources()
                .stream()
                .filter(x -> !x.getName().toLowerCase().endsWith(FileUtils.CLASS_EXTENSION))
                .collect(Collectors.toList()));

    List<DataEntryResource> dataResources = getDataResources(result);
    assertEquals(4, dataResources.size());

    assertTrue(
        dataResources.stream().anyMatch(x -> x.getName().endsWith("resource-all-changed.md")));
    assertTrue(
        dataResources.stream().anyMatch(x -> x.getName().endsWith("resource-all-changed.txt")));
    assertTrue(
        dataResources.stream().anyMatch(x -> x.getName().endsWith("resource-all-present.txt")));
    assertTrue(
        dataResources.stream().anyMatch(x -> x.getName().endsWith("resource-all-unchanged.txt")));

    for (DataEntryResource dataResource : dataResources) {
      byte[] bytes = ByteStreams.toByteArray(dataResource.getByteStream());
      List<String> lines =
          Arrays.asList(new String(bytes, Charset.defaultCharset()).split(System.lineSeparator()));

      if (dataResource.getName().endsWith("resource-all-changed.md")) {
        checkAllAreChanged(lines, originalAllChangedResource);
      } else if (dataResource.getName().endsWith("resource-all-changed.txt")) {
        checkAllAreChanged(lines, originalAllChangedResource);
      } else if (dataResource.getName().endsWith("resource-all-present.txt")) {
        checkAllArePresent(lines, new CodeInspector(result));
      } else if (dataResource.getName().endsWith("resource-all-unchanged.txt")) {
        checkAllAreUnchanged(lines, originalAllUnchangedResource);
      }
    }
  }

  @Test
  public void testEnabledWithFilter() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    compileWithR8(getProguardConfigWithNeverInline(true, "*.md"), dataResourceConsumer);

    // Check that the file matching the filter has changed as expected.
    checkAllAreChanged(
        dataResourceConsumer.get("resource-all-changed.md"), originalAllChangedResource);

    // Check that all the other data resources are unchanged.
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.class"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.cLaSs"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.txt"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-present.txt"), originalAllPresentResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-unchanged.txt"), originalAllUnchangedResource);
  }

  @Test
  public void testDisabled() throws Exception {
    CustomDataResourceConsumer dataResourceConsumer = new CustomDataResourceConsumer();
    compileWithR8(getProguardConfigWithNeverInline(false, null), dataResourceConsumer);

    // Check that all data resources are unchanged.
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.class"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.cLaSs"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.md"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-changed.txt"), originalAllChangedResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-present.txt"), originalAllPresentResource);
    checkAllAreUnchanged(
        dataResourceConsumer.get("resource-all-unchanged.txt"), originalAllUnchangedResource);
  }

  private static void checkAllAreChanged(List<String> adaptedLines, List<String> originalLines) {
    assertEquals(adaptedLines.size(), originalLines.size());
    for (int i = 0; i < originalLines.size(); i++) {
      assertNotEquals(originalLines.get(i), adaptedLines.get(i));
    }
  }

  private static void checkAllArePresent(List<String> lines, CodeInspector inspector) {
    for (String line : lines) {
      assertThat(inspector.clazz(line), isPresent());
    }
  }

  private static void checkAllAreUnchanged(List<String> adaptedLines, List<String> originalLines) {
    assertEquals(adaptedLines.size(), originalLines.size());
    for (int i = 0; i < originalLines.size(); i++) {
      assertEquals(originalLines.get(i), adaptedLines.get(i));
    }
  }

  private AndroidApp compileWithR8(String proguardConfig, DataResourceConsumer dataResourceConsumer)
      throws CompilationFailedException, IOException {
    R8Command command =
        ToolHelper.allowTestProguardOptions(
                ToolHelper.prepareR8CommandBuilder(getAndroidApp())
                    .addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown()))
            .build();
    return ToolHelper.runR8(
        command,
        options -> {
          // TODO(christofferqa): Class inliner should respect -neverinline.
          options.enableClassInlining = false;
          options.enableClassMerging = true;
          options.dataResourceConsumer = dataResourceConsumer;
        });
  }

  private AndroidApp getAndroidApp() throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(
        ToolHelper.getClassFileForTestClass(AdaptResourceFileContentsTestClass.class),
        ToolHelper.getClassFileForTestClass(AdaptResourceFileContentsTestClass.A.class),
        ToolHelper.getClassFileForTestClass(AdaptResourceFileContentsTestClass.B.class));
    getDataResources().forEach(builder::addDataResource);
    return builder.build();
  }

  private List<DataEntryResource> getDataResources() {
    return ImmutableList.of(
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllChangedResource).getBytes(),
            "resource-all-changed.class",
            Origin.unknown()),
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllChangedResource).getBytes(),
            "resource-all-changed.cLaSs",
            Origin.unknown()),
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllChangedResource).getBytes(),
            "resource-all-changed.md",
            Origin.unknown()),
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllChangedResource).getBytes(),
            "resource-all-changed.txt",
            Origin.unknown()),
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllPresentResource).getBytes(),
            "resource-all-present.txt",
            Origin.unknown()),
        DataEntryResource.fromBytes(
            String.join(System.lineSeparator(), originalAllUnchangedResource).getBytes(),
            "resource-all-unchanged.txt",
            Origin.unknown()));
  }
}

class AdaptResourceFileContentsTestClass {

  public static void main(String[] args) {
    B obj = new B();
    obj.method();
  }

  static class A {

    public void method() {
      System.out.println("In A.method()");
    }
  }

  static class B extends A {

    public void method() {
      System.out.println("In B.method()");
      super.method();
    }
  }
}
