
set -euo pipefail
URLS=(
  "https://unpkg.com/react@18.3.1/umd/react.production.min.js"
  "https://unpkg.com/react-dom@18.3.1/umd/react-dom.production.min.js"
  "https://unpkg.com/@babel/standalone@7.26.4/babel.min.js"
)
for url in "${URLS[@]}"; do
  hash=$(curl -fsSL "$url" | openssl dgst -sha384 -binary | openssl base64 -A)
  echo "<script src=\"$url\" integrity=\"sha384-$hash\" crossorigin=\"anonymous\"></script>"
done
