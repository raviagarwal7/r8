// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b148525512

class Feature(x: Int, y: Int) : Base(x, y)

fun feature(i: Int) {
  val f = Feature(i, i + 1)
  printInt { f.x }
  printInt { f.y }
}
