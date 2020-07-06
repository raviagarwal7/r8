// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.ThrowingBiFunction;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidApp.Builder;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FilteredClassPathTest {

  private void testPath(List<String> filters, List<String> positives, List<String> negatives) {
    FilteredClassPath path = makeFilteredClassPath(filters);
    Assert.assertTrue(positives.stream().allMatch(path::matchesFile));
    Assert.assertFalse(negatives.stream().allMatch(path::matchesFile));
  }

  private static FilteredClassPath makeFilteredClassPath(List<String> filters) {
    return makeFilteredClassPath(Paths.get("foo"), filters);
  }

  private static FilteredClassPath makeFilteredClassPath(Path path, List<String> filters) {
    return new FilteredClassPath(
        path, ImmutableList.copyOf(filters), Origin.unknown(), Position.UNKNOWN);
  }

  @Test
  public void testFilterMatching() {
    testPath(ImmutableList.of("!boo"),
        ImmutableList.of("bool", "bo"),
        ImmutableList.of("boo"));
    testPath(ImmutableList.of("!*.boo"),
        ImmutableList.of("zoo.bar", "Fish.bool", "Cat.bo", "boo", "path/zoo.boo",
            "zoo.bool", "zoo.bo"),
        ImmutableList.of("mi.boo", ".boo"));
    testPath(ImmutableList.of("!*s/*.boo"),
        ImmutableList.of("zoo.bar", "Fish.bool", "Cat.bo", "boo", "path/zoo.boo", "sx/a.boo",
            "ass/boo", "s/x/.boo", "s/s/s.boo", "s/zoo.bool"),
        ImmutableList.of("mi.boo", ".boo", "ass/a.boo", "s/.boo", "asis/sos.boo"));
    testPath(ImmutableList.of("!**s/*.boo"),
        ImmutableList.of("zoo.bar", "Fish.bool", "Cat.bo", "boo", "path/zoo.boo", "sx/a.boo",
            "ass/boo", "s/x/.boo", "s/zoo.bool"),
        ImmutableList.of("mi.boo", ".boo", "ass/a.boo", "s/.boo", "asis/sos.boo", "s/s/s.boo",
            "xyz/abz/ass/.boo"));
    testPath(ImmutableList.of("!*.boo", "*.bar", "*.baz"),
        ImmutableList.of("sinz.bar", "song.baz", ".baz", ".bar"),
        ImmutableList.of("sinz.boo", ".boo", ".bang", "goo.bang", "s/foo.baz"));
  }

  private void testApplicationFiltered(
      ThrowingBiFunction<Builder, Collection<FilteredClassPath>, Builder, IOException> setter,
      Function<AndroidApp, List<String>> getter)
      throws IOException {
    Path androidJar = ToolHelper.getDefaultAndroidJar();
    AndroidApp app = setter.apply(
        AndroidApp.builder(),
        Collections.singletonList(makeFilteredClassPath(
            androidJar, ImmutableList.of("!java/lang/**.class", "java/util/**.class"))))
        .build();
    List<String> descriptors = getter.apply(app);
    Assert.assertTrue(descriptors.stream().noneMatch(s -> s.startsWith("Ljava/lang")));
    Assert.assertTrue(descriptors.stream().anyMatch(s -> s.startsWith("Ljava/util")));
    Assert.assertTrue(descriptors.stream().noneMatch(s -> s.startsWith("Lcom/android")));
  }

  @Test
  public void testLibraryFiltered() throws IOException {
    testApplicationFiltered(
        Builder::addFilteredLibraryArchives,
        app -> {
          ClassFileResourceProvider provider =
              Iterables.getOnlyElement(app.getLibraryResourceProviders());
          List<ProgramResource> resources =
              ListUtils.map(provider.getClassDescriptors(), provider::getProgramResource);
          return resources
              .stream()
              .flatMap(r -> r.getClassDescriptors().stream())
              .collect(Collectors.toList());
        });
  }

  private static Stream<ProgramResource> getClassProgramResources(ProgramResourceProvider reader) {
    try {
      return reader.getProgramResources().stream();
    } catch (ResourceException e) {
      return Stream.empty();
    }
  }

  @Test
  public void testProgramFiltered() throws IOException {
    testApplicationFiltered(
        Builder::addFilteredProgramArchives,
        app ->
            app.getProgramResourceProviders()
                .stream()
                .flatMap(FilteredClassPathTest::getClassProgramResources)
                .flatMap(r -> r.getClassDescriptors().stream())
                .collect(Collectors.toList()));
  }
}
