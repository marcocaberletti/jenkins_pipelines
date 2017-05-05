pipeline {
  agent none

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@daily') }

  parameters {
    string(name: 'BRANCH',    defaultValue: 'develop', description: '' )
  }

  stages {
    stage('prepare code'){
      agent { label 'generic' }
      steps {
        sh "git clone https://github.com/marcocaberletti/iam-deployment-test.git iam-nginx"
        script {
          dir('iam-nginx/iam/nginx'){
            stash include: './*', name: 'iam-nginx'
          }
        }
        sh "git clone https://github.com/marcocaberletti/docker.git docker-images"
        script {
          dir('docker-images/trust-anchors'){
            stash include: './*', name: 'trust-anchors'
          }
        }
        sh "git clone -b ${params.BRANCH} https://github.com/marcocaberletti/iam-robot-testsuite.git iam-ts"
        script{
          dir('iam-ts/docker'){
            stash include: './*', name: 'iam-ts'
          }
        }
      }
    }

    stage('create images'){
      steps {
        parallel(
            "iam-nginx": {
              node('docker'){
                unstash 'iam-nginx'
                sh "docker build --no-cache -t italiangrid/iam-nginx:latest ."
                sh "docker tag italiangrid/iam-nginx:latest ${DOCKER_REGISTRY_HOST}/italiangrid/iam-nginx:latest"
                sh "docker push ${DOCKER_REGISTRY_HOST}/italiangrid/iam-nginx:latest"
              }
            },
            "trust-anchors": {
              node('docker'){
                unstash 'trust-anchors'
                sh './build-image.sh'
                sh './push-image.sh'
              }
            },
            "iam-testsuite": {
              node('docker'){
                unstash 'iam-ts'
                sh './build-image.sh'
                sh './push-image.sh'
              }
            }
            )
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }
  }
}