#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
from os import makedirs, listdir
from os.path import join, exists, isdir
from string import Template, upper
from sys import exit
from shutil import rmtree

OUTPUT_DIR = os.path.join('build', 'generated', 'test', 'java', 'com',
                          'android', 'tools', 'r8', 'art')
# Test that comes from Jack are generated from the legacy snapshot.
JACK_TEST = os.path.join('tests', '2016-12-19', 'art')
TEST_DIR = os.path.join('tests', '2017-10-04', 'art')
TOOLCHAINS = ["dx", "jack", "none"]
TOOLS = ["r8", "d8"]
TEMPLATE = Template(
"""// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.art.$testGeneratingToolchain.$compilerUnderTest;

import static com.android.tools.r8.R8RunArtTestsTest.DexTool.$testGeneratingToolchainEnum;

import com.android.tools.r8.R8RunArtTestsTest;
import org.junit.Test;

/**
 * Auto-generated test for the art $name test using the $testGeneratingToolchain toolchain.
 *
 * DO NOT EDIT THIS FILE. EDIT THE HERE DOCUMENT TEMPLATE IN tools/create_art_tests.py INSTEAD!
 */
public class $testClassName extends R8RunArtTestsTest {

    public $testClassName() {
      super("$name", $testGeneratingToolchainEnum);
    }

    @Test
    public void run$testClassName() throws Throwable {
      // For testing with other Art VMs than the default pass the VM version as a argument to
      // runArtTest, e.g. runArtTest(DexVm.ART_4_4_4_HOST, CompilerUnderTest.$compilerUnderTestEnum).
      runArtTest(CompilerUnderTest.$compilerUnderTestEnum);
    }
}
""")

def create_toolchain_dirs(toolchain):
  toolchain_dir = join(OUTPUT_DIR, toolchain)
  if exists(toolchain_dir):
    rmtree(toolchain_dir)
  makedirs(join(toolchain_dir, "d8"))
  makedirs(join(toolchain_dir, "r8"))

def write_file(toolchain, tool, class_name, contents):
  file_name = join(OUTPUT_DIR, toolchain, tool, class_name + ".java")
  with open(file_name, "w") as file:
    file.write(contents)

def create_tests(toolchain):
  source_dir = join(TEST_DIR, "dx" if toolchain == "none" else toolchain)
  # For the toolchain "jack" tests are generated from a previous snapshot.
  if (toolchain == "jack"):
    source_dir = join(JACK_TEST, toolchain)
  dirs = [d for d in listdir(source_dir)
          if isdir(join(source_dir, d))]
  for dir in dirs:
    class_name = "Art" + dir.replace("-", "_") + "Test"
    for tool in TOOLS:
      if tool == "d8" and toolchain == "none":
        tool_enum = 'R8_AFTER_D8'
      else:
        tool_enum = upper(tool)
      contents = TEMPLATE.substitute(
          name=dir,
          compilerUnderTestEnum=tool_enum,
          compilerUnderTest=tool,
          testGeneratingToolchain=toolchain,
          testGeneratingToolchainEnum=upper(toolchain),
          testClassName=class_name)
      write_file(toolchain, tool, class_name, contents)


def main():
  for toolchain in TOOLCHAINS:
    create_toolchain_dirs(toolchain)
    create_tests(toolchain)

if __name__ == "__main__":
  exit(main())
