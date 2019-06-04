// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

public class NestPvtFieldPropagated {

  public static class Inner {
    private static String staticField = "toPropagateStatic";
  }

  public static void main(String[] args) {
    System.out.println(Inner.staticField);
  }
}