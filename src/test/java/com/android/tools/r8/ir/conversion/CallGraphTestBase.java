// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.android.tools.r8.origin.SynthesizedOrigin;
import java.util.Collections;

class CallGraphTestBase extends TestBase {

  private DexItemFactory dexItemFactory = new DexItemFactory();
  private DexProgramClass clazz =
      new DexProgramClass(
          dexItemFactory.createType("LCallGraphTest;"),
          null,
          new SynthesizedOrigin("test", CallGraphTestBase.class),
          ClassAccessFlags.fromSharedAccessFlags(0),
          dexItemFactory.objectType,
          DexTypeList.empty(),
          null,
          null,
          Collections.emptyList(),
          null,
          Collections.emptyList(),
          DexAnnotationSet.empty(),
          DexEncodedField.EMPTY_ARRAY,
          DexEncodedField.EMPTY_ARRAY,
          DexEncodedMethod.EMPTY_ARRAY,
          DexEncodedMethod.EMPTY_ARRAY,
          false,
          DexProgramClass::invalidChecksumRequest);

  Node createNode(String methodName) {
    DexMethod signature =
        dexItemFactory.createMethod(
            clazz.type, dexItemFactory.createProto(dexItemFactory.voidType), methodName);
    ProgramMethod method =
        new ProgramMethod(
            clazz,
            new DexEncodedMethod(
                signature, null, DexAnnotationSet.empty(), ParameterAnnotationsList.empty(), null));
    return new Node(method);
  }

  Node createForceInlinedNode(String methodName) {
    Node node = createNode(methodName);
    node.getMethod().getMutableOptimizationInfo().markForceInline();
    return node;
  }
}
