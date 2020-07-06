// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassObfuscationDictionaryDuplicateTest extends TestBase {

  public static class A {}

  public static class B {}

  public static class C {
    public static void main(String[] args) {
      System.out.print("HELLO WORLD!");
    }
  }

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassObfuscationDictionaryDuplicateTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws IOException, CompilationFailedException, ExecutionException {
    Path dictionary = temp.getRoot().toPath().resolve("dictionary.txt");
    FileUtils.writeTextFile(dictionary, "a");

    Set<String> finalNames = new HashSet<>();
    finalNames.add(A.class.getPackage().getName() + ".a");
    finalNames.add(B.class.getPackage().getName() + ".b");

    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, C.class)
        .noTreeShaking()
        .addKeepRules("-classobfuscationdictionary " + dictionary.toString())
        .addKeepMainRule(C.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), C.class)
        .assertSuccessWithOutput("HELLO WORLD!")
        .inspect(
            inspector -> {
              ClassSubject clazzA = inspector.clazz(A.class);
              assertThat(clazzA, isPresent());
              assertTrue(finalNames.contains(clazzA.getFinalName()));
              finalNames.remove(clazzA.getFinalName());
              ClassSubject clazzB = inspector.clazz(B.class);
              assertThat(clazzB, isPresent());
              assertTrue(finalNames.contains(clazzB.getFinalName()));
            });
  }
}
