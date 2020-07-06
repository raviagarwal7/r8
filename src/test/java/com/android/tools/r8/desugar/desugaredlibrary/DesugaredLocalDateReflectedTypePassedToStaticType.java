// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.time.LocalDate;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLocalDateReflectedTypePassedToStaticType extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final String EXPECTED = StringUtils.lines("1992");

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  public DesugaredLocalDateReflectedTypePassedToStaticType(
      TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.parameters = parameters;
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
  }

  @Test
  public void testD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    D8TestRunResult runResult =
        testForD8()
            .addInnerClasses(DesugaredLocalDateReflectedTypePassedToStaticType.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .setIncludeClassesChecksum(true)
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(parameters.getRuntime(), Main.class);
    if (shrinkDesugaredLibrary && parameters.getApiLevel().isLessThan(AndroidApiLevel.O)) {
      runResult.assertFailureWithErrorThatMatches(
          containsString("java.lang.NoSuchMethodException"));
    } else {
      runResult.assertSuccessWithOutput(EXPECTED);
    }
  }

  @Test
  public void testR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(DesugaredLocalDateReflectedTypePassedToStaticType.class)
            .addKeepMainRule(Main.class)
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                shrinkDesugaredLibrary)
            .run(parameters.getRuntime(), Main.class);
    if (shrinkDesugaredLibrary && parameters.getApiLevel().isLessThan(AndroidApiLevel.O)) {
      runResult.assertFailureWithErrorThatMatches(
          containsString("java.lang.NoSuchMethodException"));
    } else {
      runResult.assertSuccessWithOutput(EXPECTED);
    }
  }

  public static class Main {

    public static void printYear(LocalDate date) {
      System.out.println(date.getYear());
    }

    public static void main(String[] args) throws Exception {
      LocalDate date =
          (LocalDate)
              LocalDate.class
                  .getMethod("of", int.class, int.class, int.class)
                  .invoke(null, 1992, 1, 1);
      printYear(date);
    }
  }
}
