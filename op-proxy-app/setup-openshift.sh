#!/usr/bin/env bash
set -euo pipefail

# Builds op-proxy-app locally and deploys it to OpenShift.
# Usage: ./setup-openshift.sh [NAMESPACE] [IMAGE_TAG]
# Example: ./setup-openshift.sh batch-jobs v0.1.0

if ! command -v oc &> /dev/null; then
    echo "Error: oc CLI not found. Please install OpenShift CLI first."
    exit 1
fi

NAMESPACE="${1:-batch-jobs}"
IMAGE_TAG="${2:-latest}"
IMAGE_NAME="op-proxy-app"
SERVICE_ACCOUNT="duplosa"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=========================================="
echo "Building op-proxy-app locally..."
echo "=========================================="
cd "$SCRIPT_DIR"
./gradlew clean build -DskipTests

echo ""
echo "=========================================="
echo "Creating namespace and project..."
echo "=========================================="
oc new-project "$NAMESPACE" 2>/dev/null || oc project "$NAMESPACE"

echo ""
echo "=========================================="
echo "Setting up OpenShift registry and building image..."
echo "=========================================="

# Get OpenShift registry (internal)
REGISTRY=$(oc registry info 2>/dev/null) || REGISTRY="image-registry.openshift-image-registry.svc:5000"
IMAGE="${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"

echo "Using registry: $REGISTRY"
echo "Target image: $IMAGE"

# Create BuildConfig and build image using oc
oc -n "$NAMESPACE" new-build --name="$IMAGE_NAME" \
  --binary \
  --strategy=docker \
  --docker-image="registry.access.redhat.com/ubi9/openjdk-21-runtime:latest" \
  2>/dev/null || true

oc -n "$NAMESPACE" start-build "$IMAGE_NAME" \
  --from-dir="$SCRIPT_DIR" \
  --follow

echo ""
echo "=========================================="
echo "Applying service account and RBAC..."
echo "=========================================="

# Apply predefined RBAC manifest
oc -n "$NAMESPACE" apply -f "$SCRIPT_DIR/rbac-op-proxy-app.yaml"

echo ""
echo "=========================================="
echo "Deploying op-proxy-app..."
echo "=========================================="

# Create Deployment manifest
cat > /tmp/batch-job-deployment.yaml <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: op-proxy-app
  namespace: $NAMESPACE
  labels:
    app: op-proxy-app
spec:
  replicas: 1
  selector:
    matchLabels:
      app: op-proxy-app
  template:
    metadata:
      labels:
        app: op-proxy-app
    spec:
      serviceAccountName: $SERVICE_ACCOUNT
      containers:
      - name: op-proxy-app
        image: ${REGISTRY}/${NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: QUARKUS_HTTP_HOST
          value: "0.0.0.0"
        - name: QUARKUS_HTTP_PORT
          value: "8080"
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: op-proxy-app
  namespace: $NAMESPACE
spec:
  selector:
    app: op-proxy-app
  ports:
  - port: 8080
    targetPort: 8080
    name: http
  type: ClusterIP
---
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: op-proxy-app
  namespace: $NAMESPACE
spec:
  to:
    kind: Service
    name: op-proxy-app
  port:
    targetPort: http
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Allow
EOF

oc -n "$NAMESPACE" apply -f /tmp/batch-job-deployment.yaml

echo ""
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
echo "namespace: $NAMESPACE"
echo "image: $IMAGE"
echo ""

ROUTE=$(oc -n "$NAMESPACE" get route op-proxy-app -o jsonpath='{.spec.host}' 2>/dev/null || echo "pending")
echo "API endpoint: https://${ROUTE}"
echo ""
echo "Check status: oc -n $NAMESPACE get deployment,pods"
echo "View logs: oc -n $NAMESPACE logs -l app=op-proxy-app -f"
echo ""
