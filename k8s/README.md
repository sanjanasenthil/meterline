# Local kind Runbook

Build and load the app image:

```bash
kind create cluster --config k8s/kind-cluster.yml
docker build -t meterline:local .
kind load docker-image meterline:local
kubectl apply -f k8s/postgres.yml
kubectl apply -f k8s/kafka.yml
kubectl apply -f k8s/app.yml
kubectl rollout status deployment/meterline
```

Pod deletion recovery check:

```bash
kubectl delete pod -l app=meterline --wait=false
kubectl rollout status deployment/meterline
mvn test
```

Meterline relies on deterministic event IDs plus PostgreSQL uniqueness for safe recovery. Kafka is treated as at-least-once delivery; duplicate messages are expected and harmless because the database writer is idempotent.
