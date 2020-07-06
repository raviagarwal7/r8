// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Test single stepping behaviour of synchronized blocks. */
@RunWith(Parameterized.class)
public class SynchronizedBlockTest extends DebugTestBase {

  public static final String CLASS = "SynchronizedBlock";
  public static final String FILE = "SynchronizedBlock.java";

  private final String name;
  private final DebugTestConfig config;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> setup() {
    DelayedDebugTestConfig cf =
        temp -> new CfDebugTestConfig().addPaths(DebugTestBase.DEBUGGEE_JAR);
    DelayedDebugTestConfig r8cf = temp -> new R8CfDebugTestResourcesConfig(temp);
    DelayedDebugTestConfig d8 = temp -> new D8DebugTestResourcesConfig(temp);
    return ImmutableList.of(
        new Object[] {"CF", cf},
        new Object[] {"D8", d8},
        new Object[] {"R8/CF", r8cf});
  }

  public SynchronizedBlockTest(String name, DelayedDebugTestConfig config) {
    this.name = name;
    this.config = config.getConfig(temp);
  }

  @Test
  public void testEmptyBlock() throws Throwable {
    final String method = "emptyBlock";
    runDebugTest(
        config,
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 8),
        checkLocal("obj"),
        stepOver(),
        checkLine(FILE, 9),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 10),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 11),
        checkLocal("obj"),
        checkLocal("x"),
        checkLocal("y"),
        run());
  }

  @Test
  public void testNonThrowingBlock() throws Throwable {
    final String method = "nonThrowingBlock";
    runDebugTest(
        config,
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 15),
        checkLocal("obj"),
        stepOver(),
        checkLine(FILE, 16),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 17),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 18),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 19),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 20),
        checkLocal("obj"),
        checkLocal("x"),
        checkLocal("y"),
        run());
  }

  @Test
  public void testThrowingBlock() throws Throwable {
    Assume.assumeThat(
        "Connection timeout on 6.0.1 runtime. b/67671771",
        ToolHelper.getDexVm().getVersion(), not(DexVm.ART_6_0_1_TARGET.getVersion()));
    final String method = "throwingBlock";
    runDebugTest(
        config,
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 25),
        checkLocal("obj"),
        checkNoLocal("x"),
        stepOver(),
        checkLine(FILE, 26),
        checkLocal("obj"),
        checkLocal("x"),
        checkNoLocal("y"),
        stepOver(),
        checkLine(FILE, 27),
        checkLocal("obj"),
        checkLocal("x"),
        checkNoLocal("y"),
        stepOver(),
        checkLine(FILE, 28), // synchronized block end
        checkLocal("obj"),
        checkLocal("x"),
        checkNoLocal("y"),
        stepOver(),
        checkLine(FILE, 31), // catch handler
        checkLocal("obj"),
        checkNoLocal("x"),
        checkNoLocal("y"),
        stepOver(),
        run());
  }

  @Test
  public void testNestedNonThrowingBlock() throws Throwable {
    final String method = "nestedNonThrowingBlock";
    runDebugTest(
        config,
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 35),
        checkLocal("obj1"),
        checkLocal("obj2"),
        stepOver(),
        checkLine(FILE, 36),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 37),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 38),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 39),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 40),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 41),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 42),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 43),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        checkLocal("y"),
        run());
  }

  @Test
  public void testNestedThrowingBlock() throws Throwable {
    final String method = "nestedThrowingBlock";
    runDebugTest(
        config,
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 48),
        checkLocal("obj1"),
        checkLocal("obj2"),
        stepOver(),
        checkLine(FILE, 49),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 50),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 51),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 52),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 53), // inner synchronize block end
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 54), // outer synchronize block end
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 57), // catch handler
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkNoLocal("x"),
        run());
  }
}
