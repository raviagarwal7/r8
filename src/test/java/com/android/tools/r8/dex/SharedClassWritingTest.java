// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SharedClassWritingTest {

  private final static String PREFIX = "A";
  private final static int NUMBER_OF_FILES = 500;

  DexItemFactory dexItemFactory = new DexItemFactory();

  private DexString[] strings;

  @Before
  public void generateStringArray() {
    strings = new DexString[Constants.MAX_NON_JUMBO_INDEX + 100];
    for (int i = 0; i < strings.length; i++) {
      // Format i as string with common prefix and leading 0's so that they are in the array
      // in lexicographic order.
      String string = PREFIX + StringUtils.zeroPrefix(i, 8);
      strings[i] = dexItemFactory.createString(string);
    }
  }

  private DexEncodedMethod makeMethod(DexType holder, int stringCount, int startOffset) {
    assert stringCount + startOffset < strings.length;
    Instruction[] instructions = new Instruction[stringCount + 1];
    for (int i = 0; i < stringCount; i++) {
      instructions[i] = new ConstString(0, strings[startOffset + i]);
    }
    instructions[stringCount] = new ReturnVoid();
    DexCode code = new DexCode(1, 0, 0, instructions, new Try[0], new TryHandler[0], null);
    return new DexEncodedMethod(
        dexItemFactory.createMethod(
            holder, dexItemFactory.createProto(dexItemFactory.voidType), "theMethod"),
        MethodAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC, false),
        DexAnnotationSet.empty(),
        ParameterAnnotationsList.empty(),
        code);
  }

  private DexProgramClass makeClass(
      InternalOptions options,
      String name,
      int stringCount,
      int startOffset,
      Collection<DexProgramClass> synthesizedFrom) {
    String desc = DescriptorUtils.javaTypeToDescriptor(name);
    DexType type = dexItemFactory.createType(desc);
    DexProgramClass programClass =
        new DexProgramClass(
            type,
            null,
            new SynthesizedOrigin("test", getClass()),
            ClassAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC),
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
            new DexEncodedMethod[] {makeMethod(type, stringCount, startOffset)},
            false,
            DexProgramClass::invalidChecksumRequest,
            synthesizedFrom);
    return programClass;
  }

  @Test
  public void manyFilesWithSharedSynthesizedClass() throws ExecutionException, IOException {
    InternalOptions options = new InternalOptions(dexItemFactory, new Reporter());

    // Create classes that all reference enough strings to overflow the index, but are all
    // at different offsets in the strings array. This ensures we trigger multiple rounds of
    // rewrites.
    List<DexProgramClass> classes = new ArrayList<>();
    for (int i = 0; i < NUMBER_OF_FILES; i++) {
      classes.add(
          makeClass(
              options,
              "Class" + i,
              Constants.MAX_NON_JUMBO_INDEX - 1,
              i % 100,
              Collections.emptyList()));
    }

    // Create a shared class that references strings above the maximum.
    DexProgramClass sharedSynthesizedClass =
        makeClass(options, "SharedSynthesized", 100, Constants.MAX_NON_JUMBO_INDEX - 1, classes);

    DexApplication.Builder builder = DirectMappedDexApplication.builder(options, Timing.empty());
    builder.addSynthesizedClass(sharedSynthesizedClass, false);
    classes.forEach(builder::addProgramClass);
    DexApplication application = builder.build();

    CollectInfoConsumer consumer = new CollectInfoConsumer();
    options.programConsumer = consumer;
    ApplicationWriter writer =
        new ApplicationWriter(
            application,
            null,
            options,
            null,
            GraphLense.getIdentityLense(),
            InitClassLens.getDefault(),
            NamingLens.getIdentityLens(),
            null);
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    writer.write(executorService);
    List<Set<String>> generatedDescriptors = consumer.getDescriptors();
    // Check all files present.
    Assert.assertEquals(NUMBER_OF_FILES, generatedDescriptors.size());
    // And each file contains two classes of which one is the shared one.
    for (Set<String> classDescriptors : generatedDescriptors) {
      Assert.assertEquals(2, classDescriptors.size());
      Assert
          .assertTrue(classDescriptors.contains(sharedSynthesizedClass.type.toDescriptorString()));
    }
  }

  private static class CollectInfoConsumer implements DexFilePerClassFileConsumer {

    private final List<Set<String>> descriptors = new ArrayList<>();

    private final Deque<ByteBuffer> freeBuffers = new ArrayDeque<>();
    private final Set<ByteBuffer> activeBuffers = Sets.newIdentityHashSet();

    @Override
    public ByteBuffer acquireByteBuffer(int capacity) {
      synchronized (freeBuffers) {
        ByteBuffer buffer = freeBuffers.pollFirst();
        // Ensure the buffer has sufficient capacity, eg, skip buffers that are too small.
        if (buffer != null && buffer.capacity() < capacity) {
          List<ByteBuffer> small = new ArrayList<>(freeBuffers.size());
          do {
            small.add(buffer);
            buffer = freeBuffers.pollFirst();
          } while (buffer != null && buffer.capacity() < capacity);
          freeBuffers.addAll(small);
        }
        if (buffer == null) {
          buffer = ByteBuffer.allocate(capacity);
        }
        assert !activeBuffers.contains(buffer);
        activeBuffers.add(buffer);
        return buffer;
      }
    }

    @Override
    public void releaseByteBuffer(ByteBuffer buffer) {
      synchronized (freeBuffers) {
        assert activeBuffers.contains(buffer);
        activeBuffers.remove(buffer);
        buffer.position(0);
        freeBuffers.offerFirst(buffer);
      }
    }

    @Override
    public void accept(
        String primaryClassDescriptor,
        ByteDataView data,
        Set<String> descriptors,
        DiagnosticsHandler handler) {
      addDescriptors(descriptors);
    }

    synchronized void addDescriptors(Set<String> descriptors) {
      this.descriptors.add(descriptors);
    }

    public List<Set<String>> getDescriptors() {
      return descriptors;
    }

    @Override
    public void finished(DiagnosticsHandler handler) {}
  }
}
