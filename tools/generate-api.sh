#!/usr/bin/env bash
# Regenerates the api module from the server's own contract.
#
# The spec is a snapshot taken with `curl .../api/v1/openapi.json` (or
# `kite openapi`) and committed as api/openapi.json. Regenerating is how the
# client stays honest: no hand-written request, no remembered field.
set -euo pipefail

VERSION="${OPENAPI_GENERATOR_VERSION:-7.10.0}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${HOME}/.cache/kite-openapi/openapi-generator-cli-${VERSION}.jar"
OUT="${ROOT}/build/generated-api"

if [ ! -f "${JAR}" ]; then
    mkdir -p "$(dirname "${JAR}")"
    echo "downloading openapi-generator ${VERSION}"
    curl -fL --retry 3 --max-time 300 -o "${JAR}" \
        "https://maven.aliyun.com/repository/public/org/openapitools/openapi-generator-cli/${VERSION}/openapi-generator-cli-${VERSION}.jar"
fi

rm -rf "${OUT}"
java -jar "${JAR}" generate \
    -i "${ROOT}/api/openapi.json" \
    -g kotlin \
    -o "${OUT}" \
    --library jvm-retrofit2 \
    --additional-properties "serializationLibrary=gson,dateLibrary=string,useCoroutines=true,packageName=com.kiteplus.kite.mobile.api,modelPackage=com.kiteplus.kite.mobile.api.model,apiPackage=com.kiteplus.kite.mobile.api.endpoint,sourceFolder=src/main/java"

# Replace the checked-in copy wholesale. The generator has no incremental mode,
# and a half-updated generated tree is worse than none.
rm -rf "${ROOT}/api/src/main/java"
mkdir -p "${ROOT}/api/src/main"
cp -r "${OUT}/src/main/java" "${ROOT}/api/src/main/java"

echo "generated into api/src/main/java ($(find "${ROOT}/api/src/main/java" -name '*.kt' | wc -l) files)"
