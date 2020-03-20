// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.EnumValueInfoMapCollection;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;

/**
 * Used to represent that a constructor initializes an instance field on the newly created instance
 * with a known dynamic lower- and upper-bound type.
 */
public class InstanceFieldTypeInitializationInfo implements InstanceFieldInitializationInfo {

  private final ClassTypeLatticeElement dynamicLowerBoundType;
  private final TypeLatticeElement dynamicUpperBoundType;

  /** Intentionally package private, use {@link InstanceFieldInitializationInfoFactory} instead. */
  InstanceFieldTypeInitializationInfo(
      ClassTypeLatticeElement dynamicLowerBoundType, TypeLatticeElement dynamicUpperBoundType) {
    this.dynamicLowerBoundType = dynamicLowerBoundType;
    this.dynamicUpperBoundType = dynamicUpperBoundType;
  }

  public ClassTypeLatticeElement getDynamicLowerBoundType() {
    return dynamicLowerBoundType;
  }

  public TypeLatticeElement getDynamicUpperBoundType() {
    return dynamicUpperBoundType;
  }

  @Override
  public boolean isTypeInitializationInfo() {
    return true;
  }

  @Override
  public InstanceFieldTypeInitializationInfo asTypeInitializationInfo() {
    return this;
  }

  @Override
  public InstanceFieldInitializationInfo rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLense lens) {
    EnumValueInfoMapCollection unboxedEnums = appView.unboxedEnums();
    if (dynamicLowerBoundType != null
        && unboxedEnums.containsEnum(dynamicLowerBoundType.getClassType())) {
      // No point in tracking the type of primitives.
      return UnknownInstanceFieldInitializationInfo.getInstance();
    }
    if (dynamicUpperBoundType.isClassType()
        && unboxedEnums.containsEnum(dynamicUpperBoundType.asClassType().getClassType())) {
      // No point in tracking the type of primitives.
      return UnknownInstanceFieldInitializationInfo.getInstance();
    }
    return new InstanceFieldTypeInitializationInfo(
        dynamicLowerBoundType != null
            ? dynamicLowerBoundType
                .fixupClassTypeReferences(lens::lookupType, appView.withSubtyping())
                .asClassType()
            : null,
        dynamicUpperBoundType.fixupClassTypeReferences(lens::lookupType, appView.withSubtyping()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(dynamicLowerBoundType, dynamicUpperBoundType);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (getClass() != other.getClass()) {
      return false;
    }
    InstanceFieldTypeInitializationInfo info = (InstanceFieldTypeInitializationInfo) other;
    return Objects.equals(dynamicLowerBoundType, info.dynamicLowerBoundType)
        && Objects.equals(dynamicUpperBoundType, info.dynamicUpperBoundType);
  }
}
