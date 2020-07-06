// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.origin.Origin;

/** Type representing a method definition in the programs compilation unit and its holder. */
public final class ProgramMethod extends DexClassAndMethod {

  public ProgramMethod(DexProgramClass holder, DexEncodedMethod method) {
    super(holder, method);
  }

  public IRCode buildIR(AppView<?> appView) {
    DexEncodedMethod method = getDefinition();
    return method.hasCode() ? method.getCode().buildIR(this, appView, getOrigin()) : null;
  }

  public IRCode buildInliningIR(
      ProgramMethod context,
      AppView<?> appView,
      ValueNumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    Code code = getDefinition().getCode();
    return code.buildInliningIR(
        context, this, appView, valueNumberGenerator, callerPosition, origin, methodProcessor);
  }

  public boolean isStructurallyEqualTo(ProgramMethod other) {
    return getDefinition() == other.getDefinition() && getHolder() == other.getHolder();
  }

  public void registerCodeReferences(UseRegistry registry) {
    Code code = getDefinition().getCode();
    if (code != null) {
      if (Log.ENABLED) {
        Log.verbose(getClass(), "Registering definitions reachable from `%s`.", this);
      }
      code.registerCodeReferences(this, registry);
    }
  }

  @Override
  public boolean isProgramMethod() {
    return true;
  }

  @Override
  public ProgramMethod asProgramMethod() {
    return this;
  }

  @Override
  public DexProgramClass getHolder() {
    DexClass holder = super.getHolder();
    assert holder.isProgramClass();
    return holder.asProgramClass();
  }
}
