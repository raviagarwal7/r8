// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexClass;
import java.util.List;

public class AbsentKmPackageSubject extends KmPackageSubject {

  @Override
  public DexClass getDexClass() {
    return null;
  }

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent KmPackage is renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if an absent KmPackage is synthetic");
  }

  @Override
  public List<String> getParameterTypeDescriptorsInFunctions() {
    return null;
  }

  @Override
  public List<String> getReturnTypeDescriptorsInFunctions() {
    return null;
  }

  @Override
  public List<String> getReturnTypeDescriptorsInProperties() {
    return null;
  }

  @Override
  public KmFunctionSubject kmFunctionWithUniqueName(String name) {
    return null;
  }

  @Override
  public KmFunctionSubject kmFunctionExtensionWithUniqueName(String name) {
    return null;
  }

  @Override
  public List<KmFunctionSubject> getFunctions() {
    return null;
  }

  @Override
  public List<ClassSubject> getParameterTypesInFunctions() {
    return null;
  }

  @Override
  public List<ClassSubject> getReturnTypesInFunctions() {
    return null;
  }

  @Override
  public KmPropertySubject kmPropertyWithUniqueName(String name) {
    return null;
  }

  @Override
  public KmPropertySubject kmPropertyExtensionWithUniqueName(String name) {
    return null;
  }

  @Override
  public List<KmPropertySubject> getProperties() {
    return null;
  }

  @Override
  public List<ClassSubject> getReturnTypesInProperties() {
    return null;
  }

  @Override
  public List<KmTypeAliasSubject> getTypeAliases() {
    return null;
  }

  @Override
  public KmTypeAliasSubject kmTypeAliasWithUniqueName(String name) {
    return null;
  }
}
