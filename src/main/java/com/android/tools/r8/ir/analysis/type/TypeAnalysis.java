// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.fromDexType;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.Lists;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

public class TypeAnalysis {

  private enum Mode {
    UNSET,
    WIDENING,   // initial analysis, including fixed-point iteration for phis.
    NARROWING,  // updating with more specific info, e.g., passing the return value of the inlinee.
  }

  private final boolean mayHaveImpreciseTypes;

  private Mode mode = Mode.UNSET;

  private final AppView<? extends AppInfo> appView;
  private final DexEncodedMethod context;

  private final Deque<Value> worklist = new ArrayDeque<>();

  public TypeAnalysis(AppView<? extends AppInfo> appView, DexEncodedMethod encodedMethod) {
    this(appView, encodedMethod, false);
  }

  public TypeAnalysis(
      AppView<? extends AppInfo> appView,
      DexEncodedMethod encodedMethod,
      boolean mayHaveImpreciseTypes) {
    this.appView = appView;
    this.context = encodedMethod;
    this.mayHaveImpreciseTypes = mayHaveImpreciseTypes;
  }

  private void analyze() {
    while (!worklist.isEmpty()) {
      analyzeValue(worklist.poll());
    }
  }

  public void widening(DexEncodedMethod encodedMethod, IRCode code) {
    mode = Mode.WIDENING;
    assert worklist.isEmpty();
    code.topologicallySortedBlocks().forEach(b -> analyzeBasicBlock(encodedMethod, b));
    analyze();
  }

  public void widening(Iterable<Value> values) {
    mode = Mode.WIDENING;
    assert worklist.isEmpty();
    values.forEach(this::enqueue);
    analyze();
  }

  public void narrowing(Iterable<Value> values) {
    // TODO(b/125492155) Not sorting causes us to have non-deterministic behaviour. This should be
    //  removed when the bug is fixed.
    List<Value> sortedValues = Lists.newArrayList(values);
    sortedValues.sort(Comparator.comparingInt(Value::getNumber));
    mode = Mode.NARROWING;
    assert worklist.isEmpty();
    sortedValues.forEach(this::enqueue);
    analyze();
  }

  private void enqueue(Value v) {
    assert v != null;
    if (!worklist.contains(v)) {
      worklist.add(v);
    }
  }

  public void analyzeBasicBlock(DexEncodedMethod encodedMethod, BasicBlock block) {
    int argumentsSeen = encodedMethod.accessFlags.isStatic() ? 0 : -1;
    for (Instruction instruction : block.getInstructions()) {
      Value outValue = instruction.outValue();
      if (outValue == null) {
        continue;
      }
      // The type for Argument, a quasi instruction, can be inferred from the method signature.
      if (instruction.isArgument()) {
        TypeLatticeElement derived;
        if (argumentsSeen < 0) {
          // Receiver
          derived =
              fromDexType(
                  encodedMethod.method.holder,
                  // Now we try inlining even when the receiver could be null.
                  encodedMethod == context ? definitelyNotNull() : maybeNull(),
                  appView);
        } else {
          DexType argType = encodedMethod.method.proto.parameters.values[argumentsSeen];
          derived = fromDexType(argType, maybeNull(), appView);
        }
        argumentsSeen++;
        updateTypeOfValue(outValue, derived);
        // Note that we don't need to enqueue the out value of arguments here because it's constant.
      } else if (instruction.hasInvariantOutType()) {
        TypeLatticeElement derived = instruction.evaluate(appView);
        updateTypeOfValue(outValue, derived);
      } else {
        enqueue(outValue);
      }
    }
    for (Phi phi : block.getPhis()) {
      enqueue(phi);
    }
  }

  private void analyzeValue(Value value) {
    TypeLatticeElement previous = value.getTypeLattice();
    TypeLatticeElement derived =
        value.isPhi() ? value.asPhi().computePhiType(appView) : value.definition.evaluate(appView);
    assert mayHaveImpreciseTypes || derived.isPreciseType();
    assert !previous.isPreciseType() || derived.isPreciseType();
    updateTypeOfValue(value, derived);
  }

  private void updateTypeOfValue(Value value, TypeLatticeElement type) {
    assert mode != Mode.UNSET;

    TypeLatticeElement current = value.getTypeLattice();
    if (current.equals(type)) {
      return;
    }

    if (type.isBottom()) {
      return;
    }
    if (mode == Mode.WIDENING) {
      value.widening(appView, type);
    } else {
      assert mode == Mode.NARROWING;
      value.narrowing(appView, type);
    }

    // propagate the type change to (instruction) users if any.
    for (Instruction instruction : value.uniqueUsers()) {
      Value outValue = instruction.outValue();
      if (outValue != null) {
        enqueue(outValue);
      }
    }
    // Propagate the type change to phi users if any.
    for (Phi phi : value.uniquePhiUsers()) {
      enqueue(phi);
    }
  }

  public static DexType getRefinedReceiverType(
      AppInfoWithSubtyping appInfo, InvokeMethodWithReceiver invoke) {
    DexType receiverType = invoke.getInvokedMethod().holder;
    TypeLatticeElement lattice = invoke.getReceiver().getTypeLattice();
    if (lattice.isClassType()) {
      DexType refinedType = lattice.asClassTypeLatticeElement().getClassType();
      if (appInfo.isSubtype(refinedType, receiverType)) {
        return refinedType;
      }
    }
    return receiverType;
  }

}
