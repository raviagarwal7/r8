// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import java.util.Collection;
import java.util.stream.Stream;

public interface TryCatchSubject {
  RangeSubject getRange();
  boolean isCatching(String exceptionType);
  boolean hasCatchAll();

  Stream<TypeSubject> streamGuards();

  Collection<TypeSubject> guards();

  int getNumberOfHandlers();
}
