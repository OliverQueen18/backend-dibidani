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
    string(name: 'DEPLOY_PATH', defaultValue: '/home/adminubuntu/OliveApps', description: 'Dossier du compose VPS (docker-compose.yml + docker-compose.dibidani.yml + .env)')
    string(name: 'DEPLOY_HOST', defaultValue: 'adminubuntu@dibidani.com', description: 'Hôte SSH si hub/ssh, ex. root@vps')
  }
  environment {
    REGISTRY = 'oliverqueen18'
    IMAGE_NAME = 'dibidani-backend'
    COMPOSE_SERVICE = 'backend-dibidani'
    COMPOSE_FILES = '-f docker-compose.yml -f docker-compose.dibidani.yml'
    SSH_CREDENTIALS_ID = 'ssh-server-credentials'
    DOCKERHUB_CREDENTIALS_ID = 'dockerhub-credentials'
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
    withSsh {
      sh """
        docker save '${env.FULL_IMAGE}' | gzip | ssh -i "\$SSH_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new ${params.DEPLOY_HOST} 'gunzip | docker load'
      """
      writeFile file: 'remote-deploy.sh', text: remoteUpScript()
      sh """
        ssh -i "\$SSH_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new ${params.DEPLOY_HOST} 'bash -s' < remote-deploy.sh
      """
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
  writeFile file: 'remote-deploy.sh', text: remotePullScript()
  withSsh {
    sh """
      ssh -i "\$SSH_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new ${params.DEPLOY_HOST} 'bash -s' < remote-deploy.sh
    """
  }
}

void withSsh(Closure body) {
  if (!params.DEPLOY_HOST?.trim()) {
    error 'DEPLOY_HOST est obligatoire (SSH vers le VPS).'
  }
  withCredentials([sshUserPrivateKey(credentialsId: env.SSH_CREDENTIALS_ID, keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
    body()
  }
}

String remotePullScript() {
  return """#!/bin/bash
set -euo pipefail
cd '${params.DEPLOY_PATH}'
if [ ! -f docker-compose.dibidani.yml ]; then
  echo "docker-compose.dibidani.yml introuvable dans ${params.DEPLOY_PATH}" >&2
  exit 1
fi
${waitContainerFn()}
docker compose ${env.COMPOSE_FILES} pull postgres-dibidani ${env.COMPOSE_SERVICE}
docker compose ${env.COMPOSE_FILES} up -d postgres-dibidani
wait_container postgres-dibidani 24
docker compose ${env.COMPOSE_FILES} pull image-studio-dibidani && docker compose ${env.COMPOSE_FILES} up -d image-studio-dibidani || echo "image-studio absent sur Docker Hub — l'API continue (cadrage local)."
docker compose ${env.COMPOSE_FILES} up -d --no-deps --force-recreate ${env.COMPOSE_SERVICE}
wait_container dibidani-backend 48
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
${waitContainerFn()}
docker compose ${env.COMPOSE_FILES} up -d postgres-dibidani
wait_container postgres-dibidani 24
docker compose ${env.COMPOSE_FILES} up -d --no-deps --no-build ${env.COMPOSE_SERVICE}
wait_container dibidani-backend 48
"""
}

String waitContainerFn() {
  return """
wait_container() {
  local name="\$1" max="\$2" i status
  for i in \$(seq 1 "\$max"); do
    status=\$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "\$name" 2>/dev/null || echo missing)
    echo "santé \$name: \$status"
    if [ "\$status" = healthy ]; then
      return 0
    fi
    sleep 5
  done
  docker compose ${env.COMPOSE_FILES} ps || true
  docker logs --tail 120 "\$name" || true
  return 1
}
"""
}
