// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Ignore;
import org.junit.Test;

public class R8UnreachableCodeTest {

  private static final Path SMALI_DIR = Paths.get(ToolHelper.SMALI_BUILD_DIR);

  @Ignore
  @Test
  public void UnreachableCode() throws IOException, ExecutionException {
    String name = "unreachable-code-1";
    AndroidApp input =
        AndroidApp.builder()
            .addProgramFiles(SMALI_DIR.resolve(name).resolve(name + ".dex"))
            .build();
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Timing timing = Timing.empty();
    InternalOptions options = new InternalOptions();
    options.programConsumer = DexIndexedConsumer.emptyConsumer();
    DirectMappedDexApplication application =
        new ApplicationReader(input, options, timing).read(executorService).toDirect();
    IRConverter converter =
        new IRConverter(AppView.createForR8(new AppInfoWithClassHierarchy(application)), null);
    converter.optimize();
    DexProgramClass clazz = application.classes().iterator().next();
    assertEquals(4, clazz.getMethodCollection().numberOfDirectMethods());
    for (DexEncodedMethod method : clazz.directMethods()) {
      if (!method.method.name.toString().equals("main")) {
        assertEquals(2, method.getCode().asDexCode().instructions.length);
      }
    }
  }
}
