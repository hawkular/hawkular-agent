#!/bin/python
#
# Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
# and other contributors as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Scans for running and installed (but not running) WildFly application servers.
# This takes an option set of directory names to scan. If none are given, /opt, /usr, and /home are scanned.

import os
import subprocess
import sys

#  First find the WildFly servers that are actually running
_running = set()

_openJavaFiles = subprocess.check_output('/usr/sbin/lsof -w -c java | tail -n +2 | tr -s [:blank:] | grep " REG " | cut --delimiter=\' \' -f9 | sort -u', shell=True)

for line in _openJavaFiles.split("\n"):
  while line and not os.path.isdir(line + "/modules/system/layers/base"):
    parent = os.path.dirname(line)
    if line == parent:
      line = "" # we are at the root, stop checking
    else:
      line = parent
  if line: _running.add(line)

# Now find the WildFly servers that are installed but may not be running
# This only goes down a max depth of 4 directories (so it will find /opt/a/b/wildfly but not /opt/a/b/c/wildfly).
_installed = set()

if len(sys.argv) == 1:
  _scanDirectories = ["/opt", "/usr", "/home"]
else:
  _scanDirectories = sys.argv[1:]

for sdir in _scanDirectories:
  find_results = subprocess.check_output('find ' + sdir + ' -maxdepth 8 -type d -path "*/modules/system/layers/base" -print 2>/dev/null | xargs -I \'{}\' echo {} | rev | cut -d\'/\' -f5- | rev', shell=True)
  for line in find_results.split("\n"):
    if line: _installed.add(line)

#print "* WF are running: ", _running
#print "* WF are installed:", _installed
#print "* WF are running and installed:", _running | _installed

for discovered in sorted(_running | _installed):
  print discovered
