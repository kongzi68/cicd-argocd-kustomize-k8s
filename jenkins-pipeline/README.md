# jenkins-pipeline


```bash
docker run --rm \
    -v /root/.docker/config.json:/root/.docker/config.json \
    139.224.134.35:9999/libs/skopeo/stable:latest \
    copy --insecure-policy --src-tls-verify=false --dest-tls-verify=false \
    --dest-authfile /root/.docker/config.json \
    --src-authfile /root/.docker/config.json \
    docker://bf-harbor.betalpha.com:8765/libs/python:bf-v3.10.14-bookworm \
    docker://192.168.31.199:11180/libs/python:bf-v3.10.14-bookworm
```
