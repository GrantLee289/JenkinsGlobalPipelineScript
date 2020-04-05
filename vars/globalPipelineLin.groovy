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
                            PERSISTENCE = pipelineParameters.persistDir
                            DOCKERUSER = "grantlee289"
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
                        def INPUT_PARAMS = input message: "Pulling branch ${BRANCH} \n from repository ${REPO} \n for project ${PROJECT_NAME} \n with volumes at ${PERSISTENCE}", ok: "Start the build now!"
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
                        sh "sudo docker build . --tag ${DOCKERUSER}/${PROJECT_NAME}"
                    }
                }
            }

            stage('Push image to Docker Hub then delete it locally') {
                steps {
                    withDockerRegistry([credentialsId: 'dockerCreds', url: ""]) {
                        sh "docker push ${DOCKERUSER}/${PROJECT_NAME}"
                        sh "sudo docker image rm ${DOCKERUSER}/${PROJECT_NAME}"
                    }
                }
            }

            stage('Compose image with volumes') {
                steps {
                    sh "sudo docker-compose up -d"
                }
            }
        }

        post {
            always {
                echo 'Pipeline finished'
                cleanWs()
                sh "sudo docker image prune"
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