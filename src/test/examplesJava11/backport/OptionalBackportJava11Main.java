// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import java.util.Optional;

public final class OptionalBackportJava11Main {

  public static void main(String[] args) {
    testIsEmpty();
  }

  private static void testIsEmpty() {
    Optional<String> present = Optional.of("hey");
    assertFalse(present.isEmpty());

    Optional<String> absent = Optional.empty();
    assertTrue(absent.isEmpty());
  }

  private static void assertTrue(boolean value) {
    if (!value) {
      throw new AssertionError("Expected <true> but was <false>");
    }
  }

  private static void assertFalse(boolean value) {
    if (value) {
      throw new AssertionError("Expected <false> but was <true>");
    }
  }
}
