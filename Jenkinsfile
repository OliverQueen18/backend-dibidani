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
    choice(name: 'DEPLOY_MODE', choices: ['hub', 'ssh', 'same-host'], description: 'hub : Docker Hub + compose pull sur le VPS. ssh : docker save. same-host : Jenkins sur le serveur.')
    string(name: 'DEPLOY_PATH', defaultValue: '/opt', description: 'Dossier du compose VPS (docker-compose.yml + docker-compose.dibidani.yml + .env)')
    string(name: 'DEPLOY_HOST', defaultValue: '', description: 'Hôte SSH si hub/ssh, ex. root@vps')
  }
  environment {
    REGISTRY = 'oliverqueen18'
    IMAGE_NAME = 'dibidani-backend'
    COMPOSE_SERVICE = 'backend-dibidani'
    COMPOSE_FILES = '-f docker-compose.yml -f docker-compose.dibidani.yml'
    SSH_CREDENTIALS_ID = 'dibidani-deploy-ssh'
    DOCKERHUB_CREDENTIALS_ID = 'dockerhub-oliverqueen18'
  }
  stages {
    stage('Préparer') {
      steps {
        script {
          def sha = env.GIT_COMMIT ?: ''
          def shortSha = sha.length() >= 8 ? sha.substring(0, 8) : (env.BUILD_NUMBER ?: 'local')
          env.IMAGE_TAG = "${env.BUILD_NUMBER}-${shortSha}"
          env.HUB_IMAGE = "${env.REGISTRY}/${env.IMAGE_NAME}"
          env.FULL_IMAGE = "${env.HUB_IMAGE}:${env.IMAGE_TAG}"
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
        sh 'docker build -t "$FULL_IMAGE" -t "$HUB_IMAGE:latest" -t "$IMAGE_NAME:latest" .'
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
  if (params.DEPLOY_MODE == 'hub') {
    pushHub()
    remoteComposePull()
    return
  }
  if (params.DEPLOY_MODE == 'ssh') {
    if (!params.DEPLOY_HOST?.trim()) {
      error 'DEPLOY_HOST est obligatoire en mode ssh.'
    }
    sshagent(credentials: [env.SSH_CREDENTIALS_ID]) {
      sh """
        docker save '${env.FULL_IMAGE}' | gzip | ssh -o StrictHostKeyChecking=accept-new ${params.DEPLOY_HOST} 'gunzip | docker load'
      """
      writeFile file: 'remote-deploy.sh', text: remoteUpScript()
      sh "ssh -o StrictHostKeyChecking=accept-new ${params.DEPLOY_HOST} 'bash -s' < remote-deploy.sh"
    }
    return
  }
  sh remoteUpScript()
}

void pushHub() {
  withCredentials([usernamePassword(credentialsId: env.DOCKERHUB_CREDENTIALS_ID, usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
    sh '''
      echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
      docker push "$FULL_IMAGE"
      docker push "$HUB_IMAGE:latest"
      docker logout
    '''
  }
}

void remoteComposePull() {
  if (!params.DEPLOY_HOST?.trim()) {
    error 'DEPLOY_HOST est obligatoire en mode hub (SSH vers le VPS).'
  }
  writeFile file: 'remote-deploy.sh', text: remotePullScript()
  sshagent(credentials: [env.SSH_CREDENTIALS_ID]) {
    sh "ssh -o StrictHostKeyChecking=accept-new ${params.DEPLOY_HOST} 'bash -s' < remote-deploy.sh"
  }
}

String remotePullScript() {
  return """#!/bin/bash
set -euo pipefail
cd '${params.DEPLOY_PATH}'
docker compose ${env.COMPOSE_FILES} pull ${env.COMPOSE_SERVICE}
docker compose ${env.COMPOSE_FILES} up -d --no-deps --force-recreate ${env.COMPOSE_SERVICE}
${healthLoop()}
"""
}

String remoteUpScript() {
  return """#!/bin/bash
set -euo pipefail
cd '${params.DEPLOY_PATH}'
if [ ! -f .env ]; then
  echo "Fichier .env introuvable dans ${params.DEPLOY_PATH}" >&2
  exit 1
fi
docker compose ${env.COMPOSE_FILES} up -d --no-deps --no-build ${env.COMPOSE_SERVICE}
${healthLoop()}
"""
}

String healthLoop() {
  return '''
for i in $(seq 1 36); do
  status=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' dibidani-backend 2>/dev/null || echo missing)
  echo "santé dibidani-backend: $status"
  [ "$status" = healthy ] && exit 0
  sleep 5
done
docker logs --tail 80 dibidani-backend || true
exit 1
'''
}
