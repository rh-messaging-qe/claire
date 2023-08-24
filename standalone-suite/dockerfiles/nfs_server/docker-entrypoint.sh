#!/bin/bash
#
# Based on https://github.com/GoogleCloudPlatform/nfs-server-docker
#
#
# Copyright 2015 The Kubernetes Authors.
# Copyright 2018 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

trap stop SIGTERM SIGINT SIGQUIT SIGHUP ERR

function start() {
    unset gid
    # accept "-G gid" option
    while getopts "G:" opt; do
        # shellcheck disable=SC2220
        case ${opt} in
            G) gid=${OPTARG};;
        esac
    done
    shift $((OPTIND - 1))

    # prepare /etc/exports
    echo "/exports *(fsid=0,rw,sync,no_subtree_check,root_squash,insecure)" >> /etc/exports
    for i in "$@"; do
        echo "${i} *(rw,sync,no_subtree_check,no_root_squash,insecure)" >> /etc/exports
        if [ -v gid ] ; then
            chmod 070 "${i}"
            chgrp "${gid}" "${i}"
        fi
        echo "Serving ${i}"
    done

    # start rpcbind if it is not started yet
    /usr/sbin/rpcinfo 127.0.0.1 > /dev/null 2>&1; s=$?
    if [ ${s} -ne 0 ]; then
       echo "Starting rpcbind"
       /sbin/rpcbind -w
    fi

    mount -t nfsd nfds /proc/fs/nfsd
    /usr/sbin/rpc.mountd -N 2 -N 3
    /usr/sbin/exportfs -r
    /usr/sbin/rpc.nfsd -G 10 -N 3 10
    echo "NFS started"
}

function stop() {
    echo "Stopping NFS"

    echo "Stopping nfsd"
    /usr/sbin/rpc.nfsd 0

    echo "Stopping exported file systems"
    /usr/sbin/exportfs -au
    /usr/sbin/exportfs -f

    echo "Stopping rpc.mountd"
    kill "$(pidof rpc.mountd)"

    echo "Umount /proc/fs/nfsd"
    umount -f /proc/fs/nfsd

    echo "Cleaning /etc/exports"
    echo > /etc/exports

    echo "Stopping rpcbind"
    kill "$(pidof rpcbind)"

    sleep 2
    exit 0
}

start "$@"

while true; do
    sleep 5
done