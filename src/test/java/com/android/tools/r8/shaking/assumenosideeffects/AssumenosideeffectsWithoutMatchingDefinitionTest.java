// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssumenosideeffectsWithoutMatchingDefinitionTest extends TestBase {
  private static final Class<?> MAIN = ProgramClass.class;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("production");

  enum TestConfig {
    RULE_THAT_DIRECTLY_REFERS_BASE,
    RULE_THAT_DIRECTLY_REFERS_SUB,
    RULE_WITH_EXTENDS_BASE,
    RULE_WITH_EXTENDS_SUB;

    public String getKeepRule() {
      switch (this) {
        case RULE_THAT_DIRECTLY_REFERS_BASE:
          return StringUtils.lines(
              "-assumenosideeffects class " + LibraryBase.class.getTypeName() + " {",
              "  boolean isInEditMode() return false;",
              "}");
        case RULE_THAT_DIRECTLY_REFERS_SUB:
          return StringUtils.lines(
              "-assumenosideeffects class " + LibrarySub.class.getTypeName() + " {",
              "  boolean isInEditMode() return false;",
              "}");
        case RULE_WITH_EXTENDS_BASE:
          return StringUtils.lines(
              "-assumenosideeffects class * extends " + LibraryBase.class.getTypeName() + " {",
              "  boolean isInEditMode() return false;",
              "}");
        case RULE_WITH_EXTENDS_SUB:
          return StringUtils.lines(
              "-assumenosideeffects class * extends " + LibrarySub.class.getTypeName() + " {",
              "  boolean isInEditMode() return false;",
              "}");
      }
      throw new Unreachable();
    }

    public void inspect(CodeInspector inspector) {
      ClassSubject main = inspector.clazz(MAIN);
      assertThat(main, isPresent());

      MethodSubject init = main.init();
      assertThat(init, isPresent());
      assertTrue(init.streamInstructions().noneMatch(i -> i.isIf() || i.isIfNez() || i.isIfEqz()));
    }
  }

  @Parameterized.Parameters(name = "{0} {1}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimes().build(), TestConfig.values());
  }

  private final TestParameters parameters;
  private final TestConfig config;

  public AssumenosideeffectsWithoutMatchingDefinitionTest(
      TestParameters parameters, TestConfig config) {
    this.parameters = parameters;
    this.config = config;
  }

  @ClassRule
  public static TemporaryFolder staticTemp = ToolHelper.getTemporaryFolderForTest();

  private static Path libJarPath;
  private static Path libDexPath;

  @BeforeClass
  public static void prepareLibJar() throws Exception {
    libJarPath = staticTemp.newFile("lib.jar").toPath().toAbsolutePath();
    writeClassesToJar(libJarPath, ImmutableList.of(LibraryBase.class, LibrarySub.class));
    libDexPath = staticTemp.newFile("lib.dex").toPath().toAbsolutePath();
    testForD8(staticTemp)
        .addProgramFiles(libJarPath)
        .setMinApi(AndroidApiLevel.B)
        .setProgramConsumer(new ArchiveConsumer(libDexPath))
        .compile();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(libJarPath)
        .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
        .addProgramClasses(MAIN)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRule())
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .compile()
        .addRunClasspathFiles(parameters.isDexRuntime() ? libDexPath : libJarPath)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(config::inspect);
  }

  static class LibraryBase {
    boolean isInEditMode() {
      return System.currentTimeMillis() <= 0;
    }
  }

  static class LibrarySub extends LibraryBase {}

  static class ProgramClass extends LibrarySub {
    private final String test;

    private ProgramClass() {
      if (isInEditMode()) {
        test = "test";
      } else {
        test = "production";
      }
    }

    public static void main(String... args) {
      System.out.println(new ProgramClass().test);
    }
  }
}
