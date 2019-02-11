// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.FixedLocalValue;
import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;

public class Store extends Instruction {

  public Store(Value dest, StackValue src) {
    super(dest, src);
  }

  public Value src() {
    return inValues.get(0);
  }

  @Override
  public boolean isStore() {
    return true;
  }

  @Override
  public Store asStore() {
    return this;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isStore();
  }

  @Override
  public int maxInValueRegister() {
    throw new Unreachable();
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U16BIT_MAX;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forStore();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable("This classfile-specific IR should not be inserted in the Dex backend.");
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfStore(outType(), builder.getLocalRegister(outValue)));
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return helper.getDexType(src());
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    throw new Unreachable("This IR must not be inserted before load and store insertion.");
  }

  @Override
  public TypeLatticeElement evaluate(AppInfo appInfo) {
    return src().getTypeLattice();
  }

  @Override
  public boolean hasInvariantOutType() {
    return false;
  }

  @Override
  public boolean canBeDeadCode(
      AppView<? extends AppInfoWithLiveness> appView, AppInfo appInfo, IRCode code) {
    return !(outValue instanceof FixedLocalValue);
  }

  @Override
  public boolean needsValueInRegister(Value value) {
    assert value == src();
    assert src() instanceof StackValue;
    return false;
  }
}
