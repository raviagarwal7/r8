// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexEncodedAnnotation;

public class FoundAnnotationSubject extends AnnotationSubject {

  private final DexAnnotation annotation;

  FoundAnnotationSubject(DexAnnotation annotation) {
    this.annotation = annotation;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return false;
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if an annotation is synthetic");
  }

  @Override
  public DexEncodedAnnotation getAnnotation() {
    return annotation.annotation;
  }
}
