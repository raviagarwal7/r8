// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantconstnumber;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B124152497 extends TestBase {

  private final Class<?> clazz;
  private final boolean forceRedundantConstNumberRemovalOnDalvik;
  private final TestParameters parameters;

  @Parameters(name = "{2}, class: {0}, force redundant const number removal: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(
            BooleanTestClass.class,
            ByteTestClass.class,
            CharTestClass.class,
            FloatTestClass.class,
            ShortTestClass.class),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimes().build());
  }

  public B124152497(
      Class<?> clazz, boolean forceRedundantConstNumberRemovalOnDalvik, TestParameters parameters) {
    this.clazz = clazz;
    this.forceRedundantConstNumberRemovalOnDalvik = forceRedundantConstNumberRemovalOnDalvik;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    assumeTrue(!forceRedundantConstNumberRemovalOnDalvik || isDalvik());
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(clazz)
            .addKeepMainRule(clazz)
            .addOptionsModification(
                options -> {
                  options.enableRedundantConstNumberOptimization = true;
                  if (forceRedundantConstNumberRemovalOnDalvik) {
                    assertTrue(isDalvik());
                    options.testing.forceRedundantConstNumberRemoval = true;
                  }
                })
            .enableInliningAnnotations()
            .setMinApi(parameters.getRuntime())
            .compile()
            .run(parameters.getRuntime(), clazz);
    if (isExpectedToSucceed()) {
      result.assertSuccessWithOutputLines(getExpectedOutput());
    } else {
      result.assertFailureWithErrorThatMatches(containsString("java.lang.VerifyError"));
    }
  }

  private boolean isDalvik() {
    return parameters.getRuntime().isDex()
        && parameters.getRuntime().asDex().getVm().getVersion().isOlderThanOrEqual(Version.V4_4_4);
  }

  private boolean isExpectedToSucceed() {
    return !isDalvik()
        || !forceRedundantConstNumberRemovalOnDalvik
        || clazz == BooleanTestClass.class
        || clazz == FloatTestClass.class;
  }

  private String getExpectedOutput() {
    if (clazz == BooleanTestClass.class) {
      return "true";
    }
    if (clazz == ByteTestClass.class) {
      return "42";
    }
    if (clazz == CharTestClass.class) {
      return "*";
    }
    if (clazz == FloatTestClass.class) {
      return "42.0";
    }
    if (clazz == ShortTestClass.class) {
      return "42";
    }
    throw new Unreachable();
  }

  static class BooleanTestClass {

    public static void main(String[] args) {
      int value = getInt();
      if (value == 1) {
        consumeBoolean(true);
      }
    }

    @NeverInline
    private static int getInt() {
      return System.currentTimeMillis() >= 0 ? 1 : 0;
    }

    @NeverInline
    private static void consumeBoolean(boolean value) {
      System.out.println(value);
    }
  }

  static class ByteTestClass {

    public static void main(String[] args) {
      int value = getInt();
      if (value == 42) {
        consumeByte((byte) 42);
      }
    }

    @NeverInline
    private static int getInt() {
      return System.currentTimeMillis() >= 0 ? 42 : 0;
    }

    @NeverInline
    private static void consumeByte(byte value) {
      System.out.println(value);
    }
  }

  static class CharTestClass {

    public static void main(String[] args) {
      int value = getInt();
      if (value == 42) {
        consumeChar('*'); // '*' == (char) 42
      }
    }

    @NeverInline
    private static int getInt() {
      return System.currentTimeMillis() >= 0 ? 42 : 0;
    }

    @NeverInline
    private static void consumeChar(char value) {
      System.out.println(value);
    }
  }

  static class FloatTestClass {

    public static void main(String[] args) {
      int value = getInt();
      if (value == 42) {
        consumeFloat(42);
      }
    }

    @NeverInline
    private static int getInt() {
      return System.currentTimeMillis() >= 0 ? 42 : 0;
    }

    @NeverInline
    private static void consumeFloat(float value) {
      System.out.println(value);
    }
  }

  static class ShortTestClass {

    public static void main(String[] args) {
      int value = getInt();
      if (value == 42) {
        consumeShort((short) 42);
      }
    }

    @NeverInline
    private static int getInt() {
      return System.currentTimeMillis() >= 0 ? 42 : 0;
    }

    @NeverInline
    private static void consumeShort(short value) {
      System.out.println(value);
    }
  }
}
