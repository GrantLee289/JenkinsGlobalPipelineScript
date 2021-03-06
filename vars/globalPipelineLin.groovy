def call(Map pipelineParameters) {

    pipeline {
        agent any

        tools {
            maven 'MAVEN-3.6.0'
        }

        options {
            disableConcurrentBuilds()
        }

        triggers {
            pollSCM('H/2 * * * *')
        }

        stages {

            stage('Get params') {
                steps {
                    script {
                        if (pipelineParameters != null) {
                            REPO = pipelineParameters.scmUrl
                            BRANCH = pipelineParameters.branch
                            PROJECT_NAME = pipelineParameters.projectName
                            DOCKERUSER = pipelineParameters.dockerUser
                        }
                    }
                }
            }

            stage('User or SCM?') {
                steps {
                    script {
                        isUserChange = currentBuild.rawBuild.getCauses()[0].toString().contains('UserIdCause')
                    }
                }
            }

            stage('Get user name') {
                when {
                    expression {
                        return (isUserChange == true)
                    }
                }
                steps {
                    script {
                        def INPUT_PARAMS = input message: "Who are you?", ok: "Lets go!", parameters: [string(name: 'NAME', description: "Tell me your name...")]
                    }
                }
            }

            stage('Check for readiness') {
                when {
                    expression {
                        return (isUserChange == true)
                    }
                }
                steps {
                    script {
                        def INPUT_PARAMS = input message: "Pulling branch ${BRANCH} \n from repository ${REPO} \n for project ${PROJECT_NAME}", ok: "Start the build now!"
                    }
                }
            }

            stage('Pull Repo') {
                steps {
                    checkout scm: [$class           : 'GitSCM', branches: [[name: "${BRANCH}"]],
                                   userRemoteConfigs: [[credentialsId: 'GrantL', url: "${REPO}"]]]
                }
            }

            stage('Install the application') {
                steps {
                    sh "mvn clean install"
                }
            }

            stage('Save test results') {
                steps {
                    junit 'target/surefire-reports/*.xml'
                }
            }

            stage('Build docker image') {
                steps {
                    withDockerRegistry([credentialsId: 'dockerCreds', url: ""]) {
                        sh "docker build . --tag ${DOCKERUSER}/${PROJECT_NAME}"
                    }
                }
            }

            stage('Push image to Docker Hub then delete it locally') {
                steps {
                    withDockerRegistry([credentialsId: 'dockerCreds', url: ""]) {
                        sh "docker push ${DOCKERUSER}/${PROJECT_NAME}"
                        sh "docker image rm ${DOCKERUSER}/${PROJECT_NAME}"
                    }
                }
            }

            stage('Compose image with volumes') {
                steps {
                    sh "docker-compose up -d"
                }
            }
        }

        post {
            always {
                echo 'Pipeline finished'
                cleanWs()
                sh "docker image prune -f"
            }
            success {
                echo 'Build Successful'
            }
            failure {
                echo 'Build failed'
            }
        }
    }
}