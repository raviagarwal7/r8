// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package classmerging;

public class Test {

  public static void main(String... args) {
    GenericInterface iface = new GenericInterfaceImpl();
    callMethodOnIface(iface);
    GenericAbstractClass clazz = new GenericAbstractClassImpl();
    callMethodOnAbstractClass(clazz);
    ConflictingInterfaceImpl impl = new ConflictingInterfaceImpl();
    callMethodOnIface(impl);
    System.out.println(new SubClassThatReferencesSuperMethod().referencedMethod());
    Outer outer = new Outer();
    Outer.SubClass inner = outer.getInstance();
    System.out.println(outer.getInstance().method());
    System.out.println(new SubClass(42));

    // Ensure that the instantiations are not dead code eliminated.
    escape(clazz);
    escape(iface);
    escape(impl);
    escape(inner);
    escape(outer);
  }

  private static void callMethodOnIface(GenericInterface iface) {
    System.out.println(iface.method());
  }

  private static void callMethodOnAbstractClass(GenericAbstractClass clazz) {
    System.out.println(clazz.method());
    System.out.println(clazz.otherMethod());
  }

  private static void callMethodOnIface(ConflictingInterface iface) {
    System.out.println(iface.method());
    System.out.println(ClassWithConflictingMethod.conflict(null));
    System.out.println(OtherClassWithConflictingMethod.conflict(null));
  }

  @NeverInline
  static void escape(Object o) {
    if (System.currentTimeMillis() < 0) {
      System.out.println(o);
    }
  }
}
