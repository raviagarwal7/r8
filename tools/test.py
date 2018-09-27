#!/usr/bin/env python
# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Convenience script for running tests. If no argument is given run all tests,
# if an argument is given, run only tests with that pattern. This script will
# force the tests to run, even if no input changed.

import os
import gradle
import optparse
import subprocess
import sys
import utils
import uuid
import notify
import upload_to_x20


ALL_ART_VMS = ["default", "7.0.0", "6.0.1", "5.1.1", "4.4.4", "4.0.4"]
BUCKET = 'r8-test-results'

def ParseOptions():
  result = optparse.OptionParser()
  result.add_option('--no_internal',
      help='Do not run Google internal tests.',
      default=False, action='store_true')
  result.add_option('--archive_failures',
      help='Upload test results to cloud storage on failure.',
      default=False, action='store_true')
  result.add_option('--only_internal',
      help='Only run Google internal tests.',
      default=False, action='store_true')
  result.add_option('--all_tests',
      help='Run tests in all configurations.',
      default=False, action='store_true')
  result.add_option('-v', '--verbose',
      help='Print test stdout to, well, stdout.',
      default=False, action='store_true')
  result.add_option('--dex_vm',
      help='The android version of the vm to use. "all" will run the tests on '
           'all art vm versions (stopping after first failed execution)',
      default="default",
      choices=ALL_ART_VMS + ["all"])
  result.add_option('--dex_vm_kind',
                    help='Whether to use host or target version of runtime',
                    default="host",
                    nargs=1,
                    choices=["host", "target"])
  result.add_option('--one_line_per_test',
      help='Print a line before a tests starts and after it ends to stdout.',
      default=False, action='store_true')
  result.add_option('--tool',
      help='Tool to run ART tests with: "r8" (default) or "d8". Ignored if'
          ' "--all_tests" enabled.',
      default=None, choices=["r8", "d8"])
  result.add_option('--jctf',
      help='Run JCTF tests with: "r8" (default) or "d8".',
      default=False, action='store_true')
  result.add_option('--only_jctf',
      help='Run only JCTF tests with: "r8" (default) or "d8".',
      default=False, action='store_true')
  result.add_option('--jctf_compile_only',
      help="Don't run, only compile JCTF tests.",
      default=False, action='store_true')
  result.add_option('--aosp_jar',
      help='Run aosp_jar test.',
      default=False, action='store_true')
  result.add_option('--disable_assertions',
      help='Disable assertions when running tests.',
      default=False, action='store_true')
  result.add_option('--with_code_coverage',
      help='Enable code coverage with Jacoco.',
      default=False, action='store_true')
  result.add_option('--test_dir',
      help='Use a custom directory for the test artifacts instead of a'
          ' temporary (which is automatically removed after the test).'
          ' Note that the directory will not be cleared before the test.')
  result.add_option('--java_home',
      help='Use a custom java version to run tests.')
  result.add_option('--generate_golden_files_to',
      help='Store dex files produced by tests in the specified directory.'
           ' It is aimed to be read on platforms with no host runtime available'
           ' for comparison.')
  result.add_option('--use_golden_files_in',
      help='Download golden files hierarchy for this commit in the specified'
           ' location and use them instead of executing on host runtime.')

  return result.parse_args()

def archive_failures():
  upload_dir = os.path.join(utils.REPO_ROOT, 'build', 'reports', 'tests')
  u_dir = uuid.uuid4()
  destination = 'gs://%s/%s' % (BUCKET, u_dir)
  utils.upload_dir_to_cloud_storage(upload_dir, destination, is_html=True)
  url = 'http://storage.googleapis.com/%s/%s/test/index.html' % (BUCKET, u_dir)
  print 'Test results available at: %s' % url
  print '@@@STEP_LINK@Test failures@%s@@@' % url

def Main():
  (options, args) = ParseOptions()
  if 'BUILDBOT_BUILDERNAME' in os.environ:
    gradle.RunGradle(['clean'])

  gradle_args = ['--stacktrace']
  # Set all necessary Gradle properties and options first.
  if options.verbose:
    gradle_args.append('-Pprint_test_stdout')
  if options.no_internal:
    gradle_args.append('-Pno_internal')
  if options.only_internal:
    gradle_args.append('-Ponly_internal')
  if options.all_tests:
    gradle_args.append('-Pall_tests')
  if options.tool:
    gradle_args.append('-Ptool=%s' % options.tool)
  if options.one_line_per_test:
    gradle_args.append('-Pone_line_per_test')
  if options.jctf:
    gradle_args.append('-Pjctf')
  if options.only_jctf:
    gradle_args.append('-Ponly_jctf')
  if options.jctf_compile_only:
    gradle_args.append('-Pjctf_compile_only')
  if options.aosp_jar:
    gradle_args.append('-Paosp_jar')
  if options.disable_assertions:
    gradle_args.append('-Pdisable_assertions')
  if options.with_code_coverage:
    gradle_args.append('-Pwith_code_coverage')
  if os.name == 'nt':
    # temporary hack
    gradle_args.append('-Pno_internal')
    gradle_args.append('-x')
    gradle_args.append('createJctfTests')
    gradle_args.append('-x')
    gradle_args.append('jctfCommonJar')
    gradle_args.append('-x')
    gradle_args.append('jctfTestsClasses')
  if options.test_dir:
    gradle_args.append('-Ptest_dir=' + options.test_dir)
    if not os.path.exists(options.test_dir):
      os.makedirs(options.test_dir)
  if options.java_home:
    gradle_args.append('-Dorg.gradle.java.home=' + options.java_home)
  if options.generate_golden_files_to:
    gradle_args.append('-Pgenerate_golden_files_to=' + options.generate_golden_files_to)
    if not os.path.exists(options.generate_golden_files_to):
      os.makedirs(options.generate_golden_files_to)
    gradle_args.append('-PHEAD_sha1=' + utils.get_HEAD_sha1())
  if options.use_golden_files_in:
    gradle_args.append('-Puse_golden_files_in=' + options.use_golden_files_in)
    if not os.path.exists(options.use_golden_files_in):
      os.makedirs(options.use_golden_files_in)
    gradle_args.append('-PHEAD_sha1=' + utils.get_HEAD_sha1())
  # Add Gradle tasks
  gradle_args.append('cleanTest')
  gradle_args.append('test')
  # Test filtering. Must always follow the 'test' task.
  for testFilter in args:
    gradle_args.append('--tests')
    gradle_args.append(testFilter)
  if options.with_code_coverage:
    # Create Jacoco report after tests.
    gradle_args.append('jacocoTestReport')

  if options.use_golden_files_in:
    sha1 = '%s' % utils.get_HEAD_sha1()
    with utils.ChangedWorkingDirectory(options.use_golden_files_in):
      utils.download_file_from_cloud_storage(
                                    'gs://r8-test-results/golden-files/%s.tar.gz' % sha1,
                                    '%s.tar.gz' % sha1)
      utils.unpack_archive('%s.tar.gz' % sha1)


  # Now run tests on selected runtime(s).
  vms_to_test = [options.dex_vm] if options.dex_vm != "all" else ALL_ART_VMS
  for art_vm in vms_to_test:
    vm_kind_to_test = "_" + options.dex_vm_kind if art_vm != "default" else ""
    return_code = gradle.RunGradle(gradle_args + ['-Pdex_vm=%s' % (art_vm + vm_kind_to_test)],
                                   throw_on_failure=False)

    if options.generate_golden_files_to:
      sha1 = '%s' % utils.get_HEAD_sha1()
      with utils.ChangedWorkingDirectory(options.generate_golden_files_to):
        archive = utils.create_archive(sha1)
        utils.upload_file_to_cloud_storage(archive,
                                           'gs://r8-test-results/golden-files/' + archive)

    if return_code != 0:
      if options.archive_failures and os.name != 'nt':
        archive_failures()
      return return_code

  return 0

if __name__ == '__main__':
  return_code = Main()
  if return_code != 0:
    notify.notify("Tests failed.")
  else:
    notify.notify("Tests passed.")
  sys.exit(return_code)
