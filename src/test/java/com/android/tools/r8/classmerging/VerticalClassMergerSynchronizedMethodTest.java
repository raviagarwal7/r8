// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import java.io.IOException;
import java.lang.Thread.State;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerSynchronizedMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // The 4.0.4 runtime will flakily mark threads as blocking and report DEADLOCKED.
    return getTestParameters()
        .withDexRuntimesStartingFromExcluding(Version.V4_0_4)
        .withAllApiLevels()
        .build();
  }

  public VerticalClassMergerSynchronizedMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testOnRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addInnerClasses(VerticalClassMergerSynchronizedMethodTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput("Hello World!");
  }

  @Test
  public void testNoMergingOfClassUsedInMonitor()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(VerticalClassMergerSynchronizedMethodTest.class)
        .addKeepMainRule(Main.class)
        .enableMergeAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput("Hello World!")
        .inspect(inspector -> assertThat(inspector.clazz(LockOne.class), isPresent()));
  }

  private interface I {

    void action();
  }

  // Will be merged into LockTwo.
  abstract static class LockOne {

    static synchronized void acquire(I c) {
      c.action();
    }
  }

  @NeverMerge
  public static class LockTwo extends LockOne {

    static synchronized void acquire(I c) {
      Main.inTwoCritical = true;
      while (!Main.inThreeCritical) {}
      c.action();
    }
  }

  @NeverMerge
  public static class LockThree {

    static synchronized void acquire(I c) {
      Main.inThreeCritical = true;
      while (!Main.inTwoCritical) {}
      c.action();
    }
  }

  public static class AcquireOne implements I {

    @Override
    public void action() {
      LockOne.acquire(() -> System.out.print("Hello "));
    }
  }

  public static class AcquireThree implements I {

    @Override
    public void action() {
      LockThree.acquire(() -> System.out.print("World!"));
    }
  }

  public static class Main {

    static volatile boolean inTwoCritical = false;
    static volatile boolean inThreeCritical = false;
    static volatile boolean arnoldWillNotBeBack = false;

    private static volatile Thread t1 = new Thread(Main::lockThreeThenOne);
    private static volatile Thread t2 = new Thread(Main::lockTwoThenThree);
    private static volatile Thread terminator = new Thread(Main::arnold);

    public static void main(String[] args) {
      t1.start();
      t2.start();
      // This thread is started to ensure termination in case we are rewriting incorrectly.
      terminator.start();

      while (!arnoldWillNotBeBack) {}
    }

    static void lockThreeThenOne() {
      LockThree.acquire(new AcquireOne());
    }

    static void lockTwoThenThree() {
      LockTwo.acquire(new AcquireThree());
    }

    static void arnold() {
      while (t1.getState() != State.TERMINATED || t2.getState() != State.TERMINATED) {
        if (t1.getState() == State.BLOCKED && t2.getState() == State.BLOCKED) {
          System.err.println("DEADLOCKED!");
          System.exit(1);
          break;
        }
      }
      arnoldWillNotBeBack = true;
    }
  }
}
