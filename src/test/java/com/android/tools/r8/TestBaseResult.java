// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

public abstract class TestBaseResult<CR extends TestBaseResult<CR, RR>, RR extends TestRunResult> {

  final TestState state;

  TestBaseResult(TestState state) {
    this.state = state;
  }

  public abstract CR self();

  public <S, E extends Throwable> S map(ThrowingFunction<CR, S, E> fn) {
    return fn.applyWithRuntimeException(self());
  }

  public <T extends Throwable> CR apply(ThrowableConsumer<CR> fn) {
    fn.acceptWithRuntimeException(self());
    return self();
  }
}
