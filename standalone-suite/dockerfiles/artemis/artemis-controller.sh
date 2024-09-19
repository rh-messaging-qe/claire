#!/usr/bin/env bash

set -e

PID_FILE="/var/lib/artemis-instance/artemis.pid"
LOG_FILE="/var/lib/artemis-instance/artemis-controller.log"

exec >> $LOG_FILE 2>&1

function mount_nfs() {
    if [[ -n "${NFS_MOUNTS}" ]]; then
        echo "NFS_MOUNTS found. NFS_MOUNTS=${NFS_MOUNTS}"
        echo "Start to mount file systems"
        IFS='|' read -r -a all_nfs_mounts <<< "${NFS_MOUNTS}"
        for i in "${all_nfs_mounts[@]}"; do
            echo "parsing: ${i}"
            IFS='#' read -r -a nfs_mount <<< "${i}"
            echo "mounting ${nfs_mount[0]} into ${nfs_mount[1]} with options ${nfs_mount[2]}"
            mkdir -p "${nfs_mount[1]}"
            sudo mount -t nfs -o "${nfs_mount[2]}" "${nfs_mount[0]}" "${nfs_mount[1]}"
            echo ""
        done
    fi
    echo ""
}

function umount_nfs() {
    if [[ -n "${NFS_MOUNTS}" ]]; then
        echo "NFS_MOUNTS found. NFS_MOUNTS=${NFS_MOUNTS}"
        echo "Start to umount file systems"
        IFS='|' read -r -a all_nfs_mounts <<< "${NFS_MOUNTS}"
        for i in "${all_nfs_mounts[@]}"; do
            echo "parsing: ${i}"
            IFS='#' read -r -a nfs_mount <<< "${i}"
            echo "umount ${nfs_mount[1]}"
            sudo umount -f -t nfs "${nfs_mount[1]}"
            echo ""
        done
    fi
    echo ""
}

function start() {
    echo ""
    echo "Going to start artemis"
    nohup /var/lib/artemis-instance/bin/artemis run 2>&1 &
    sleep 2
    pidof java > "${PID_FILE}"
}

function force_stop() {
    echo ""
    echo "Force stopping artemis"
    echo ""
    stop_command "-9"
    echo ""
    echo "Artemis force stopped"
    echo ""
}

function stop() {
    echo ""
    echo "Stopping artemis"
    echo ""
    stop_command
    echo ""
    echo "Artemis stopped"
    echo ""
}

function stop_command() {
    pid=$(cat "${PID_FILE}")
    kill "$@" "${pid}"
    sleep 1

    running=0
    for i in 1 2 3 4 5 6 7 8 9 10; do
        if ! ps -p "${pid}" > /dev/null ; then
            running=1
            break
        fi
        sleep 1
    done

    if [[ "${running}" -eq 0 ]]; then
        echo "Could not stop process ${pid}, going to kill it"
        kill -9 "${pid}"
        sleep 1
    fi
    
    rm "${PID_FILE}"

    sleep 2
}

"$@"

