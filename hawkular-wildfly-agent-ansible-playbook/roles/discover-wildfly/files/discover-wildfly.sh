#!/bin/sh
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

#  First find the WildFly servers that are actually running
_openJavaFiles=$(/usr/sbin/lsof -w -c java | tail -n +2 | tr -s [:blank:] | grep " REG " | cut --delimiter=' ' -f9 | sort -u)
_running=$(
  for dir in $_openJavaFiles; do
    _d=${dir};
    while [[ "$_d" != "" && ! -e "${_d}/modules/system/layers/base" ]]; do
      _d=${_d%/*}
    done
    if [ ! -z ${_d} ]; then
      echo ${_d};
    fi
  done | uniq
)

# Now find the WildFly servers that are installed but may not be running
# This only goes down a max depth of 4 directories (so it will find /opt/a/b/wildfly but not /opt/a/b/c/wildfly).
_scanDirectories=${*:-"/opt /usr /home"}

_installed=$(
  for dir in $_scanDirectories
  do
    find $dir -maxdepth 8 -type d -path "*/modules/system/layers/base" -print 2>/dev/null | xargs -I '{}' echo {} | rev | cut -d'/' -f5- | rev
  done
)

#echo "* WF are running: $_running"
#echo "* WF are installed: $_installed"

echo $_running $_installed | tr ' ' '\n' | sort -u
