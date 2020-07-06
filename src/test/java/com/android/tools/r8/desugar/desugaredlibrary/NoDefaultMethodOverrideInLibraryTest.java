// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * This test checks that if a default interface method in a library is not overridden by a class
 * method in the library, then a program defined maximally specific method becomes the target if
 * present.
 *
 * <p>Concretely, Collection defines a default removeIf() method which is not overridden in either
 * the List interface or the LinkedList class. Thus, any class deriving LinkedList for which
 * removeIf is overridden or a new default method is added should target those extensions.
 */
@RunWith(Parameterized.class)
public class NoDefaultMethodOverrideInLibraryTest extends DesugaredLibraryTestBase {

  static final String EXPECTED = StringUtils.lines("MyIntegerList::removeIf", "false", "false");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public NoDefaultMethodOverrideInLibraryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    TestRuntime systemRuntime = TestRuntime.getSystemRuntime();
    if (systemRuntime.isCf() && systemRuntime.asCf().isNewerThanOrEqual(CfVm.JDK8)) {
      // This test assumes that the library defines a Collection class with a default removeIf.
      Method removeIf = Collection.class.getDeclaredMethod("removeIf", Predicate.class);
      assertNotNull(removeIf);
      assertTrue(removeIf.isDefault());
      // Also, the LinkedList implementation does *not* define an override of removeIf.
      try {
        LinkedList.class.getDeclaredMethod("removeIf", Predicate.class);
        fail("Unexpected defintion of removeIf on LinkedList");
      } catch (NoSuchMethodException e) {
        // Expected.
      }
    }
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addInnerClasses(NoDefaultMethodOverrideInLibraryTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED);
    } else {
      testForD8()
          .setMinApi(parameters.getApiLevel())
          .addInnerClasses(NoDefaultMethodOverrideInLibraryTest.class)
          .enableCoreLibraryDesugaring(parameters.getApiLevel())
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              this::buildDesugaredLibrary, parameters.getApiLevel())
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED);
    }
  }

  // Custom list interface with a default method for removeIf.
  interface MyIntegerList extends List<Integer> {

    @Override
    default boolean removeIf(Predicate<? super Integer> filter) {
      System.out.println("MyIntegerList::removeIf");
      return false;
    }
  }

  // Derived list with no override of removeIf but with a default method in MyIntegerList.
  // The call will thus go to the maximally specific method, which is MyIntegerList::removeIf.
  static class MyIntegerLinkedListWithoutOverride extends LinkedList<Integer>
      implements MyIntegerList {
    // No override of spliterator.
  }

  // Derived list with an override of removeIf. The call must hit the classes override and that
  // will explictly call the library default method (Collection.removeIf).
  static class MyIntegerLinkedListWithOverride extends LinkedList<Integer>
      implements MyIntegerList {

    @Override
    public boolean removeIf(Predicate<? super Integer> filter) {
      return super.removeIf(filter);
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new MyIntegerLinkedListWithoutOverride().removeIf(e -> false));
      System.out.println(new MyIntegerLinkedListWithOverride().removeIf(e -> false));
    }
  }
}
