#!/usr/bin/env python
# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Wrapper script for running gradle.
# Will make sure we pulled down gradle before running, and will use the pulled
# down version to have a consistent developer experience.

import argparse
import os
import subprocess
import sys

import jdk
import utils

GRADLE_DIR = os.path.join(utils.REPO_ROOT, 'third_party', 'gradle')
GRADLE_SHA1 = os.path.join(GRADLE_DIR, 'gradle.tar.gz.sha1')
GRADLE_TGZ = os.path.join(GRADLE_DIR, 'gradle.tar.gz')

if utils.IsWindows():
  GRADLE = os.path.join(GRADLE_DIR, 'gradle', 'bin', 'gradle.bat')
else:
  GRADLE = os.path.join(GRADLE_DIR, 'gradle', 'bin', 'gradle')

def ParseOptions():
  parser = argparse.ArgumentParser(description = 'Call gradle.')
  parser.add_argument('--no-internal', '--no_internal',
      help='Do not build with support for Google internal tests.',
      default=False, action='store_true')
  parser.add_argument('--java-home', '--java_home',
      help='Use a custom java version to run gradle.')
  parser.add_argument('--worktree',
                      help='Gradle is running in a worktree and may lock up '
                           'the gradle caches.',
                      action='store_true',
                      default=False)
  return parser.parse_known_args()

def GetJavaEnv(env):
  java_env = dict(env if env else os.environ, JAVA_HOME = jdk.GetJdkHome())
  java_env['PATH'] = java_env['PATH'] + os.pathsep + os.path.join(jdk.GetJdkHome(), 'bin')
  java_env['GRADLE_OPTS'] = '-Xmx1g'
  return java_env

def PrintCmd(s):
  if type(s) is list:
    s = ' '.join(s)
  print 'Running: %s' % s
  # I know this will hit os on windows eventually if we don't do this.
  sys.stdout.flush()

def EnsureGradle():
  utils.EnsureDepFromGoogleCloudStorage(
    GRADLE, GRADLE_TGZ, GRADLE_SHA1, 'Gradle binary')

def EnsureJdk():
  jdkHome = jdk.GetJdkHome()
  jdkTgz = jdkHome + '.tar.gz'
  jdkSha1 = jdkTgz + '.sha1'
  utils.EnsureDepFromGoogleCloudStorage(jdkHome, jdkTgz, jdkSha1, 'JDK')

def EnsureDeps():
  EnsureGradle()
  EnsureJdk()

def RunGradleIn(gradleCmd, args, cwd, throw_on_failure=True, env=None):
  EnsureDeps()
  cmd = [gradleCmd]
  cmd.extend(args)
  utils.PrintCmd(cmd)
  with utils.ChangedWorkingDirectory(cwd):
    return_value = subprocess.call(cmd, env=GetJavaEnv(env))
    if throw_on_failure and return_value != 0:
      raise Exception('Failed to execute gradle')
    return return_value

def RunGradleWrapperIn(args, cwd, throw_on_failure=True, env=None):
  return RunGradleIn('./gradlew', args, cwd, throw_on_failure, env=env)

def RunGradle(args, throw_on_failure=True, env=None):
  return RunGradleIn(GRADLE, args, utils.REPO_ROOT, throw_on_failure, env=env)

def RunGradleExcludeDeps(args, throw_on_failure=True, env=None):
  EnsureDeps()
  args.append('-Pexclude_deps')
  return RunGradle(args, throw_on_failure, env=env)

def RunGradleInGetOutput(gradleCmd, args, cwd, env=None):
  EnsureDeps()
  cmd = [gradleCmd]
  cmd.extend(args)
  utils.PrintCmd(cmd)
  with utils.ChangedWorkingDirectory(cwd):
    return subprocess.check_output(cmd, env=GetJavaEnv(env))

def RunGradleWrapperInGetOutput(args, cwd, env=None):
  return RunGradleInGetOutput('./gradlew', args, cwd, env=env)

def RunGradleGetOutput(args, env=None):
  return RunGradleInGetOutput(GRADLE, args, utils.REPO_ROOT, env=env)

def Main():
  (options, args) = ParseOptions()
  if options.java_home:
    args.append('-Dorg.gradle.java.home=' + options.java_home)
  if options.no_internal:
    args.append('-Pno_internal')
  if options.worktree:
    args.append('-g=' + os.path.join(utils.REPO_ROOT, ".gradle_user_home"))
  return RunGradle(args)

if __name__ == '__main__':
  sys.exit(Main())
