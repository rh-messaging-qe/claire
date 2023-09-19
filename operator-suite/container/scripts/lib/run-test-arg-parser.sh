#!/bin/bash
#
# ARG_OPTIONAL_BOOLEAN([debug],[d],[enable debug],[off])
# ARG_OPTIONAL_SINGLE([image-tag],[i],[claire image tag],[activemq-artemis-v1.0.15])
# ARG_OPTIONAL_REPEATED([make-envvar],[e],[makefile environment variable],[])
# ARG_OPTIONAL_SINGLE([kubeconfig],[k],[kubeconfig file, if not specified will try to use KUBECONFIG env var and if it do not exist will try to use ${HOME}/.kube/config],[])
# ARG_OPTIONAL_SINGLE([make-target],[m],[claire make target],[operator_test_smoke])
# ARG_OPTIONAL_SINGLE([namespace],[n],[cluster namespace used to deploy],[claire])
# ARG_OPTIONAL_SINGLE([test-completion-wait-check],[w],[how much seconds wait between check if the test execution was completed],[60])
# ARG_HELP([])
# ARGBASH_GO()
# needed because of Argbash --> m4_ignore([
### START OF CODE GENERATED BY Argbash v2.9.0 one line above ###
# Argbash is a bash code generator used to get arguments parsing right.
# Argbash is FREE SOFTWARE, see https://argbash.io for more info
# Generated online by https://argbash.io/generate


die()
{
	local _ret="${2:-1}"
	test "${_PRINT_HELP:-no}" = yes && print_help >&2
	echo "$1" >&2
	exit "${_ret}"
}


begins_with_short_option()
{
	local first_option all_short_options='diekmnwh'
	first_option="${1:0:1}"
	test "$all_short_options" = "${all_short_options/$first_option/}" && return 1 || return 0
}

# THE DEFAULTS INITIALIZATION - OPTIONALS
_arg_debug="off"
_arg_image_namespace="rhmessagingqe"
_arg_image_tag="activemq-artemis-1.1.0"
_arg_make_envvar=()
_arg_kubeconfig=
_arg_make_target="operator_test_smoke"
_arg_namespace="claire"
_arg_test_completion_wait_check="60"


print_help()
{
	printf 'Usage: %s [-d|--(no-)debug] [-r|--image-namespace <arg>] [-i|--image-tag <arg>] [-e|--make-envvar <arg>] [-k|--kubeconfig <arg>] [-m|--make-target <arg>] [-n|--namespace <arg>] [-w|--test-completion-wait-check <arg>] [-h|--help]\n' "$0"
	printf '\t%s\n' "-d, --debug, --no-debug: enable debug (default: $_arg_debug)"
	printf '\t%s\n' "-r, --image-namespace: claire image repo namespace (default: '$_arg_image_namespace')"
	printf '\t%s\n' "-i, --image-tag: claire image tag (default: '$_arg_image_tag')"
	printf '\t%s\n' "-e, --make-envvar: makefile environment variable (default: none)"
	printf '\t%s\n' "-k, --kubeconfig: kubeconfig file, if not specified will try to use KUBECONFIG env var and if it do not exist will try to use ${HOME}/.kube/config (no default)"
	printf '\t%s\n' "-m, --make-target: claire make target (default: '$_arg_make_target')"
	printf '\t%s\n' "-n, --namespace: cluster namespace used to deploy (default: '$_arg_namespace')"
	printf '\t%s\n' "-w, --test-completion-wait-check: how much seconds wait between check if the test execution was completed (default: '$_arg_test_completion_wait_check')"
	printf '\t%s\n' "-h, --help: Prints help"
}


parse_commandline()
{
	while test $# -gt 0
	do
		_key="$1"
		case "$_key" in
			-d|--no-debug|--debug)
				_arg_debug="on"
				test "${1:0:5}" = "--no-" && _arg_debug="off"
				;;
			-d*)
				_arg_debug="on"
				_next="${_key##-d}"
				if test -n "$_next" -a "$_next" != "$_key"
				then
					{ begins_with_short_option "$_next" && shift && set -- "-d" "-${_next}" "$@"; } || die "The short option '$_key' can't be decomposed to ${_key:0:2} and -${_key:2}, because ${_key:0:2} doesn't accept value and '-${_key:2:1}' doesn't correspond to a short option."
				fi
				;;
			-r|--image-namespace)
				test $# -lt 2 && die "Missing value for the optional argument '$_key'." 1
				_arg_image_namespace="$2"
				shift
				;;
			--image-namespace=*)
				_arg_image_namespace="${_key##--image-namespace=}"
				;;
			-r*)
				_arg_image_namespace="${_key##-i}"
				;;
			-i|--image-tag)
				test $# -lt 2 && die "Missing value for the optional argument '$_key'." 1
				_arg_image_tag="$2"
				shift
				;;
			--image-tag=*)
				_arg_image_tag="${_key##--image-tag=}"
				;;
			-i*)
				_arg_image_tag="${_key##-i}"
				;;
			-e|--make-envvar)
				test $# -lt 2 && die "Missing value for the optional argument '$_key'." 1
				_arg_make_envvar+=("$2")
				shift
				;;
			--make-envvar=*)
				_arg_make_envvar+=("${_key##--make-envvar=}")
				;;
			-e*)
				_arg_make_envvar+=("${_key##-e}")
				;;
			-k|--kubeconfig)
				test $# -lt 2 && die "Missing value for the optional argument '$_key'." 1
				_arg_kubeconfig="$2"
				shift
				;;
			--kubeconfig=*)
				_arg_kubeconfig="${_key##--kubeconfig=}"
				;;
			-k*)
				_arg_kubeconfig="${_key##-k}"
				;;
			-m|--make-target)
				test $# -lt 2 && die "Missing value for the optional argument '$_key'." 1
				_arg_make_target="$2"
				shift
				;;
			--make-target=*)
				_arg_make_target="${_key##--make-target=}"
				;;
			-m*)
				_arg_make_target="${_key##-m}"
				;;
			-n|--namespace)
				test $# -lt 2 && die "Missing value for the optional argument '$_key'." 1
				_arg_namespace="$2"
				shift
				;;
			--namespace=*)
				_arg_namespace="${_key##--namespace=}"
				;;
			-n*)
				_arg_namespace="${_key##-n}"
				;;
			-w|--test-completion-wait-check)
				test $# -lt 2 && die "Missing value for the optional argument '$_key'." 1
				_arg_test_completion_wait_check="$2"
				shift
				;;
			--test-completion-wait-check=*)
				_arg_test_completion_wait_check="${_key##--test-completion-wait-check=}"
				;;
			-w*)
				_arg_test_completion_wait_check="${_key##-w}"
				;;
			-h|--help)
				print_help
				exit 0
				;;
			-h*)
				print_help
				exit 0
				;;
			*)
				_PRINT_HELP=yes die "FATAL ERROR: Got an unexpected argument '$1'" 1
				;;
		esac
		shift
	done
}

parse_commandline "$@"

# OTHER STUFF GENERATED BY Argbash

### END OF CODE GENERATED BY Argbash (sortof) ### ])
# [ <-- needed because of Argbash
# ] <-- needed because of Argbash