// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.DexMethod;
import java.util.HashMap;
import java.util.Map;

/**
 * Maintains a set of canonical positions. Also supports appending a new caller at the end of the
 * caller chain of a Position.
 */
public class CanonicalPositions {
  private final Position callerPosition;
  private final Map<Position, Position> canonicalPositions;
  private final Position preamblePosition;

  public CanonicalPositions(
      Position callerPosition,
      int expectedPositionsCount,
      DexMethod method) {
    canonicalPositions =
        new HashMap<>(1 + (callerPosition == null ? 0 : 1) + expectedPositionsCount);
    this.callerPosition = callerPosition;
    if (callerPosition != null) {
      canonicalPositions.put(callerPosition, callerPosition);
    }
    preamblePosition =
        callerPosition == null
            ? Position.synthetic(0, method, null)
            : new Position(0, null, method, callerPosition);
    canonicalPositions.put(preamblePosition, preamblePosition);
  }

  public Position getPreamblePosition() {
    return preamblePosition;
  }

  /**
   * Update the internal set if this is the first occurrence of the position's value and return
   * canonical instance of position.
   */
  public Position getCanonical(Position position) {
    Position canonical = canonicalPositions.putIfAbsent(position, position);
    return canonical != null ? canonical : position;
  }

  /**
   * Append callerPosition (supplied in constructor) to the end of caller's caller chain and return
   * the canonical instance. Always returns null if preserveCaller (also supplied in constructor) is
   * false.
   */
  public Position canonicalizeCallerPosition(Position caller) {
    if (caller == null) {
      return callerPosition;
    }
    if (caller.callerPosition == null && callerPosition == null) {
      return getCanonical(caller);
    }
    Position callerOfCaller = canonicalizeCallerPosition(caller.callerPosition);
    return getCanonical(
        caller.isNone()
            ? Position.noneWithMethod(caller.method, callerOfCaller)
            : new Position(caller.line, caller.file, caller.method, callerOfCaller));
  }
}
