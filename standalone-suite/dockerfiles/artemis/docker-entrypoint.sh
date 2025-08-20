#!/usr/bin/env bash

set -e

trap stop SIGTERM SIGINT SIGQUIT SIGHUP ERR

function stop {
    echo ""
    echo "Stop signal received. Clean up resources before stop container"
    echo ""
    artemis-controller.sh stop
    artemis-controller.sh umount_nfs
    exit 0
}

function start {
    echo ""
    echo "About to start artemis"
    echo ""
    artemis-controller.sh mount_nfs
    echo "nfs mounted"
    artemis-controller.sh start
    echo "artemis-controller.sh started"
}

function start_display {
    RHEL_VERSION=$(rpm -E %{rhel})
    echo ""
    echo "About to start xvfb/xvnc DISPLAY on ${RHEL_VERSION}"
    echo ""
    if (( RHEL_VERSION < 10 )); then
      Xvfb :99 -screen 0 1920x1080x24 &
      XVFB_PID=$!
      echo "xvfb DISPLAY=:99 started (pid $XVFB_PID)"
    else
      Xvnc :99 -geometry 1920x1080 -depth 24 -nolisten unix &
      XVFB_PID=$!
      echo "xvnc DISPLAY=:99 started (pid $XVFB_PID)"
    fi
    sleep 5
}

if [ $UID -eq 0 ]; then
  start_display
  groupadd -g "${ARTEMIS_GROUP_GID}" "${ARTEMIS_GROUP}"
  useradd -u "${ARTEMIS_USER_UID}" -d "${ARTEMIS_USER_HOME}" -m -g "${ARTEMIS_GROUP}" "${ARTEMIS_USER}"
  env | grep -E -v "(^_|^TERM|^SHLVL|^LS_COLORS|^PWD|^HOME|^SHELL|^USER|^LOGNAME|^PATH)" > /tmp/initial_envvars
  sed -i -e 's/^/export /' /tmp/initial_envvars
  echo "export DISPLAY=:99" >> /tmp/initial_envvars
  if [[ ${BASE_IMAGE} =~ .*ubi[7-8]:.* ]]; then
    unset SHLVL; unset LS_COLORS; unset PWD; unset HOME; unset SHELL; unset USER; unset LOGNAME; unset PATH
    if [[ ${BASE_IMAGE} =~ .*ubi7:.* ]]; then
      exec /usr/bin/setpriv --reuid="${ARTEMIS_USER_UID}" --regid="${ARTEMIS_GROUP_GID}" --inh-caps=-all --clear-group /bin/bash -l -c "$0" "$@"
    else
      exec /usr/bin/setpriv --reuid="${ARTEMIS_USER_UID}" --regid="${ARTEMIS_GROUP_GID}" --inh-caps=-all --init-groups /bin/bash -l -c "$0" "$@"
    fi
  else 
    exec /usr/bin/setpriv --reuid="${ARTEMIS_USER_UID}" --regid="${ARTEMIS_GROUP_GID}" --inh-caps=-all --init-groups --reset-env "$0" "$@"
  fi
  exit 1
fi

# shellcheck source=/dev/null
source /tmp/initial_envvars

start
tail -n +1 -F "/var/lib/artemis-instance/artemis-controller.log" &
while true; do
  sleep 5
done
