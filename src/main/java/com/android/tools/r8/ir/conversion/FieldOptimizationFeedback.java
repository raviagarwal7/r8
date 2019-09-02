// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;

public interface FieldOptimizationFeedback {

  void markFieldCannotBeKept(DexEncodedField field);

  void markFieldAsPropagated(DexEncodedField field);

  void markFieldHasDynamicType(DexEncodedField field, TypeLatticeElement type);
}