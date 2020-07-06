// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.PriorityQueue;
import org.junit.Test;

public class Regress68656641 extends SmaliTestBase {

  private static class MyRegisterAllocator extends LinearScanRegisterAllocator {
    public MyRegisterAllocator(AppView<?> appView, IRCode code) {
      super(appView, code);
    }

    public void addInactiveIntervals(LiveIntervals intervals) {
      inactive.add(intervals);
    }

    public void splitOverlappingInactiveIntervals(LiveIntervals intervals, int register) {
      splitOverlappingInactiveIntervals(intervals, register, false);
    }

    public PriorityQueue<LiveIntervals> getUnhandled() {
      return unhandled;
    }
  }

  IRCode simpleCode() {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    MethodSignature signature = builder.addStaticMethod(
        "void",
        DEFAULT_METHOD_NAME,
        ImmutableList.of(),
        1,
        "    return-void");
    AndroidApp application = buildApplication(builder);
    // Build the code, and split the code into three blocks.
    MethodSubject methodSubject = getMethodSubject(application, signature);
    return methodSubject.buildIR();
  }

  @Test
  public void splitOverlappingInactiveIntervalWithNoNextUse() {
    InternalOptions options = new InternalOptions();
    AppInfo appInfo = new AppInfo(DexApplication.builder(options, null).build());
    AppView<?> appView = AppView.createForD8(appInfo);
    IRCode code = simpleCode();
    MyRegisterAllocator allocator = new MyRegisterAllocator(appView, code);
    // Setup live an inactive live interval with ranges [0, 10[ and [20, 30[ with only
    // uses in the first interval and which is linked to another interval.
    LiveIntervals inactiveIntervals = new LiveIntervals(new Value(0, TypeElement.getInt(), null));
    inactiveIntervals.addRange(new LiveRange(0, 10));
    inactiveIntervals.addUse(new LiveIntervalsUse(0, 10));
    inactiveIntervals.addUse(new LiveIntervalsUse(4, 10));
    inactiveIntervals.addRange(new LiveRange(20, 30));
    inactiveIntervals.setRegister(0);
    LiveIntervals linked = new LiveIntervals(new Value(1, TypeElement.getInt(), null));
    linked.setRegister(1);
    inactiveIntervals.link(linked);
    allocator.addInactiveIntervals(inactiveIntervals);
    // Setup an unhandled interval that overlaps the inactive interval.
    LiveIntervals unhandledIntervals = new LiveIntervals(new Value(2, TypeElement.getInt(), null));
    unhandledIntervals.addRange(new LiveRange(12, 24));
    // Split the overlapping inactive intervals and check that after the split, the second
    // part of the inactive interval is unhandled and will therefore get a new register
    // assigned later during allocation.
    allocator.splitOverlappingInactiveIntervals(unhandledIntervals, 0);
    assert allocator.getUnhandled().size() == 1;
    assert allocator.getUnhandled().peek().getStart() == 20;
    assert allocator.getUnhandled().peek().getEnd() == 30;
  }
}
