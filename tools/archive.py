#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import create_maven_release
import gradle
import jdk
import optparse
import os
try:
  import resource
except ImportError:
  # Not a Unix system. Do what Gandalf tells you not to.
  pass
import shutil
import subprocess
import sys
import toolhelper
import utils
import zipfile
from build_r8lib import build_r8lib

ARCHIVE_BUCKET = 'r8-releases'

def ParseOptions():
  result = optparse.OptionParser()
  result.add_option('--dry-run', '--dry_run',
      help='Build only, no upload.',
      default=False, action='store_true')
  result.add_option('--dry-run-output', '--dry_run_output',
      help='Output directory for \'build only, no upload\'.',
      type="string", action="store")
  return result.parse_args()

def GetToolVersion(jar_path):
  # TODO(mkroghj) This would not work for r8-lib, maybe use utils.getR8Version.
  output = subprocess.check_output([
    jdk.GetJavaExecutable(), '-jar', jar_path, '--version'
  ])
  return output.splitlines()[0].strip()

def GetVersion():
  r8_version = GetToolVersion(utils.R8_JAR)
  d8_version = GetToolVersion(utils.D8_JAR)
  # The version printed is "D8 vVERSION_NUMBER" and "R8 vVERSION_NUMBER"
  # Sanity check that versions match.
  if d8_version.split()[1] != r8_version.split()[1]:
    raise Exception(
        'Version mismatch: \n%s\n%s' % (d8_version, r8_version))
  return d8_version.split()[1]

def GetGitBranches():
  return subprocess.check_output(['git', 'show', '-s', '--pretty=%d', 'HEAD'])

def GetGitHash():
  return subprocess.check_output(['git', 'rev-parse', 'HEAD']).strip()

def IsMaster(version):
  branches = subprocess.check_output(['git', 'branch', '-r', '--contains',
                                      'HEAD'])
  # CL runs from gerrit does not have a branch, we always treat them as master
  # commits to archive these to the hash based location
  if len(branches) == 0:
    return True
  if not version == 'master':
    # Sanity check, we don't want to archive on top of release builds EVER
    # Note that even though we branch, we never push the bots to build the same
    # commit as master on a branch since we always change the version to
    # not be just 'master' (or we crash here :-)).
    if 'origin/master' in branches:
      raise Exception('We are seeing origin/master in a commit that '
                      'don\'t have \'master\' as version')
    return False
  if not 'origin/master' in branches:
      raise Exception('We are not seeing origin/master '
                      'in a commit that have \'master\' as version')
  return True

def GetStorageDestination(storage_prefix,
                          version_or_path,
                          file_name,
                          is_master):
  # We archive master commits under raw/master instead of directly under raw
  version_dir = GetVersionDestination(storage_prefix,
                                      version_or_path,
                                      is_master)
  return '%s/%s' % (version_dir, file_name)

def GetVersionDestination(storage_prefix, version_or_path, is_master):
  archive_dir = 'raw/master' if is_master else 'raw'
  return '%s%s/%s/%s' % (storage_prefix, ARCHIVE_BUCKET,
                         archive_dir, version_or_path)

def GetUploadDestination(version_or_path, file_name, is_master):
  return GetStorageDestination('gs://', version_or_path, file_name, is_master)

def GetUrl(version_or_path, file_name, is_master):
  return GetStorageDestination('https://storage.googleapis.com/',
                               version_or_path, file_name, is_master)

def GetMavenUrl(is_master):
  return GetVersionDestination('https://storage.googleapis.com/', '', is_master)

def SetRLimitToMax():
  (soft, hard) = resource.getrlimit(resource.RLIMIT_NOFILE)
  resource.setrlimit(resource.RLIMIT_NOFILE, (hard, hard))

def PrintResourceInfo():
  (soft, hard) = resource.getrlimit(resource.RLIMIT_NOFILE)
  print('INFO: Open files soft limit: %s' % soft)
  print('INFO: Open files hard limit: %s' % hard)

def Main():
  (options, args) = ParseOptions()
  if not utils.is_bot() and not options.dry_run:
    raise Exception('You are not a bot, don\'t archive builds. '
      + 'Use --dry-run to test locally')
  if (options.dry_run_output and
      (not os.path.exists(options.dry_run_output) or
       not os.path.isdir(options.dry_run_output))):
    raise Exception(options.dry_run_output
        + ' does not exist or is not a directory')

  if utils.is_bot() and not utils.IsWindows():
    SetRLimitToMax()
  if not utils.IsWindows():
    PrintResourceInfo()

  # Create maven release which uses a build that exclude dependencies.
  create_maven_release.generate_r8_maven_zip(utils.MAVEN_ZIP)
  create_maven_release.generate_r8_maven_zip(
      utils.MAVEN_ZIP_LIB, is_r8lib=True)

  # Generate and copy a full build without dependencies.
  gradle.RunGradleExcludeDeps([utils.R8, utils.R8_SRC])
  shutil.copyfile(utils.R8_JAR, utils.R8_FULL_EXCLUDE_DEPS_JAR)

  # Ensure all archived artifacts has been built before archiving.
  # The target tasks postfixed by 'lib' depend on the actual target task so
  # building it invokes the original task first.
  # The '-Pno_internal' flag is important because we generate the lib based on uses in tests.
  gradle.RunGradle([
    utils.R8,
    utils.D8,
    utils.R8LIB,
    utils.R8LIB_NO_DEPS,
    utils.LIBRARY_DESUGAR_CONVERSIONS,
    '-Pno_internal'
  ])

  # Create maven release of the desuage_jdk_libs configuration. This require
  # an r8.jar with dependencies to have been built.
  create_maven_release.generate_desugar_configuration_maven_zip(
      utils.DESUGAR_CONFIGURATION_MAVEN_ZIP)

  version = GetVersion()
  is_master = IsMaster(version)
  if is_master:
    # On master we use the git hash to archive with
    print 'On master, using git hash for archiving'
    version = GetGitHash()

  destination = GetVersionDestination('gs://', version, is_master)
  if utils.cloud_storage_exists(destination) and not options.dry_run:
    raise Exception('Target archive directory %s already exists' % destination)
  with utils.TempDir() as temp:
    # Create pom file for our maven repository that we build for testing.
    default_pom_file = os.path.join(temp, 'r8.pom')
    create_maven_release.write_default_r8_pom_file(default_pom_file, version)

    version_file = os.path.join(temp, 'r8-version.properties')
    with open(version_file,'w') as version_writer:
      version_writer.write('version.sha=' + GetGitHash() + '\n')
      if not os.environ.get('SWARMING_BOT_ID') and not options.dry_run:
        raise Exception('Environment variable SWARMING_BOT_ID not set')

      releaser = \
          ("<local developer build>" if options.dry_run
            else 'releaser=go/r8bot ('
                + os.environ.get('SWARMING_BOT_ID') + ')\n')
      version_writer.write(releaser)
      version_writer.write('version-file.version.code=1\n')

    for file in [
      utils.D8_JAR,
      utils.R8_JAR,
      utils.R8LIB_JAR,
      utils.R8LIB_JAR + '.map',
      utils.R8_SRC_JAR,
      utils.R8_FULL_EXCLUDE_DEPS_JAR,
      utils.R8LIB_EXCLUDE_DEPS_JAR,
      utils.R8LIB_EXCLUDE_DEPS_JAR + '.map',
      utils.MAVEN_ZIP,
      utils.MAVEN_ZIP_LIB,
      utils.DESUGAR_CONFIGURATION,
      utils.DESUGAR_CONFIGURATION_MAVEN_ZIP,
      utils.GENERATED_LICENSE,
    ]:
      file_name = os.path.basename(file)
      tagged_jar = os.path.join(temp, file_name)
      shutil.copyfile(file, tagged_jar)
      if file_name.endswith('.jar') and not file_name.endswith('-src.jar'):
        with zipfile.ZipFile(tagged_jar, 'a') as zip:
          zip.write(version_file, os.path.basename(version_file))
      destination = GetUploadDestination(version, file_name, is_master)
      print('Uploading %s to %s' % (tagged_jar, destination))
      if options.dry_run:
        if options.dry_run_output:
          dry_run_destination = os.path.join(options.dry_run_output, file_name)
          print('Dry run, not actually uploading. Copying to '
              + dry_run_destination)
          shutil.copyfile(tagged_jar, dry_run_destination)
        else:
          print('Dry run, not actually uploading')
      else:
        utils.upload_file_to_cloud_storage(tagged_jar, destination)
        print('File available at: %s' % GetUrl(version, file_name, is_master))

      # Upload R8 to a maven compatible location.
      if file == utils.R8_JAR:
        maven_dst = GetUploadDestination(utils.get_maven_path('r8', version),
                                         'r8-%s.jar' % version, is_master)
        maven_pom_dst = GetUploadDestination(
            utils.get_maven_path('r8', version),
            'r8-%s.pom' % version, is_master)
        if options.dry_run:
          print('Dry run, not actually creating maven repo for R8')
        else:
          utils.upload_file_to_cloud_storage(tagged_jar, maven_dst)
          utils.upload_file_to_cloud_storage(default_pom_file, maven_pom_dst)
          print('Maven repo root available at: %s' % GetMavenUrl(is_master))

      # Upload desugar_jdk_libs configuration to a maven compatible location.
      if file == utils.DESUGAR_CONFIGURATION:
        jar_name = 'desugar_jdk_libs_configuration-%s.jar' % version
        maven_dst = GetUploadDestination(
            utils.get_maven_path('desugar_jdk_libs_configuration', version),
                                 jar_name, is_master)

        with utils.TempDir() as tmp_dir:
          desugar_jdk_libs_configuration_jar = os.path.join(tmp_dir, jar_name)
          create_maven_release.generate_jar_with_desugar_configuration(
              utils.DESUGAR_CONFIGURATION,
              utils.LIBRARY_DESUGAR_CONVERSIONS_ZIP,
              desugar_jdk_libs_configuration_jar)

          if options.dry_run:
            print('Dry run, not actually creating maven repo for '
                + 'desugar configuration.')
            if options.dry_run_output:
              shutil.copyfile(
                  desugar_jdk_libs_configuration_jar,
                  os.path.join(options.dry_run_output, jar_name))
          else:
            utils.upload_file_to_cloud_storage(
                desugar_jdk_libs_configuration_jar, maven_dst)
            print('Maven repo root available at: %s' % GetMavenUrl(is_master))

if __name__ == '__main__':
  sys.exit(Main())
