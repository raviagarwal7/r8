// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

/** Globally controlled settings that affect the default values for kept items. */
public interface GlobalKeepInfoConfiguration {

  boolean isTreeShakingEnabled();

  boolean isMinificationEnabled();

  boolean isAccessModificationEnabled();
}
