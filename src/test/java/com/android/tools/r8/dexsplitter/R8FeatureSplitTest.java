// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dexsplitter;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ExtractMarker;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8FeatureSplitTest extends SplitterTestBase {

  private static String EXPECTED = "Hello world";

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withDexRuntimes().build();
  }

  private final TestParameters parameters;

  public R8FeatureSplitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static FeatureSplit emptySplitProvider(FeatureSplit.Builder builder) {
    builder
        .addProgramResourceProvider(ImmutableList::of)
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    return builder.build();
  }

  @Test
  public void simpleApiTest() throws CompilationFailedException, IOException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(HelloWorld.class)
        .setMinApi(parameters.getRuntime())
        .addFeatureSplit(R8FeatureSplitTest::emptySplitProvider)
        .addKeepMainRule(HelloWorld.class)
        .compile()
        .run(parameters.getRuntime(), HelloWorld.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testTwoFeatures() throws CompilationFailedException, IOException, ExecutionException {
    CompiledWithFeature compiledWithFeature = new CompiledWithFeature().invoke();
    Path basePath = compiledWithFeature.getBasePath();
    Path feature1Path = compiledWithFeature.getFeature1Path();
    Path feature2Path = compiledWithFeature.getFeature2Path();
    CodeInspector baseInspector = new CodeInspector(basePath);
    assertTrue(baseInspector.clazz(BaseClass.class).isPresent());

    CodeInspector feature1Inspector = new CodeInspector(feature1Path);
    assertEquals(feature1Inspector.allClasses().size(), 1);
    assertTrue(feature1Inspector.clazz(FeatureClass.class).isPresent());

    CodeInspector feature2Inspector = new CodeInspector(feature2Path);
    assertEquals(feature2Inspector.allClasses().size(), 1);
    assertTrue(feature2Inspector.clazz(FeatureClass2.class).isPresent());

    // Sanity check, we can't call the Feature from the base directly.
    ProcessResult result =
        runFeatureOnArt(BaseClass.class, basePath, feature1Path, parameters.getRuntime());
    assertTrue(result.exitCode != 0);

    result = runFeatureOnArt(FeatureClass.class, basePath, feature1Path, parameters.getRuntime());
    assertEquals(result.exitCode, 0);
    assertEquals(result.stdout, StringUtils.lines("Testing base", "Testing feature"));

    result = runFeatureOnArt(FeatureClass2.class, basePath, feature2Path, parameters.getRuntime());
    assertEquals(result.exitCode, 0);
    assertEquals(result.stdout, StringUtils.lines("Testing second"));
  }

  @Test
  public void testMarkerInFeatures()
      throws IOException, CompilationFailedException, ExecutionException, ResourceException {
    CompiledWithFeature compiledWithFeature = new CompiledWithFeature().invoke();
    Path basePath = compiledWithFeature.getBasePath();
    Path feature1Path = compiledWithFeature.getFeature1Path();
    Path feature2Path = compiledWithFeature.getFeature2Path();
    Collection<Marker> markers = ExtractMarker.extractMarkerFromDexFile(basePath);
    Collection<Marker> feature1Markers = ExtractMarker.extractMarkerFromDexFile(feature1Path);
    Collection<Marker> feature2Markers = ExtractMarker.extractMarkerFromDexFile(feature2Path);

    assertEquals(markers.size(), 1);
    assertEquals(feature1Markers.size(), 1);
    assertEquals(feature2Markers.size(), 1);
    assertEquals(markers.iterator().next(), feature1Markers.iterator().next());
    assertEquals(markers.iterator().next(), feature2Markers.iterator().next());
  }

  @Test
  public void testNonJavaPassThrough() throws IOException, CompilationFailedException {
    Path basePath = temp.newFile("base.zip").toPath();
    Path feature1Path = temp.newFile("feature1.zip").toPath();
    Path feature2Path = temp.newFile("feature2.zip").toPath();
    Collection<String> nonJavaFiles = ImmutableList.of("foobar", "barfoo");

    testForR8(parameters.getBackend())
        .addProgramClasses(BaseClass.class, RunInterface.class, SplitRunner.class)
        .setMinApi(parameters.getRuntime())
        .addFeatureSplit(
            builder ->
                splitWithNonJavaFile(
                    builder, feature1Path, temp, nonJavaFiles, true, FeatureClass.class))
        .addFeatureSplit(
            builder ->
                splitWithNonJavaFile(
                    builder, feature2Path, temp, nonJavaFiles, true, FeatureClass2.class))
        .addKeepAllClassesRule()
        .compile()
        .writeToZip(basePath);
    for (Path feature : ImmutableList.of(feature1Path, feature2Path)) {
      ZipFile zipFile = new ZipFile(feature.toFile());
      for (String nonJavaFile : nonJavaFiles) {
        ZipEntry entry = zipFile.getEntry(nonJavaFile);
        assertNotNull(entry);
        String content = new String(ByteStreams.toByteArray(zipFile.getInputStream(entry)));
        assertEquals(content, nonJavaFile);
      }
    }
    ZipFile zipFile = new ZipFile(basePath.toFile());
    for (String nonJavaFile : nonJavaFiles) {
      ZipEntry entry = zipFile.getEntry(nonJavaFile);
      assertNull(entry);
    }
  }

  @Test
  public void testAdaptResourceContentInSplits() throws IOException, CompilationFailedException {
    Path basePath = temp.newFile("base.zip").toPath();
    Path feature1Path = temp.newFile("feature1.zip").toPath();
    Path feature2Path = temp.newFile("feature2.zip").toPath();
    // Make the content of the data resource be class names
    Collection<String> nonJavaFiles =
        ImmutableList.of(FeatureClass.class.getName(), FeatureClass2.class.getName());

    testForR8(parameters.getBackend())
        .addProgramClasses(BaseClass.class, RunInterface.class, SplitRunner.class)
        .setMinApi(parameters.getRuntime())
        .addFeatureSplit(
            builder ->
                splitWithNonJavaFile(
                    builder, feature1Path, temp, nonJavaFiles, false, FeatureClass.class))
        .addFeatureSplit(
            builder ->
                splitWithNonJavaFile(
                    builder, feature2Path, temp, nonJavaFiles, false, FeatureClass2.class))
        .addKeepClassRulesWithAllowObfuscation(
            BaseClass.class, FeatureClass.class, FeatureClass2.class)
        .addKeepRules("-adaptresourcefilecontents")
        .compile()
        .writeToZip(basePath);
    for (Path feature : ImmutableList.of(feature1Path, feature2Path)) {
      ZipFile zipFile = new ZipFile(feature.toFile());
      for (String nonJavaFile : nonJavaFiles) {
        ZipEntry entry = zipFile.getEntry(nonJavaFile);
        assertNotNull(entry);
        String content = new String(ByteStreams.toByteArray(zipFile.getInputStream(entry)));
        assertNotEquals(content, nonJavaFile);
      }
    }
    ZipFile zipFile = new ZipFile(basePath.toFile());
    for (String nonJavaFile : nonJavaFiles) {
      ZipEntry entry = zipFile.getEntry(nonJavaFile);
      assertNull(entry);
    }
  }

  public static class HelloWorld {
    public static void main(String[] args) {
      System.out.println("Hello world");
    }
  }

  public static class BaseClass implements RunInterface {
    @Override
    public void run() {
      new FeatureClass().test();
    }

    public void test() {
      System.out.println("Testing base");
    }
  }

  public static class FeatureClass implements RunInterface {
    public void test() {
      System.out.println("Testing feature");
    }

    @Override
    public void run() {
      new BaseClass().test();
      test();
    }
  }

  public static class FeatureClass2 implements RunInterface {
    public void test() {
      System.out.println("Testing second");
    }

    @Override
    public void run() {
      test();
    }
  }

  private class CompiledWithFeature {

    private Path basePath;
    private Path feature1Path;
    private Path feature2Path;

    public Path getBasePath() {
      return basePath;
    }

    public Path getFeature1Path() {
      return feature1Path;
    }

    public Path getFeature2Path() {
      return feature2Path;
    }


    public CompiledWithFeature invoke() throws IOException, CompilationFailedException {
      basePath = temp.newFile("base.zip").toPath();
      feature1Path = temp.newFile("feature1.zip").toPath();
      feature2Path = temp.newFile("feature2.zip").toPath();

      testForR8(parameters.getBackend())
          .addProgramClasses(BaseClass.class, RunInterface.class, SplitRunner.class)
          .setMinApi(parameters.getRuntime())
          .addFeatureSplit(
              builder -> simpleSplitProvider(builder, feature1Path, temp, FeatureClass.class))
          .addFeatureSplit(
              builder -> simpleSplitProvider(builder, feature2Path, temp, FeatureClass2.class))
          .addKeepAllClassesRule()
          .compile()
          .writeToZip(basePath);
      return this;
    }
  }
}
