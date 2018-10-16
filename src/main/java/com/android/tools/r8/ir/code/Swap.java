// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import java.util.Arrays;

public class Swap extends Instruction {

  public Swap(StackValues dest, StackValues src) {
    super(dest, src.getStackValues());
    assert src.getStackValues().size() == 2;
    assert !(this.inValues.get(0).type.isWide() ^ this.inValues.get(1).type.isWide());
  }

  public Swap(StackValues dest, StackValue src1, StackValue src2) {
    super(dest, Arrays.asList(src1, src2));
    assert !(this.inValues.get(0).type.isWide() ^ !this.inValues.get(1).type.isWide());
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable("This classfile-specific IR should not be inserted in the Dex backend.");
  }

  @Override
  public void buildCf(CfBuilder builder) {
    if (this.inValues.get(0).type.isWide()) {
      builder.add(new CfStackInstruction(Opcode.Dup2X2));
      builder.add(new CfStackInstruction(Opcode.Pop2));
    } else {
      builder.add(new CfStackInstruction(Opcode.Swap));
    }
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    throw new Unreachable();
  }

  @Override
  public int compareNonValueParts(Instruction other) {
    throw new Unreachable();
  }

  @Override
  public int maxInValueRegister() {
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    throw new Unreachable();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forSwap();
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    // Intentionally empty. Swap is a stack operation.
  }

  @Override
  public boolean hasInvariantOutType() {
    return false;
  }

  @Override
  public boolean isSwap() {
    return true;
  }

  @Override
  public Swap asSwap() {
    return this;
  }
}