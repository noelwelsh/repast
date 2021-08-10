#!/usr/bin/env bash
set -euo pipefail

latexmk -xelatex -pvc "$@"
