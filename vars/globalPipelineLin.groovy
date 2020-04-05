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
                            DOCKERUSER = ""
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
                        DOCKERUSER = userInput.NAME
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

            stage('Create persistence directories') {
                steps {
                    sh "test -d ${PERSISTENCE} && echo '${PERSISTENCE} exists' || sudo mkdir ${PERSISTENCE}"
                    sh "test -d ${PERSISTENCE}/${PROJECT_NAME} && echo '${PERSISTENCE}/${PROJECT_NAME} exists' || sudo mkdir ${PERSISTENCE}/${PROJECT_NAME} ${PERSISTENCE}/${PROJECT_NAME}/logs ${PERSISTENCE}/${PROJECT_NAME}/from  ${PERSISTENCE}/${PROJECT_NAME}/to"
                }
            }

            stage('Build docker image') {
                steps {
                    sh "sudo docker build . --tag ${DOCKERUSER}/${PROJECT_NAME}"
                }
            }

            stage('Push image to Docker Hub and delete local image') {
                steps {
                    sh "sudo docker push ${DOCKERUSER}/${PROJECT_NAME}"
                    sh "sudo docker image rm ${DOCKERUSER}/${PROJECT_NAME}"
                }
            }

            stage('Compose image with volumes') {
                sh "sudo docker-compose up -d"
            }

//            stage('Run container') {
//                steps {
//                    sh "sudo docker run -d --name ${PROJECT_NAME}-app -v ${PERSISTENCE}/${PROJECT_NAME}/logs:/logs -v ${PERSISTENCE}/${PROJECT_NAME}/from:/from -v ${PERSISTENCE}/${PROJECT_NAME}/to:/to -it ${DOCKERUSER}/${PROJECT_NAME}"
//                }
//            }

            stage('Clean workspace') {
                steps {
                    cleanWs()
                }
            }
        }

        post {
            always {
                echo 'Pipeline finished'
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