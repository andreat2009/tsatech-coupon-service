pipeline {
    agent {
        kubernetes {
            namespace 'ecommerce'
            yaml '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    app: jenkins-agent-maven
spec:
  volumes:
  - name: workspace-volume
    emptyDir: {}
  - name: m2-repo
    emptyDir: {}
  - name: platform-truststore
    secret:
      secretName: platform-truststore
  containers:
  - name: jnlp
    image: jenkins/inbound-agent:latest
    workingDir: /tmp/agent
    env:
    - name: JENKINS_AGENT_WORKDIR
      value: /tmp/agent
    - name: JENKINS_JAVA_OPTS
      value: "-Djavax.net.ssl.trustStore=/etc/truststore/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
    - name: JENKINS_URL
      value: "https://jenkins-platform.apps-crc.testing/"
    - name: JENKINS_WEB_SOCKET
      value: "true"
    - name: HOME
      value: /tmp/agent
    - name: XDG_CONFIG_HOME
      value: /tmp/agent/.config
    volumeMounts:
    - name: workspace-volume
      mountPath: /tmp/agent
    - name: platform-truststore
      mountPath: /etc/truststore
      readOnly: true
  - name: maven
    image: maven:3.9.9-eclipse-temurin-17
    command: ['cat']
    tty: true
    env:
    - name: MAVEN_CONFIG
      value: /tmp/.m2
    - name: HOME
      value: /tmp/agent
    - name: XDG_CONFIG_HOME
      value: /tmp/agent/.config
    volumeMounts:
    - name: m2-repo
      mountPath: /tmp/.m2
    - name: workspace-volume
      mountPath: /tmp/agent
  - name: oc
    image: image-registry.openshift-image-registry.svc:5000/openshift/cli:latest
    command: ['cat']
    tty: true
    env:
    - name: HOME
      value: /tmp/agent
    - name: XDG_CONFIG_HOME
      value: /tmp/agent/.config
    volumeMounts:
    - name: workspace-volume
      mountPath: /tmp/agent
'''
        }
    }
    environment {
        APP_NAME = "coupon-service"
        HELM_RELEASE = "${params.HELM_RELEASE ?: 'coupon-service'}"
        CHART_PATH = "${params.CHART_PATH ?: 'helm'}"
    }
    stages {
        stage('Checkout') {
            steps { checkout scm }
        }
        stage('OpenShift Login') {
            steps {
                container('oc') {
                    withCredentials([string(credentialsId: 'openshift-token', variable: 'OPENSHIFT_TOKEN')]) {
                        sh '''
                          set -euo pipefail
                          OPENSHIFT_SERVER="${OPENSHIFT_SERVER:-https://api.crc.testing:6443}"
                          OPENSHIFT_PROJECT="${OPENSHIFT_NAMESPACE:-${OPENSHIFT_PROJECT:-ecommerce}}"
                          oc login --token="$OPENSHIFT_TOKEN" --server="$OPENSHIFT_SERVER" --insecure-skip-tls-verify=true
                          oc project "$OPENSHIFT_PROJECT"
                        '''
                    }
                }
            }
        }
        stage('Build Maven') {
            steps {
                container('maven') { sh 'mvn clean package -DskipTests' }
            }
        }
        stage('Build Image') {
            steps {
                container('oc') {
                    sh '''
                      set -euo pipefail
                      OPENSHIFT_PROJECT="${OPENSHIFT_NAMESPACE:-${OPENSHIFT_PROJECT:-ecommerce}}"
                      oc new-build --name="${APP_NAME}" --binary --strategy=docker >/dev/null 2>&1 || true
                      # Keep build history small to reduce disk usage on CRC
                      oc patch bc/${APP_NAME} --type=merge -p '{"spec":{"successfulBuildsHistoryLimit":2,"failedBuildsHistoryLimit":2}}' >/dev/null 2>&1 || true
                      oc start-build "${APP_NAME}" --from-dir=. --follow
                    '''
                }
            }
        }
        stage('Helm Deploy') {
            steps {
                container('oc') {
                    sh '''
                      set -euo pipefail
                      OPENSHIFT_PROJECT="${OPENSHIFT_NAMESPACE:-${OPENSHIFT_PROJECT:-ecommerce}}"
                      IMAGE_REPOSITORY="${IMAGE_REPOSITORY:-image-registry.openshift-image-registry.svc:5000/${OPENSHIFT_PROJECT}/${APP_NAME}}"
                      IMAGE_TAG="${IMAGE_TAG:-latest}"
                      IMAGE_REF=$(oc -n "$OPENSHIFT_PROJECT" get istag "${APP_NAME}:latest" -o jsonpath='{.image.dockerImageReference}' 2>/dev/null || true)
                      IMAGE_DIGEST=""
                      if [ -n "$IMAGE_REF" ] && echo "$IMAGE_REF" | grep -q "@sha256:"; then
                        IMAGE_DIGEST="${IMAGE_REF##*@}"
                      fi
                      if ! command -v helm >/dev/null 2>&1; then
                        ARCH=$(uname -m)
                        if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
                          HELM_PKG="helm-v3.14.4-linux-arm64.tar.gz"
                        else
                          HELM_PKG="helm-v3.14.4-linux-amd64.tar.gz"
                        fi
                        curl -sSL "https://get.helm.sh/${HELM_PKG}" -o /tmp/helm.tgz
                        tar -xzf /tmp/helm.tgz -C /tmp
                        if [ -d /tmp/linux-arm64 ]; then
                          chmod +x /tmp/linux-arm64/helm
                          export PATH="/tmp/linux-arm64:$PATH"
                        else
                          chmod +x /tmp/linux-amd64/helm
                          export PATH="/tmp/linux-amd64:$PATH"
                        fi
                      fi

                      helm lint "${CHART_PATH}"
                      helm upgrade --install "${HELM_RELEASE}" "${CHART_PATH}" \
                        --namespace "${OPENSHIFT_PROJECT}" \
                        --set image.repository="${IMAGE_REPOSITORY}" \
                        --set image.tag="${IMAGE_TAG}" \
                        --set image.digest="${IMAGE_DIGEST}" \
                        --set replicaCount=1 \
                        --set truststore.enabled=true \
                        --set secret.name="${APP_NAME}-secret"
                    '''
                }
            }
        }
        stage('Rollout & Smoke') {
            steps {
                container('oc') {
                    sh '''
                      set -euo pipefail
                      OPENSHIFT_PROJECT="${OPENSHIFT_NAMESPACE:-${OPENSHIFT_PROJECT:-ecommerce}}"
                      oc rollout status deployment/${APP_NAME} --timeout=240s
                      ROUTE_HOST=$(oc -n "$OPENSHIFT_PROJECT" get route "${APP_NAME}" -o jsonpath='{.spec.host}' || true)
                      if [ -n "$ROUTE_HOST" ] && command -v curl >/dev/null 2>&1; then
                        smoke_ok=0
                        SMOKE_ATTEMPTS="${SMOKE_RETRY_ATTEMPTS:-15}"
                        SMOKE_SLEEP_SECONDS="${SMOKE_RETRY_SLEEP_SECONDS:-4}"
                        for attempt in $(seq 1 "$SMOKE_ATTEMPTS"); do
                          if curl -k -fsS --http1.1 --connect-timeout 5 --max-time 12 "https://${ROUTE_HOST}/actuator/health" >/dev/null; then
                            smoke_ok=1
                            break
                          fi
                          if curl -k -fsS --http1.1 --connect-timeout 5 --max-time 12 "https://${ROUTE_HOST}/" >/dev/null; then
                            smoke_ok=1
                            break
                          fi
                          echo "Smoke check attempt ${attempt}/${SMOKE_ATTEMPTS} failed for ${ROUTE_HOST}; retrying..."
                          sleep "$SMOKE_SLEEP_SECONDS"
                        done
                        if [ "$smoke_ok" -ne 1 ]; then
                          echo "Smoke check failed for ${ROUTE_HOST} after retries"
                          exit 35
                        fi
                      fi
                    '''
                }
            }
        }
    }
}
