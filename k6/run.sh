#!/bin/bash

SCRIPT="${1:?Usage: ./k6/run.sh <product-list|product-detail> [k6 options...]}"
shift

K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_EXPORT="k6/${SCRIPT}-report.html" \
k6 run "k6/${SCRIPT}-test.js" "$@"
