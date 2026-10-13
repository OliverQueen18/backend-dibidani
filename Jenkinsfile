pipeline {
  agent any
  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 40, unit: 'MINUTES')
  }
  triggers { pollSCM('H/10 * * * *') }
  parameters {
    booleanParam(name: 'RUN_TESTS', defaultValue: true, description: 'Exécuter les tests Maven')
    booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Déployer après le build')
    choice(name: 'DEPLOY_MODE', choices: ['same-host', 'ssh'], description: 'same-host : Jenkins et Docker Compose sur la même machine. ssh : transfert de l’image vers le serveur.')
    string(name: 'DEPLOY_PATH', defaultValue: '/opt/dibidani', description: 'Dossier Compose (docker-compose.yml + docker-compose.prod.yml + .env)')
    string(name: 'DEPLOY_HOST', defaultValue: '', description: 'Hôte SSH si DEPLOY_MODE=ssh, ex. deploy@dibidani.ml')
  }
  environment {
    IMAGE_NAME = 'dibidani-backend'
    COMPOSE_SERVICE = 'backend'
    IMAGE_ENV_VAR = 'BACKEND_IMAGE'
    COMPOSE_FILES = '-f docker-compose.yml -f docker-compose.prod.yml'
    SSH_CREDENTIALS_ID = 'dibidani-deploy-ssh'
  }
  stages {
    stage('Préparer') {
      steps {
        script {
          def sha = env.GIT_COMMIT ?: ''
          def shortSha = sha.length() >= 8 ? sha.substring(0, 8) : (env.BUILD_NUMBER ?: 'local')
          env.IMAGE_TAG = "${env.BUILD_NUMBER}-${shortSha}"
          env.FULL_IMAGE = "${env.IMAGE_NAME}:${env.IMAGE_TAG}"
          echo "Image : ${env.FULL_IMAGE}"
        }
      }
    }
    stage('Tests') {
      when { expression { params.RUN_TESTS } }
      steps {
        sh '''
          docker run --rm \
            -v "$PWD":/src -w /src \
            -v dibidani-m2:/root/.m2 \
            maven:3.9-eclipse-temurin-21-alpine \
            mvn -B test
        '''
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
        }
      }
    }
    stage('Image Docker') {
      steps {
        sh 'docker build -t "$FULL_IMAGE" -t "$IMAGE_NAME:latest" .'
      }
    }
    stage('Déployer') {
      when { expression { shouldDeploy() } }
      steps {
        script { deployImage() }
      }
    }
  }
  post {
    success { echo "Backend OK — ${env.FULL_IMAGE}" }
    failure { echo 'Échec du pipeline backend Dibidani.' }
  }
}

boolean shouldDeploy() {
  if (!params.DEPLOY) {
    return false
  }
  def branch = (env.BRANCH_NAME ?: env.GIT_BRANCH ?: '').replaceFirst(/^origin\//, '')
  return !branch || branch == 'main' || branch == 'master'
}

void deployImage() {
  if (params.DEPLOY_MODE == 'ssh') {
    if (!params.DEPLOY_HOST?.trim()) {
      error 'DEPLOY_HOST est obligatoire en mode ssh (ex. deploy@192.168.1.10).'
    }
    sshagent(credentials: [env.SSH_CREDENTIALS_ID]) {
      sh """
        docker save '${env.FULL_IMAGE}' | gzip | ssh -o StrictHostKeyChecking=accept-new ${params.DEPLOY_HOST} 'gunzip | docker load'
      """
      writeFile file: 'remote-deploy.sh', text: remoteDeployScript()
      sh "ssh -o StrictHostKeyChecking=accept-new ${params.DEPLOY_HOST} 'bash -s' < remote-deploy.sh"
    }
  } else {
    sh remoteDeployScript()
  }
}

String remoteDeployScript() {
  return """#!/bin/bash
set -euo pipefail
cd '${params.DEPLOY_PATH}'
if [ ! -f .env ]; then
  echo "Fichier .env introuvable dans ${params.DEPLOY_PATH}" >&2
  exit 1
fi
awk -v k='${env.IMAGE_ENV_VAR}' -v v='${env.FULL_IMAGE}' '
  BEGIN { f=0 }
  \$0 ~ "^" k "=" { print k "=" v; f=1; next }
  { print }
  END { if (!f) print k "=" v }
' .env > .env.tmp && mv .env.tmp .env
export ${env.IMAGE_ENV_VAR}='${env.FULL_IMAGE}'
docker compose ${env.COMPOSE_FILES} up -d --no-deps --no-build ${env.COMPOSE_SERVICE}
for i in \$(seq 1 36); do
  status=\$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' dibidani-backend 2>/dev/null || echo missing)
  echo "santé dibidani-backend: \$status"
  [ "\$status" = healthy ] && exit 0
  sleep 5
done
docker logs --tail 80 dibidani-backend || true
exit 1
"""
}
