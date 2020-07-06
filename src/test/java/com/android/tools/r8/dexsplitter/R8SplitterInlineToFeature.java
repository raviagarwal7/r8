// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableSet;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * We don't allow to inline code from the base into a feature, but we do allow inlining code from
 * the the base into the feature.
 */
@RunWith(Parameterized.class)
public class R8SplitterInlineToFeature extends SplitterTestBase {

  public static final String EXPECTED = StringUtils.lines("42");

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withDexRuntimes().build();
  }

  private final TestParameters parameters;

  public R8SplitterInlineToFeature(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInlining() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Consumer<R8FullTestBuilder> configurator =
        r8FullTestBuilder -> r8FullTestBuilder.enableMergeAnnotations().noMinification();
    ThrowingConsumer<R8TestCompileResult, Exception> ensureInlined =
        r8TestCompileResult -> {
          // Ensure that isEarly from BaseUtilClass is inlined into the feature
          ClassSubject clazz = r8TestCompileResult.inspector().clazz(BaseUtilClass.class);
          assertThat(clazz.uniqueMethodWithName("isEarly"), not(isPresent()));
        };
    ProcessResult processResult =
        testR8Splitter(
            parameters,
            ImmutableSet.of(BaseSuperClass.class, BaseUtilClass.class),
            ImmutableSet.of(FeatureClass.class),
            FeatureClass.class,
            ensureInlined,
            configurator);

    assertEquals(processResult.exitCode, 0);
    assertEquals(processResult.stdout, StringUtils.lines("42"));
  }

  @NeverMerge
  public abstract static class BaseSuperClass implements RunInterface {
    public void run() {
      System.out.println(getFromFeature());
    }

    public abstract String getFromFeature();
  }

  public static class BaseUtilClass {
    public static boolean isEarly() {
      return System.currentTimeMillis() < 2;
    }
  }

  public static class FeatureClass extends BaseSuperClass {
    @Override
    public String getFromFeature() {
      if (BaseUtilClass.isEarly()) {
        return "Very early";
      } else {
        return "42";
      }
    }
  }
}
