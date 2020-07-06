// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static com.google.common.base.Predicates.alwaysTrue;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Set;
import org.junit.Test;

public class NonidenticalCatchHandlerTest extends TestBase {

  private static class TestClass {
    public void foo(Object a) {
      int x = 0;
      try {
        x = ((String)a).length();
      } catch (ClassCastException e) {
        x = -1;
      } catch (NullPointerException e) {
        x = -1;
      }
      System.out.println(x);
    }
  }

  @Test
  public void test() throws Exception {
    byte[] inputBytes = ToolHelper.getClassAsBytes(TestClass.class);
    AndroidApp inputApp =
        AndroidApp.builder()
            .addClassProgramData(inputBytes, Origin.unknown())
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .build();
    assertEquals(2, countCatchHandlers(inputApp));
    AndroidApp outputDexApp =
        ToolHelper.runR8(
            ToolHelper.prepareR8CommandBuilder(inputApp)
                .setDisableTreeShaking(true)
                .setDisableMinification(true)
                .build());
    assertEquals(1, countCatchHandlers(outputDexApp));
    AndroidApp outputCfApp =
        ToolHelper.runR8(
            ToolHelper.prepareR8CommandBuilder(inputApp)
                .setDisableTreeShaking(true)
                .setDisableMinification(true)
                .setProgramConsumer(ClassFileConsumer.emptyConsumer())
                .build());
    // ClassCastException and NullPointerException will not have same type on stack.
    assertEquals(2, countCatchHandlers(outputCfApp));
  }

  private int countCatchHandlers(AndroidApp inputApp) throws Exception {
    CodeInspector inspector = new CodeInspector(inputApp);
    DexProgramClass dexClass = inspector.clazz(TestClass.class).getDexProgramClass();
    Code code = dexClass.lookupVirtualMethod(alwaysTrue()).getCode();
    if (code.isCfCode()) {
      CfCode cfCode = code.asCfCode();
      Set<CfLabel> targets = Sets.newIdentityHashSet();
      for (CfTryCatch tryCatch : cfCode.getTryCatchRanges()) {
        targets.addAll(tryCatch.targets);
      }
      return targets.size();
    } else if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      IntSet targets = new IntOpenHashSet();
      for (Try aTry : dexCode.tries) {
        targets.add(aTry.handlerOffset);
      }
      return targets.size();
    } else {
      throw new Unimplemented(code.getClass().getName());
    }
  }
}
