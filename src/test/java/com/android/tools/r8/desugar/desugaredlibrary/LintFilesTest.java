// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.GenerateLintFiles;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfigurationParser;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;

public class LintFilesTest extends TestBase {

  private void checkFileContent(AndroidApiLevel minApiLevel, Path lintFile) throws Exception {
    // Just do some light probing in the generated lint files.
    List<String> methods = FileUtils.readAllLines(lintFile);

    // All methods supported on Optional*.
    assertTrue(methods.contains("java/util/Optional"));
    assertTrue(methods.contains("java/util/OptionalInt"));

    // No parallel* methods pre L, and all stream methods supported from L.
    assertEquals(
        minApiLevel == AndroidApiLevel.L,
        methods.contains("java/util/Collection#parallelStream()Ljava/util/stream/Stream;"));
    assertEquals(
        minApiLevel == AndroidApiLevel.L, methods.contains("java/util/stream/DoubleStream"));
    assertFalse(
        methods.contains(
            "java/util/stream/DoubleStream#parallel()Ljava/util/stream/DoubleStream;"));
    assertFalse(
        methods.contains("java/util/stream/DoubleStream#parallel()Ljava/util/stream/BaseStream;"));
    assertEquals(
        minApiLevel == AndroidApiLevel.B,
        methods.contains(
            "java/util/stream/DoubleStream#allMatch(Ljava/util/function/DoublePredicate;)Z"));
    assertEquals(minApiLevel == AndroidApiLevel.L, methods.contains("java/util/stream/IntStream"));
    assertFalse(
        methods.contains("java/util/stream/IntStream#parallel()Ljava/util/stream/IntStream;"));
    assertFalse(
        methods.contains("java/util/stream/IntStream#parallel()Ljava/util/stream/BaseStream;"));
    assertEquals(
        minApiLevel == AndroidApiLevel.B,
        methods.contains(
            "java/util/stream/IntStream#allMatch(Ljava/util/function/IntPredicate;)Z"));

    // Emulated interface default method.
    assertTrue(methods.contains("java/util/List#spliterator()Ljava/util/Spliterator;"));

    // Emulated interface static method.
    assertTrue(methods.contains("java/util/Map$Entry#comparingByValue()Ljava/util/Comparator;"));

    // No no-default method from emulated interface.
    assertFalse(methods.contains("java/util/List#size()I"));

    // File should be sorted.
    List<String> sorted = new ArrayList<>(methods);
    sorted.sort(Comparator.naturalOrder());
    assertEquals(methods, sorted);
  }

  @Test
  public void testFileContent() throws Exception {
    // Test require r8.jar not r8lib.jar, as the class com.android.tools.r8.GenerateLintFiles in
    // not kept.
    Assume.assumeTrue(!ToolHelper.isTestingR8Lib());

    Path directory = temp.newFolder().toPath();
    GenerateLintFiles.main(
        new String[] {ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING.toString(), directory.toString()});
    InternalOptions options = new InternalOptions(new DexItemFactory(), new Reporter());
    DesugaredLibraryConfiguration desugaredLibraryConfiguration =
        new DesugaredLibraryConfigurationParser(
                options.itemFactory, options.reporter, false, AndroidApiLevel.B.getLevel())
            .parse(StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING));

    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      if (apiLevel == AndroidApiLevel.R) {
        // Skip API level 30 for now.
        continue;
      }

      if (apiLevel.getLevel()
          >= desugaredLibraryConfiguration.getRequiredCompilationApiLevel().getLevel()) {
        Path compileApiLevelDirectory =
            directory.resolve("compile_api_level_" + apiLevel.getLevel());
        assertTrue(Files.exists(compileApiLevelDirectory));
        for (AndroidApiLevel minApiLevel : AndroidApiLevel.values()) {
          String desugaredApisBaseName =
              "desugared_apis_" + apiLevel.getLevel() + "_" + minApiLevel.getLevel();
          if (minApiLevel == AndroidApiLevel.L || minApiLevel == AndroidApiLevel.B) {
            assertTrue(
                Files.exists(compileApiLevelDirectory.resolve(desugaredApisBaseName + ".txt")));
            assertTrue(
                Files.exists(compileApiLevelDirectory.resolve(desugaredApisBaseName + ".jar")));
            checkFileContent(
                minApiLevel, compileApiLevelDirectory.resolve(desugaredApisBaseName + ".txt"));
          } else {
            assertFalse(
                Files.exists(compileApiLevelDirectory.resolve(desugaredApisBaseName + ".txt")));
            assertFalse(
                Files.exists(compileApiLevelDirectory.resolve(desugaredApisBaseName + ".jar")));
          }
        }
      }
    }
  }
}
